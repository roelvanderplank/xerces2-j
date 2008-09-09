/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.xerces.impl.xs.traversers;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

import javax.xml.stream.XMLStreamException;

import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.XMLEntityManager;
import org.apache.xerces.impl.XMLErrorReporter;
import org.apache.xerces.impl.dv.InvalidDatatypeValueException;
import org.apache.xerces.impl.dv.xs.DecimalDV;
import org.apache.xerces.impl.dv.xs.TypeValidator;
import org.apache.xerces.impl.xs.SchemaGrammar;
import org.apache.xerces.impl.xs.SchemaNamespaceSupport;
import org.apache.xerces.impl.xs.SchemaSymbols;
import org.apache.xerces.impl.xs.XMLSchemaException;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
import org.apache.xerces.impl.xs.XSDDescription;
import org.apache.xerces.impl.xs.XSDeclarationPool;
import org.apache.xerces.impl.xs.XSElementDecl;
import org.apache.xerces.impl.xs.XSGrammarBucket;
import org.apache.xerces.impl.xs.XSGroupDecl;
import org.apache.xerces.impl.xs.XSMessageFormatter;
import org.apache.xerces.impl.xs.XSModelGroupImpl;
import org.apache.xerces.impl.xs.XSParticleDecl;
import org.apache.xerces.impl.xs.opti.ElementImpl;
import org.apache.xerces.impl.xs.opti.SchemaDOMParser;
import org.apache.xerces.impl.xs.opti.SchemaParsingConfig;
import org.apache.xerces.impl.xs.util.SimpleLocator;

import org.apache.xerces.util.DOMUtil;
import org.apache.xerces.parsers.SAXParser;
import org.apache.xerces.parsers.XML11Configuration;
import org.apache.xerces.util.DOMInputSource;
import org.apache.xerces.util.DefaultErrorHandler;
import org.apache.xerces.util.SAXInputSource;
import org.apache.xerces.util.StAXInputSource;
import org.apache.xerces.util.SymbolTable;
import org.apache.xerces.util.XMLSymbols;
import org.apache.xerces.util.URI.MalformedURIException;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.apache.xerces.xni.grammars.XMLSchemaDescription;
import org.apache.xerces.xni.parser.XMLComponentManager;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xs.XSObject;
import org.apache.xerces.xs.XSParticle;
import org.apache.xerces.xs.datatypes.XSDecimal;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * The purpose of this class is to co-ordinate the construction of a
 * grammar object corresponding to a schema.  To do this, it must be
 * prepared to parse several schema documents (for instance if the
 * schema document originally referred to contains <include> or
 * <redefined> information items).  If any of the schemas imports a
 * schema, other grammars may be constructed as a side-effect.
 *
 * @xerces.internal 
 *
 * @author Neil Graham, IBM
 * @author Pavani Mukthipudi, Sun Microsystems
 * 
 * @version $Id$
 */
public class XSDHandler {
    
    /** Feature identifier: validation. */
    protected static final String VALIDATION =
        Constants.SAX_FEATURE_PREFIX + Constants.VALIDATION_FEATURE;
    
    /** feature identifier: XML Schema validation */
    protected static final String XMLSCHEMA_VALIDATION =
        Constants.XERCES_FEATURE_PREFIX + Constants.SCHEMA_VALIDATION_FEATURE;
    
    /** Feature identifier:  allow java encodings */
    protected static final String ALLOW_JAVA_ENCODINGS =
        Constants.XERCES_FEATURE_PREFIX + Constants.ALLOW_JAVA_ENCODINGS_FEATURE;
    
    /** Feature identifier:  continue after fatal error */
    protected static final String CONTINUE_AFTER_FATAL_ERROR =
        Constants.XERCES_FEATURE_PREFIX + Constants.CONTINUE_AFTER_FATAL_ERROR_FEATURE;
    
    /** Feature identifier:  allow java encodings */
    protected static final String STANDARD_URI_CONFORMANT_FEATURE =
        Constants.XERCES_FEATURE_PREFIX + Constants.STANDARD_URI_CONFORMANT_FEATURE;
    
    /** Feature: disallow doctype*/
    protected static final String DISALLOW_DOCTYPE =
        Constants.XERCES_FEATURE_PREFIX + Constants.DISALLOW_DOCTYPE_DECL_FEATURE;
    
    /** Feature: generate synthetic annotations */
    protected static final String GENERATE_SYNTHETIC_ANNOTATIONS = 
        Constants.XERCES_FEATURE_PREFIX + Constants.GENERATE_SYNTHETIC_ANNOTATIONS_FEATURE;
    
    /** Feature identifier: validate annotations. */
    protected static final String VALIDATE_ANNOTATIONS =
        Constants.XERCES_FEATURE_PREFIX + Constants.VALIDATE_ANNOTATIONS_FEATURE;
    
    /** Feature identifier: honour all schemaLocations */
    protected static final String HONOUR_ALL_SCHEMALOCATIONS = 
      Constants.XERCES_FEATURE_PREFIX + Constants.HONOUR_ALL_SCHEMALOCATIONS_FEATURE;
    
    /** Feature identifier: namespace prefixes. */
    private static final String NAMESPACE_PREFIXES =
        Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACE_PREFIXES_FEATURE;
    
    /** Feature identifier: string interning. */
    protected static final String STRING_INTERNING =
        Constants.SAX_FEATURE_PREFIX + Constants.STRING_INTERNING_FEATURE;
   
    /** Property identifier: error handler. */
    protected static final String ERROR_HANDLER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_HANDLER_PROPERTY;
    
    /** Property identifier: JAXP schema source. */
    protected static final String JAXP_SCHEMA_SOURCE =
        Constants.JAXP_PROPERTY_PREFIX + Constants.SCHEMA_SOURCE;
    
    /** Property identifier: entity resolver. */
    public static final String ENTITY_RESOLVER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ENTITY_RESOLVER_PROPERTY;
    /** Property identifier: entity manager. */
    protected static final String ENTITY_MANAGER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ENTITY_MANAGER_PROPERTY;
    
    /** Property identifier: error reporter. */
    public static final String ERROR_REPORTER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_REPORTER_PROPERTY;
    
    /** Property identifier: grammar pool. */
    public static final String XMLGRAMMAR_POOL =
        Constants.XERCES_PROPERTY_PREFIX + Constants.XMLGRAMMAR_POOL_PROPERTY;
    
    /** Property identifier: symbol table. */
    public static final String SYMBOL_TABLE =
        Constants.XERCES_PROPERTY_PREFIX + Constants.SYMBOL_TABLE_PROPERTY;
    
    /** Property identifier: security manager. */
    protected static final String SECURITY_MANAGER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY;
    
    protected static final boolean DEBUG_NODE_POOL = false;
    
    // Data
    
    // different sorts of declarations; should make lookup and
    // traverser calling more efficient/less bulky.
    final static int ATTRIBUTE_TYPE          = 1;
    final static int ATTRIBUTEGROUP_TYPE     = 2;
    final static int ELEMENT_TYPE            = 3;
    final static int GROUP_TYPE              = 4;
    final static int IDENTITYCONSTRAINT_TYPE = 5;
    final static int NOTATION_TYPE           = 6;
    final static int TYPEDECL_TYPE           = 7;
    
    // this string gets appended to redefined names; it's purpose is to be
    // as unlikely as possible to cause collisions.
    public final static String REDEF_IDENTIFIER = "_fn3dktizrknc9pi";
    
    //
    //protected data that can be accessable by any traverser
    // stores <notation> decl
    protected Hashtable fNotationRegistry = new Hashtable();
    
    protected XSDeclarationPool fDeclPool = null;
    
    
    // These tables correspond to the symbol spaces defined in the
    // spec.
    // They are keyed with a QName (that is, String("URI,localpart) and
    // their values are nodes corresponding to the given name's decl.
    // By asking the node for its ownerDocument and looking in
    // XSDocumentInfoRegistry we can easily get the corresponding
    // XSDocumentInfo object.
    private Hashtable fUnparsedAttributeRegistry = new Hashtable();
    private Hashtable fUnparsedAttributeGroupRegistry = new Hashtable();
    private Hashtable fUnparsedElementRegistry = new Hashtable();
    private Hashtable fUnparsedGroupRegistry = new Hashtable();
    private Hashtable fUnparsedIdentityConstraintRegistry = new Hashtable();
    private Hashtable fUnparsedNotationRegistry = new Hashtable();
    private Hashtable fUnparsedTypeRegistry = new Hashtable();
    // Compensation for the above hashtables to locate XSDocumentInfo, 
    // Since we may take Schema Element directly, so can not get the
    // corresponding XSDocumentInfo object just using above hashtables.
    private Hashtable fUnparsedAttributeRegistrySub = new Hashtable();
    private Hashtable fUnparsedAttributeGroupRegistrySub = new Hashtable();
    private Hashtable fUnparsedElementRegistrySub = new Hashtable();
    private Hashtable fUnparsedGroupRegistrySub = new Hashtable();
    private Hashtable fUnparsedIdentityConstraintRegistrySub = new Hashtable();
    private Hashtable fUnparsedNotationRegistrySub = new Hashtable();
    private Hashtable fUnparsedTypeRegistrySub = new Hashtable();
    
    // this is keyed with a documentNode (or the schemaRoot nodes
    // contained in the XSDocumentInfo objects) and its value is the
    // XSDocumentInfo object corresponding to that document.
    // Basically, the function of this registry is to be a link
    // between the nodes we fetch from calls to the fUnparsed*
    // arrays and the XSDocumentInfos they live in.
    private Hashtable fXSDocumentInfoRegistry = new Hashtable();
    
    // this hashtable is keyed on by XSDocumentInfo objects.  Its values
    // are Vectors containing the XSDocumentInfo objects <include>d,
    // <import>ed or <redefine>d by the key XSDocumentInfo.
    private Hashtable fDependencyMap = new Hashtable();
    
    // this hashtable is keyed on by a target namespace.  Its values
    // are Vectors containing namespaces imported by schema documents
    // with the key target namespace.
    // if an imprted schema has absent namespace, the value "null" is stored.
    private Hashtable fImportMap = new Hashtable();
    // all namespaces that imports other namespaces
    // if the importing schema has absent namespace, empty string is stored.
    // (because the key of a hashtable can't be null.)
    private Vector fAllTNSs = new Vector();
    // stores instance document mappings between namespaces and schema hints
    private Hashtable fLocationPairs = null;
    private static final Hashtable EMPTY_TABLE = new Hashtable();
    
    // Records which nodes are hidden when the input is a DOMInputSource.
    Hashtable fHiddenNodes = null;

    // Conditional inclustion
    private static final String XSD_VERSION_1_0 = "1.0";
    private static final String XSD_VERSION_1_1 = "1.1";

    private static final TypeValidator DECIMAL_DV = new DecimalDV();
    private static final XSDecimal SUPPORTED_VERSION_1_0 = getSupportedVersion(XSD_VERSION_1_0);
    private static final XSDecimal SUPPORTED_VERSION_1_1 = getSupportedVersion(XSD_VERSION_1_1);
    private XSDecimal fSupportedVersion = SUPPORTED_VERSION_1_0;
    
    private static XSDecimal getSupportedVersion(String version) {
        XSDecimal result = null;
        try {
            result = (XSDecimal) DECIMAL_DV.getActualValue(version, null);
        }
        catch (InvalidDatatypeValueException ide) {
        }
        return result;
    }

    // convenience methods
    private String null2EmptyString(String ns) {
        return ns == null ? XMLSymbols.EMPTY_STRING : ns;
    }
    private String emptyString2Null(String ns) {
        return ns == XMLSymbols.EMPTY_STRING ? null : ns;
    }
    // use Schema Element to lookup the SystemId.
    private String doc2SystemId(Element ele) {
        String documentURI = null;
        /**
         * REVISIT: Casting until DOM Level 3 interfaces are available. -- mrglavas
         */
        if(ele.getOwnerDocument() instanceof org.apache.xerces.impl.xs.opti.SchemaDOM){
            documentURI = ((org.apache.xerces.impl.xs.opti.SchemaDOM) ele.getOwnerDocument()).getDocumentURI();
        }
        return documentURI != null ? documentURI : (String) fDoc2SystemId.get(ele);
    }
    
    // This vector stores strings which are combinations of the
    // publicId and systemId of the inputSource corresponding to a
    // schema document.  This combination is used so that the user's
    // EntityResolver can provide a consistent way of identifying a
    // schema document that is included in multiple other schemas.
    private Hashtable fTraversed = new Hashtable();
    
    // this hashtable contains a mapping from Schema Element to its systemId
    // this is useful to resolve a uri relative to the referring document
    private Hashtable fDoc2SystemId = new Hashtable();
    
    // the primary XSDocumentInfo we were called to parse
    private XSDocumentInfo fRoot = null;
    
    // This hashtable's job is to act as a link between the Schema Element and its
    // XSDocumentInfo object.
    private Hashtable fDoc2XSDocumentMap = new Hashtable();
    
    // map between <redefine> elements and the XSDocumentInfo
    // objects that correspond to the documents being redefined.
    private Hashtable fRedefine2XSDMap = new Hashtable();
    
    // map between <redefine> elements and the namespace support
    private Hashtable fRedefine2NSSupport = new Hashtable();
    
    // these objects store a mapping between the names of redefining
    // groups/attributeGroups and the groups/AttributeGroups which
    // they redefine by restriction (implicitly).  It is up to the
    // Group and AttributeGroup traversers to check these restrictions for
    // validity.
    private Hashtable fRedefinedRestrictedAttributeGroupRegistry = new Hashtable();
    private Hashtable fRedefinedRestrictedGroupRegistry = new Hashtable();
    
    // a variable storing whether the last schema document
    // processed (by getSchema) was a duplicate.
    private boolean fLastSchemaWasDuplicate;
    
    // validate annotations feature
    private boolean fValidateAnnotations = false;
    
    //handle multiple import feature
    private boolean fHonourAllSchemaLocations = false;

    // the XMLErrorReporter
    private XMLErrorReporter fErrorReporter;
    private XMLEntityResolver fEntityResolver;
    
    // the XSAttributeChecker
    private XSAttributeChecker fAttributeChecker;
    
    // the symbol table
    private SymbolTable fSymbolTable;
    
    // the GrammarResolver
    private XSGrammarBucket fGrammarBucket;
    
    // the Grammar description
    private XSDDescription fSchemaGrammarDescription;
    
    // the Grammar Pool
    private XMLGrammarPool fGrammarPool;
    
    //************ Traversers **********
    XSDAttributeGroupTraverser fAttributeGroupTraverser;
    XSDAttributeTraverser fAttributeTraverser;
    XSDComplexTypeTraverser fComplexTypeTraverser;
    XSDElementTraverser fElementTraverser;
    XSDGroupTraverser fGroupTraverser;
    XSDKeyrefTraverser fKeyrefTraverser;
    XSDNotationTraverser fNotationTraverser;
    XSDSimpleTypeTraverser fSimpleTypeTraverser;
    XSDTypeAlternativeTraverser fTypeAlternativeTraverser;
    XSDUniqueOrKeyTraverser fUniqueOrKeyTraverser;
    XSDWildcardTraverser fWildCardTraverser;
    
    SchemaDOMParser fSchemaParser;
    SchemaContentHandler fXSContentHandler;
    StAXSchemaParser fStAXSchemaParser;
    XML11Configuration fAnnotationValidator;
    XSAnnotationGrammarPool fGrammarBucketAdapter;

    // flag to indicate schema 1.1 support
    short fSchemaVersion = Constants.SCHEMA_VERSION_1_0;

    // these data members are needed for the deferred traversal
    // of local elements.
    
    // the initial size of the array to store deferred local elements
    private static final int INIT_STACK_SIZE = 30;
    // the incremental size of the array to store deferred local elements
    private static final int INC_STACK_SIZE  = 10;
    // current position of the array (# of deferred local elements)
    private int fLocalElemStackPos = 0;
    
    private XSParticleDecl[] fParticle = new XSParticleDecl[INIT_STACK_SIZE];
    private Element[] fLocalElementDecl = new Element[INIT_STACK_SIZE];
    private XSDocumentInfo[] fLocalElementDecl_schema = new XSDocumentInfo[INIT_STACK_SIZE]; //JACK
    private int[] fAllContext = new int[INIT_STACK_SIZE];
    private XSObject[] fParent = new XSObject[INIT_STACK_SIZE];
    private String [][] fLocalElemNamespaceContext = new String [INIT_STACK_SIZE][1];
    
    // these data members are needed for the deferred traversal
    // of keyrefs.
    
    // the initial size of the array to store deferred keyrefs
    private static final int INIT_KEYREF_STACK = 2;
    // the incremental size of the array to store deferred keyrefs
    private static final int INC_KEYREF_STACK_AMOUNT = 2;
    // current position of the array (# of deferred keyrefs)
    private int fKeyrefStackPos = 0;
    
    private Element [] fKeyrefs = new Element[INIT_KEYREF_STACK];
    private XSDocumentInfo [] fKeyrefsMapXSDocumentInfo = new XSDocumentInfo[INIT_KEYREF_STACK];
    private XSElementDecl [] fKeyrefElems = new XSElementDecl [INIT_KEYREF_STACK];
    private String [][] fKeyrefNamespaceContext = new String[INIT_KEYREF_STACK][1];
    
    // Constructors
    public XSDHandler(){
        fHiddenNodes = new Hashtable();       
        fSchemaParser = new SchemaDOMParser(new SchemaParsingConfig());
        fSchemaParser.setSupportedVersion(fSupportedVersion); //REVISIT: pass to constructor?
    }
    
    // it should be possible to use the same XSDHandler to parse
    // multiple schema documents; this will allow one to be
    // constructed.
    public XSDHandler (XSGrammarBucket gBucket) {
        this();
        fGrammarBucket = gBucket;
        
        // Note: don't use SchemaConfiguration internally
        //       we will get stack overflaw because
        //       XMLSchemaValidator will be instantiating XSDHandler...
        fSchemaGrammarDescription = new XSDDescription();
    } // end constructor
       
    /**
     * This method initiates the parse of a schema.  It will likely be
     * called from the Validator and it will make the
     * resulting grammar available; it returns a reference to this object just
     * in case.  A reset(XMLComponentManager) must be called before this methods is called.
     * @param is
     * @param desc
     * @param locationPairs
     * @return the SchemaGrammar
     * @throws IOException
     */
    public SchemaGrammar parseSchema(XMLInputSource is, XSDDescription desc,
            Hashtable locationPairs)
    throws IOException {
        fLocationPairs = locationPairs;
        fSchemaParser.resetNodePool();   
        SchemaGrammar grammar = null;
        String schemaNamespace  = null;
        short referType = desc.getContextType();
        // if loading using JAXP schemaSource property, or using grammar caching loadGrammar
        // the desc.targetNamespace is always null.
        // Therefore we should not attempt to find out if
        // the schema is already in the bucket, since in the case we have
        // no namespace schema in the bucket, findGrammar will always return the
        // no namespace schema.
        if (referType != XSDDescription.CONTEXT_PREPARSE){
            // first try to find it in the bucket/pool, return if one is found
            if(fHonourAllSchemaLocations && referType == XSDDescription.CONTEXT_IMPORT && isExistingGrammar(desc)) {
                grammar = fGrammarBucket.getGrammar(desc.getTargetNamespace());
            }
            else {
                grammar = findGrammar(desc);
            }
            if (grammar != null)
                return grammar;
            schemaNamespace = desc.getTargetNamespace();
            // handle empty string URI as null
            if (schemaNamespace != null) {
                schemaNamespace = fSymbolTable.addSymbol(schemaNamespace);
            }
        }
        
        // before parsing a schema, need to clear registries associated with
        // parsing schemas
        prepareForParse();       
        
        Document schemaRootDoc = null;
        Element schemaRoot = null;
        // first phase:  construct trees.
        if (is instanceof DOMInputSource) {
            //clean up the field fHiddenNodes, used for DOMInputSource
            fHiddenNodes.clear();
            Node domNode = ((DOMInputSource)is).getNode();
            
            if (domNode instanceof Document) {
                schemaRootDoc = (Document)domNode;
                schemaRoot = DOMUtil.getRoot(schemaRootDoc);
            }
            else if (domNode instanceof Element) {
                schemaRoot = (Element)domNode;
            }
            else {
                return null;
            }
        } // DOMInputSource
        else if (is instanceof SAXInputSource) {
            XMLReader parser = ((SAXInputSource)is).getXMLReader();
            InputSource inputSource = ((SAXInputSource)is).getInputSource(); 
            boolean namespacePrefixes = false;
            if (parser != null) {
                try {
                    namespacePrefixes = parser.getFeature(NAMESPACE_PREFIXES);
                }
                catch (SAXException se) {}
            }
            else {
                try {
                    parser = XMLReaderFactory.createXMLReader();
                }
                // If something went wrong with the factory
                // just use our own SAX parser.
                catch (SAXException se) {
                    parser = new SAXParser();
                }
                try {
                    parser.setFeature(NAMESPACE_PREFIXES, true);
                    namespacePrefixes = true;
                }
                catch (SAXException se) {}
            }
            // If XML names and Namespace URIs are already internalized we
            // can avoid running them through the SymbolTable.
            boolean stringsInternalized = false;
            try {
                stringsInternalized = parser.getFeature(STRING_INTERNING);
            }
            catch (SAXException exc) {
                // The feature isn't recognized or getting it is not supported.
                // In either case, assume that strings are not internalized.
            }
            if (fXSContentHandler == null) {
                fXSContentHandler = new SchemaContentHandler();
            }
            fXSContentHandler.reset(fSchemaParser, fSymbolTable, 
                    namespacePrefixes, stringsInternalized);
            parser.setContentHandler(fXSContentHandler);
            parser.setErrorHandler(fErrorReporter.getSAXErrorHandler());
        	try {
            	parser.parse(inputSource);	
        	}
        	catch (SAXException se) {
        		return null;
        	}
        	// Disconnect the schema loader and other objects from the XMLReader
        	try {
        	    parser.setContentHandler(null);
        	    parser.setErrorHandler(null);
        	}
        	// Ignore any exceptions thrown by the XMLReader. Old versions of SAX
        	// required an XMLReader to throw a NullPointerException if an attempt
        	// to set a handler to null was made.
        	catch (Exception e) {}
        	schemaRootDoc = fXSContentHandler.getDocument();
            if (schemaRootDoc == null) {
                // something went wrong right off the hop
                return null;
            }
        	schemaRoot = DOMUtil.getRoot(schemaRootDoc);          
        }
        else if (is instanceof StAXInputSource) {
            StAXInputSource sis = (StAXInputSource)is;
            if (fStAXSchemaParser == null) {
                fStAXSchemaParser = new StAXSchemaParser();
            }
            fStAXSchemaParser.reset(fSchemaParser, fSymbolTable);
            try {
                if (sis.getXMLEventReader() != null) {
                    fStAXSchemaParser.parse(sis.getXMLEventReader());
                }
                else {
                    fStAXSchemaParser.parse(sis.getXMLStreamReader());
                }
            }
            catch (XMLStreamException e) {
                return null;
            }
            schemaRootDoc = fStAXSchemaParser.getDocument();
            if (schemaRootDoc == null) {
                return null;
            }
            schemaRoot = DOMUtil.getRoot(schemaRootDoc);
        }
        else {
        	schemaRoot = getSchemaDocument(schemaNamespace, is,
                  referType == XSDDescription.CONTEXT_PREPARSE,
                  referType, null);
             
        }//is instanceof XMLInputSource

        if(schemaRoot == null){
            // something went wrong right off the hop
            return null;
        }      
        
        if ( referType == XSDDescription.CONTEXT_PREPARSE) {
        	Element schemaElem = schemaRoot;
            schemaNamespace = DOMUtil.getAttrValue(schemaElem, SchemaSymbols.ATT_TARGETNAMESPACE);
            if(schemaNamespace != null && schemaNamespace.length() > 0) {
                // Since now we've discovered a namespace, we need to update xsd key
                // and store this schema in traversed schemas bucket
                schemaNamespace = fSymbolTable.addSymbol(schemaNamespace);
                desc.setTargetNamespace(schemaNamespace);
            }
            else {
                schemaNamespace = null;
            }
            grammar = findGrammar(desc);
            if (grammar != null)
                return grammar;
            String schemaId = XMLEntityManager.expandSystemId(is.getSystemId(), is.getBaseSystemId(), false);
            XSDKey key = new XSDKey(schemaId, referType, schemaNamespace);
            fTraversed.put(key, schemaRoot);
            if (schemaId != null) {
            	fDoc2SystemId.put(schemaRoot, schemaId);
            }
        }
        
        // before constructing trees and traversing a schema, need to reset
        // all traversers and clear all registries
        prepareForTraverse();
        
        fRoot = constructTrees(schemaRoot, is.getSystemId(), desc);
        if (fRoot == null) {
            return null;
        }
        
        // second phase:  fill global registries.
        buildGlobalNameRegistries();
        
        // third phase:  call traversers
        ArrayList annotationInfo = fValidateAnnotations ? new ArrayList() : null;
        traverseSchemas(annotationInfo);
        
        // fourth phase: handle local element decls
        traverseLocalElements();
        
        // fifth phase:  handle Keyrefs
        resolveKeyRefs();
        
        // sixth phase:  validate attribute of non-schema namespaces
        // REVISIT: skip this for now. we really don't want to do it.
        //fAttributeChecker.checkNonSchemaAttributes(fGrammarBucket);
        
        // seventh phase:  store imported grammars
        // for all grammars with <import>s
        for (int i = fAllTNSs.size() - 1; i >= 0; i--) {
            // get its target namespace
            String tns = (String)fAllTNSs.elementAt(i);
            // get all namespaces it imports
            Vector ins = (Vector)fImportMap.get(tns);
            // get the grammar
            SchemaGrammar sg = fGrammarBucket.getGrammar(emptyString2Null(tns));
            if (sg == null)
                continue;
            SchemaGrammar isg;
            // for imported namespace
            int count = 0;
            for (int j = 0; j < ins.size(); j++) {
                // get imported grammar
                isg = fGrammarBucket.getGrammar((String)ins.elementAt(j));
                // reuse the same vector
                if (isg != null)
                    ins.setElementAt(isg, count++);
            }
            ins.setSize(count);
            // set the imported grammars
            sg.setImportedGrammars(ins);
        }
        
        /** validate annotations **/
        if (fValidateAnnotations && annotationInfo.size() > 0) {
            validateAnnotations(annotationInfo);
        }

        // and return.
        return fGrammarBucket.getGrammar(fRoot.fTargetNamespace);
    } // end parseSchema
    
    private void validateAnnotations(ArrayList annotationInfo) {
        if (fAnnotationValidator == null) {
            createAnnotationValidator();
        }
        final int size = annotationInfo.size();
        final XMLInputSource src = new XMLInputSource(null, null, null);
        fGrammarBucketAdapter.refreshGrammars(fGrammarBucket);
        for (int i = 0; i < size; i += 2) {
            src.setSystemId((String) annotationInfo.get(i));
            XSAnnotationInfo annotation = (XSAnnotationInfo) annotationInfo.get(i+1);
            while (annotation != null) {
                src.setCharacterStream(new StringReader(annotation.fAnnotation));
                try {
                    fAnnotationValidator.parse(src);
                }
                catch (IOException exc) {}
                annotation = annotation.next;
            }
        }
    }
    
    private void createAnnotationValidator() {
        fAnnotationValidator = new XML11Configuration();
        fGrammarBucketAdapter = new XSAnnotationGrammarPool();
        fAnnotationValidator.setFeature(VALIDATION, true);
        fAnnotationValidator.setFeature(XMLSCHEMA_VALIDATION, true);
        fAnnotationValidator.setProperty(XMLGRAMMAR_POOL, fGrammarBucketAdapter);
        /** Set error handler. **/
        XMLErrorHandler errorHandler = fErrorReporter.getErrorHandler();
        fAnnotationValidator.setProperty(ERROR_HANDLER, (errorHandler != null) ? errorHandler : new DefaultErrorHandler());
    }

    /**
     * Pull the grammar out of the bucket simply using
     * its TNS as a key
     */
    SchemaGrammar getGrammar(String tns) {
        return fGrammarBucket.getGrammar(tns);
    }
    
    /**
     * First try to find a grammar in the bucket, if failed, consult the
     * grammar pool. If a grammar is found in the pool, then add it (and all
     * imported ones) into the bucket.
     */
    protected SchemaGrammar findGrammar(XSDDescription desc) {
        SchemaGrammar sg = fGrammarBucket.getGrammar(desc.getTargetNamespace());
        if (sg == null) {
            if (fGrammarPool != null) {
                sg = (SchemaGrammar)fGrammarPool.retrieveGrammar(desc);
                if (sg != null) {
                    // put this grammar into the bucket, along with grammars
                    // imported by it (directly or indirectly)
                    if (!fGrammarBucket.putGrammar(sg, true)) {
                        // REVISIT: a conflict between new grammar(s) and grammars
                        // in the bucket. What to do? A warning? An exception?
                        reportSchemaWarning("GrammarConflict", null, null);
                        sg = null;
                    }
                }
            }
        }
        return sg;
    }
    
    // may wish to have setter methods for ErrorHandler,
    // EntityResolver...
    
    private static final String[][] NS_ERROR_CODES = {
            {"src-include.2.1", "src-include.2.1"},
            {"src-redefine.3.1", "src-redefine.3.1"},
            {"src-import.3.1", "src-import.3.2"},
            null,
            {"TargetNamespace.1", "TargetNamespace.2"},
            {"TargetNamespace.1", "TargetNamespace.2"},
            {"TargetNamespace.1", "TargetNamespace.2"},
            {"TargetNamespace.1", "TargetNamespace.2"}
    };
    
    private static final String[] ELE_ERROR_CODES = {
            "src-include.1", "src-redefine.2", "src-import.2", "schema_reference.4",
            "schema_reference.4", "schema_reference.4", "schema_reference.4", "schema_reference.4"
    };
    
    // This method does several things:
    // It constructs an instance of an XSDocumentInfo object using the
    // schemaRoot node.  Then, for each <include>,
    // <redefine>, and <import> children, it attempts to resolve the
    // requested schema document, initiates a DOM parse, and calls
    // itself recursively on that document's root.  It also records in
    // the DependencyMap object what XSDocumentInfo objects its XSDocumentInfo
    // depends on.
    // It also makes sure the targetNamespace of the schema it was
    // called to parse is correct.
    protected XSDocumentInfo constructTrees(Element schemaRoot, String locationHint, XSDDescription desc) {
        if (schemaRoot == null) return null;
        String callerTNS = desc.getTargetNamespace();
        short referType = desc.getContextType();
        
        XSDocumentInfo currSchemaInfo = null;
        try {
            // note that attributes are freed at end of traverseSchemas()
            currSchemaInfo = new XSDocumentInfo(schemaRoot, fAttributeChecker, fSymbolTable);
        } catch (XMLSchemaException se) {
            reportSchemaError(ELE_ERROR_CODES[referType],
                    new Object[]{locationHint},
					  schemaRoot);
            return null;
        }
        // targetNamespace="" is not valid, issue a warning, and ignore it
        if (currSchemaInfo.fTargetNamespace != null &&
                currSchemaInfo.fTargetNamespace.length() == 0) {
            reportSchemaWarning("EmptyTargetNamespace",
                    new Object[]{locationHint},
					schemaRoot);
            currSchemaInfo.fTargetNamespace = null;
        }
        
        if (callerTNS != null) {
            // the second index to the NS_ERROR_CODES array
            // if the caller/expected NS is not absent, we use the first column
            int secondIdx = 0;
            // for include and redefine
            if (referType == XSDDescription.CONTEXT_INCLUDE ||
                    referType == XSDDescription.CONTEXT_REDEFINE) {
                // if the referred document has no targetNamespace,
                // it's a chameleon schema
                if (currSchemaInfo.fTargetNamespace == null) {
                    currSchemaInfo.fTargetNamespace = callerTNS;
                    currSchemaInfo.fIsChameleonSchema = true;
                }
                // if the referred document has a target namespace differing
                // from the caller, it's an error
                else if (callerTNS != currSchemaInfo.fTargetNamespace) {
                    reportSchemaError(NS_ERROR_CODES[referType][secondIdx],
                            new Object [] {callerTNS, currSchemaInfo.fTargetNamespace},
							schemaRoot);
                    return null;
                }
            }
            // for instance and import, the two NS's must be the same
            else if (referType != XSDDescription.CONTEXT_PREPARSE && callerTNS != currSchemaInfo.fTargetNamespace) {
                reportSchemaError(NS_ERROR_CODES[referType][secondIdx],
                        new Object [] {callerTNS, currSchemaInfo.fTargetNamespace},
						schemaRoot);
                return null;
            }
        }
        // now there is no caller/expected NS, it's an error for the referred
        // document to have a target namespace, unless we are preparsing a schema
        else if (currSchemaInfo.fTargetNamespace != null) {
            // set the target namespace of the description
            if (referType == XSDDescription.CONTEXT_PREPARSE) {
                desc.setTargetNamespace(currSchemaInfo.fTargetNamespace);
                callerTNS = currSchemaInfo.fTargetNamespace;
            }
            else {
                // the second index to the NS_ERROR_CODES array
                // if the caller/expected NS is absent, we use the second column
                int secondIdx = 1;
                reportSchemaError(NS_ERROR_CODES[referType][secondIdx],
                        new Object [] {callerTNS, currSchemaInfo.fTargetNamespace},
						schemaRoot);
                return null;
            }
        }
        // the other cases (callerTNS == currSchemaInfo.fTargetNamespce == null)
        // are valid
        
        // a schema document can always access it's own target namespace
        currSchemaInfo.addAllowedNS(currSchemaInfo.fTargetNamespace);
        
        SchemaGrammar sg = null;
        
        if (referType == XSDDescription.CONTEXT_INCLUDE ||
                referType == XSDDescription.CONTEXT_REDEFINE) {
            sg = fGrammarBucket.getGrammar(currSchemaInfo.fTargetNamespace);
        }
        else if(fHonourAllSchemaLocations && referType == XSDDescription.CONTEXT_IMPORT) {
            sg = findGrammar(desc);
            if(sg == null) {
                sg = new SchemaGrammar(currSchemaInfo.fTargetNamespace, desc.makeClone(), fSymbolTable);
                fGrammarBucket.putGrammar(sg);
            }
        }
        else {
            sg = new SchemaGrammar(currSchemaInfo.fTargetNamespace, desc.makeClone(), fSymbolTable);
            fGrammarBucket.putGrammar(sg);
        }
        
        // store the document and its location
        // REVISIT: don't expose the DOM tree
        sg.addDocument(null, (String)fDoc2SystemId.get(currSchemaInfo.fSchemaElement));
        
        fDoc2XSDocumentMap.put(schemaRoot, currSchemaInfo);
        Vector dependencies = new Vector();
        Element rootNode = schemaRoot;
        
        Element newSchemaRoot = null;
        for (Element child = DOMUtil.getFirstChildElement(rootNode);
        child != null;
        child = DOMUtil.getNextSiblingElement(child)) {
            String schemaNamespace=null;
            String schemaHint=null;
            String localName = DOMUtil.getLocalName(child);
            
            short refType = -1;
            
            if (localName.equals(SchemaSymbols.ELT_ANNOTATION))
                continue;
            else if (localName.equals(SchemaSymbols.ELT_IMPORT)) {
                refType = XSDDescription.CONTEXT_IMPORT;
                // have to handle some validation here too!
                // call XSAttributeChecker to fill in attrs
                Object[] importAttrs = fAttributeChecker.checkAttributes(child, true, currSchemaInfo);
                schemaHint = (String)importAttrs[XSAttributeChecker.ATTIDX_SCHEMALOCATION];
                schemaNamespace = (String)importAttrs[XSAttributeChecker.ATTIDX_NAMESPACE];
                if (schemaNamespace != null)
                    schemaNamespace = fSymbolTable.addSymbol(schemaNamespace);
                // a document can't import another document with the same namespace
                if (schemaNamespace == currSchemaInfo.fTargetNamespace) {
                    reportSchemaError(schemaNamespace != null ? 
                            "src-import.1.1" : "src-import.1.2", new Object [] {schemaNamespace}, child);
                }
                
                // check contents and process optional annotations
                Element importChild = DOMUtil.getFirstChildElement(child);
                if(importChild != null ) {
                    String importComponentType = DOMUtil.getLocalName(importChild);
                    if (importComponentType.equals(SchemaSymbols.ELT_ANNOTATION)) {
                        // promoting annotations to parent component
                        sg.addAnnotation(
                                fElementTraverser.traverseAnnotationDecl(importChild, importAttrs, true, currSchemaInfo));
                    } else {
                        reportSchemaError("s4s-elt-must-match.1", new Object [] {localName, "annotation?", importComponentType}, child);
                    }
                    if(DOMUtil.getNextSiblingElement(importChild) != null) {
                        reportSchemaError("s4s-elt-must-match.1", new Object [] {localName, "annotation?", DOMUtil.getLocalName(DOMUtil.getNextSiblingElement(importChild))}, child);
                    }
                }
                else {
                    String text = DOMUtil.getSyntheticAnnotation(child);
                    if (text != null) {
                        sg.addAnnotation(fElementTraverser.traverseSyntheticAnnotation(child, text, importAttrs, true, currSchemaInfo));
                    }
                }
                fAttributeChecker.returnAttrArray(importAttrs, currSchemaInfo);
                
                // if this namespace has not been imported by this document,
                //  then import if multiple imports support is enabled.
                if(currSchemaInfo.isAllowedNS(schemaNamespace)) {
                    if(!fHonourAllSchemaLocations)
                        continue;
                }
                else  {
                    currSchemaInfo.addAllowedNS(schemaNamespace);
                }
                // also record the fact that one namespace imports another one
                // convert null to ""
                String tns = null2EmptyString(currSchemaInfo.fTargetNamespace);
                // get all namespaces imported by this one
                Vector ins = (Vector)fImportMap.get(tns);
                // if no namespace was imported, create new Vector
                if (ins == null) {
                    // record that this one imports other(s)
                    fAllTNSs.addElement(tns);
                    ins = new Vector();
                    fImportMap.put(tns, ins);
                    ins.addElement(schemaNamespace);
                }
                else if (!ins.contains(schemaNamespace)){
                    ins.addElement(schemaNamespace);
                }
                
                fSchemaGrammarDescription.reset();
                fSchemaGrammarDescription.setContextType(XSDDescription.CONTEXT_IMPORT);
                fSchemaGrammarDescription.setBaseSystemId(doc2SystemId(schemaRoot));
                fSchemaGrammarDescription.setLocationHints(new String[]{schemaHint});
                fSchemaGrammarDescription.setTargetNamespace(schemaNamespace);
                
                // if a grammar with the same namespace and location exists (or being
                // built), ignore this one (don't traverse it).
                if ((!fHonourAllSchemaLocations && findGrammar(fSchemaGrammarDescription) != null) || isExistingGrammar(fSchemaGrammarDescription))
                    continue;
                // If "findGrammar" returns a grammar, then this is not the
                // the first time we see a location for a given namespace.
                // Don't consult the location pair hashtable in this case,
                // otherwise the location will be ignored because it'll get
                // resolved to the same location as the first hint.
                newSchemaRoot = resolveSchema(fSchemaGrammarDescription, false, child,
                                              findGrammar(fSchemaGrammarDescription) == null);
            }
            else if ((localName.equals(SchemaSymbols.ELT_INCLUDE)) ||
                    (localName.equals(SchemaSymbols.ELT_REDEFINE))) {
                // validation for redefine/include will be the same here; just
                // make sure TNS is right (don't care about redef contents
                // yet).
                Object[] includeAttrs = fAttributeChecker.checkAttributes(child, true, currSchemaInfo);
                schemaHint = (String)includeAttrs[XSAttributeChecker.ATTIDX_SCHEMALOCATION];
                // store the namespace decls of the redefine element
                if (localName.equals(SchemaSymbols.ELT_REDEFINE)) {
                    fRedefine2NSSupport.put(child, new SchemaNamespaceSupport(currSchemaInfo.fNamespaceSupport));
                }
                
                // check annotations.  Must do this here to avoid having to
                // re-parse attributes later
                if(localName.equals(SchemaSymbols.ELT_INCLUDE)) {
                    Element includeChild = DOMUtil.getFirstChildElement(child);
                    if(includeChild != null ) {
                        String includeComponentType = DOMUtil.getLocalName(includeChild);
                        if (includeComponentType.equals(SchemaSymbols.ELT_ANNOTATION)) {
                            // promoting annotations to parent component
                            sg.addAnnotation(
                                    fElementTraverser.traverseAnnotationDecl(includeChild, includeAttrs, true, currSchemaInfo));
                        } else {
                            reportSchemaError("s4s-elt-must-match.1", new Object [] {localName, "annotation?", includeComponentType}, child);
                        }
                        if(DOMUtil.getNextSiblingElement(includeChild) != null) {
                            reportSchemaError("s4s-elt-must-match.1", new Object [] {localName, "annotation?", DOMUtil.getLocalName(DOMUtil.getNextSiblingElement(includeChild))}, child);
                        }
                    }
                    else {
                        String text = DOMUtil.getSyntheticAnnotation(child);
                        if (text != null) {
                            sg.addAnnotation(fElementTraverser.traverseSyntheticAnnotation(child, text, includeAttrs, true, currSchemaInfo));
                        }
                    }
                }
                else {
                    for (Element redefinedChild = DOMUtil.getFirstChildElement(child);
                    redefinedChild != null;
                    redefinedChild = DOMUtil.getNextSiblingElement(redefinedChild)) {
                        String redefinedComponentType = DOMUtil.getLocalName(redefinedChild);
                        if (redefinedComponentType.equals(SchemaSymbols.ELT_ANNOTATION)) {
                            // promoting annotations to parent component
                            sg.addAnnotation(
                                    fElementTraverser.traverseAnnotationDecl(redefinedChild, includeAttrs, true, currSchemaInfo));
                            DOMUtil.setHidden(redefinedChild, fHiddenNodes);
                        }
                        else {
                            String text = DOMUtil.getSyntheticAnnotation(child);
                            if (text != null) {
                                sg.addAnnotation(fElementTraverser.traverseSyntheticAnnotation(child, text, includeAttrs, true, currSchemaInfo));
                            }
                        }
                        // catch all other content errors later
                    }
                }
                fAttributeChecker.returnAttrArray(includeAttrs, currSchemaInfo);
                // schemaLocation is required on <include> and <redefine>
                if (schemaHint == null) {
                    reportSchemaError("s4s-att-must-appear", new Object [] {
                            "<include> or <redefine>", "schemaLocation"},
                            child);
                }
                // pass the systemId of the current document as the base systemId
                boolean mustResolve = false;
                refType = XSDDescription.CONTEXT_INCLUDE;
                if(localName.equals(SchemaSymbols.ELT_REDEFINE)) {
                    mustResolve = nonAnnotationContent(child);
                    refType = XSDDescription.CONTEXT_REDEFINE;
                }
                fSchemaGrammarDescription.reset();
                fSchemaGrammarDescription.setContextType(refType);
                fSchemaGrammarDescription.setBaseSystemId(doc2SystemId(schemaRoot));
                fSchemaGrammarDescription.setLocationHints(new String[]{schemaHint});
                fSchemaGrammarDescription.setTargetNamespace(callerTNS);
                newSchemaRoot = resolveSchema(fSchemaGrammarDescription, mustResolve, child, true);
                schemaNamespace = currSchemaInfo.fTargetNamespace;
            }
            else {
                // no more possibility of schema references in well-formed
                // schema...
                break;
            }
            
            // If the schema is duplicate, we needn't call constructTrees() again.
            // To handle mutual <include>s
            XSDocumentInfo newSchemaInfo = null;
            if (fLastSchemaWasDuplicate) {
                newSchemaInfo = newSchemaRoot == null ? null : (XSDocumentInfo)fDoc2XSDocumentMap.get(newSchemaRoot);
            }
            else {
               	newSchemaInfo = constructTrees(newSchemaRoot, schemaHint, fSchemaGrammarDescription);
            }
            
            if (localName.equals(SchemaSymbols.ELT_REDEFINE) &&
                    newSchemaInfo != null) {
                // must record which schema we're redefining so that we can
                // rename the right things later!
                fRedefine2XSDMap.put(child, newSchemaInfo);
            }
            if (newSchemaRoot != null) {
                if (newSchemaInfo != null)
                    dependencies.addElement(newSchemaInfo);
                newSchemaRoot = null;
            }
        }
        
        fDependencyMap.put(currSchemaInfo, dependencies);
        return currSchemaInfo;
    } // end constructTrees
    
    private boolean isExistingGrammar(XSDDescription desc) {
        SchemaGrammar sg = fGrammarBucket.getGrammar(desc.getTargetNamespace());
        if(sg == null) {
            return findGrammar(desc) != null;
        }
        else {
            try {
                return sg.getDocumentLocations().contains(XMLEntityManager.expandSystemId(desc.getLiteralSystemId(), desc.getBaseSystemId(), false));
            } catch (MalformedURIException e) {
                return false;
            }
        }
    }
    
    // This method builds registries for all globally-referenceable
    // names.  A registry will be built for each symbol space defined
    // by the spec.  It is also this method's job to rename redefined
    // components, and to record which components redefine others (so
    // that implicit redefinitions of groups and attributeGroups can be handled).
    protected void buildGlobalNameRegistries() {
        

        // Starting with fRoot, we examine each child of the schema
        // element.  Skipping all imports and includes, we record the names
        // of all other global components (and children of <redefine>).  We
        // also put <redefine> names in a registry that we look through in
        // case something needs renaming.  Once we're done with a schema we
        // set its Document node to hidden so that we don't try to traverse
        // it again; then we look to its Dependency map entry.  We keep a
        // stack of schemas that we haven't yet finished processing; this
        // is a depth-first traversal.
            
        Stack schemasToProcess = new Stack();
        schemasToProcess.push(fRoot);
       
        while (!schemasToProcess.empty()) {            
            XSDocumentInfo currSchemaDoc =
                (XSDocumentInfo)schemasToProcess.pop();
            Element currDoc = currSchemaDoc.fSchemaElement; 
            if(DOMUtil.isHidden(currDoc, fHiddenNodes)){
                // must have processed this already!
                continue;
            }

            Element currRoot = currDoc;
            // process this schema's global decls
            boolean dependenciesCanOccur = true;
            for (Element globalComp =
                DOMUtil.getFirstChildElement(currRoot);
            globalComp != null;
            globalComp = DOMUtil.getNextSiblingElement(globalComp)) {
                // this loop makes sure the <schema> element ordering is
                // also valid.
                if (DOMUtil.getLocalName(globalComp).equals(SchemaSymbols.ELT_ANNOTATION)) {
                    //skip it; traverse it later
                    continue;
                }
                else if (DOMUtil.getLocalName(globalComp).equals(SchemaSymbols.ELT_INCLUDE) ||
                        DOMUtil.getLocalName(globalComp).equals(SchemaSymbols.ELT_IMPORT)) {
                    if (!dependenciesCanOccur) {
                        reportSchemaError("s4s-elt-invalid-content.3", new Object [] {DOMUtil.getLocalName(globalComp)}, globalComp);
                    }
                    DOMUtil.setHidden(globalComp, fHiddenNodes);
                }
                else if (DOMUtil.getLocalName(globalComp).equals(SchemaSymbols.ELT_REDEFINE)) {
                    if (!dependenciesCanOccur) {
                        reportSchemaError("s4s-elt-invalid-content.3", new Object [] {DOMUtil.getLocalName(globalComp)}, globalComp);
                    }
                    for (Element redefineComp = DOMUtil.getFirstChildElement(globalComp);
                    redefineComp != null;
                    redefineComp = DOMUtil.getNextSiblingElement(redefineComp)) {
                        String lName = DOMUtil.getAttrValue(redefineComp, SchemaSymbols.ATT_NAME);
                        if (lName.length() == 0) // an error we'll catch later
                            continue;
                        String qName = currSchemaDoc.fTargetNamespace == null ?
                                ","+lName:
                                    currSchemaDoc.fTargetNamespace +","+lName;
                        String componentType = DOMUtil.getLocalName(redefineComp);
                        if (componentType.equals(SchemaSymbols.ELT_ATTRIBUTEGROUP)) {
                            checkForDuplicateNames(qName, fUnparsedAttributeGroupRegistry, fUnparsedAttributeGroupRegistrySub, redefineComp, currSchemaDoc);
                            // the check will have changed our name;
                            String targetLName = DOMUtil.getAttrValue(redefineComp, SchemaSymbols.ATT_NAME)+REDEF_IDENTIFIER;
                            // and all we need to do is error-check+rename our kkids:
                            renameRedefiningComponents(currSchemaDoc, redefineComp, SchemaSymbols.ELT_ATTRIBUTEGROUP,
                                    lName, targetLName);
                        }
                        else if ((componentType.equals(SchemaSymbols.ELT_COMPLEXTYPE)) ||
                                (componentType.equals(SchemaSymbols.ELT_SIMPLETYPE))) {
                            checkForDuplicateNames(qName, fUnparsedTypeRegistry, fUnparsedTypeRegistrySub, redefineComp, currSchemaDoc);
                            // the check will have changed our name;
                            String targetLName = DOMUtil.getAttrValue(redefineComp, SchemaSymbols.ATT_NAME) + REDEF_IDENTIFIER;
                            // and all we need to do is error-check+rename our kkids:
                            if (componentType.equals(SchemaSymbols.ELT_COMPLEXTYPE)) {
                                renameRedefiningComponents(currSchemaDoc, redefineComp, SchemaSymbols.ELT_COMPLEXTYPE,
                                        lName, targetLName);
                            }
                            else { // must be simpleType
                                renameRedefiningComponents(currSchemaDoc, redefineComp, SchemaSymbols.ELT_SIMPLETYPE,
                                        lName, targetLName);
                            }
                        }
                        else if (componentType.equals(SchemaSymbols.ELT_GROUP)) {
                            checkForDuplicateNames(qName, fUnparsedGroupRegistry, fUnparsedGroupRegistrySub, redefineComp, currSchemaDoc);
                            // the check will have changed our name;
                            String targetLName = DOMUtil.getAttrValue(redefineComp, SchemaSymbols.ATT_NAME)+REDEF_IDENTIFIER;
                            // and all we need to do is error-check+rename our kids:
                            renameRedefiningComponents(currSchemaDoc, redefineComp, SchemaSymbols.ELT_GROUP,
                                    lName, targetLName);
                        }
                    } // end march through <redefine> children
                    // and now set as traversed
                    //DOMUtil.setHidden(globalComp);
                }
                else {
                    dependenciesCanOccur = false;
                    String lName = DOMUtil.getAttrValue(globalComp, SchemaSymbols.ATT_NAME);
                    if (lName.length() == 0) // an error we'll catch later
                        continue;
                    String qName = currSchemaDoc.fTargetNamespace == null?
                            ","+lName:
                                currSchemaDoc.fTargetNamespace +","+lName;
                    String componentType = DOMUtil.getLocalName(globalComp);
                    if (componentType.equals(SchemaSymbols.ELT_ATTRIBUTE)) {
                        checkForDuplicateNames(qName, fUnparsedAttributeRegistry, fUnparsedAttributeRegistrySub, globalComp, currSchemaDoc);
                    }
                    else if (componentType.equals(SchemaSymbols.ELT_ATTRIBUTEGROUP)) {
                        checkForDuplicateNames(qName, fUnparsedAttributeGroupRegistry, fUnparsedAttributeGroupRegistrySub, globalComp, currSchemaDoc);
                    }
                    else if ((componentType.equals(SchemaSymbols.ELT_COMPLEXTYPE)) ||
                            (componentType.equals(SchemaSymbols.ELT_SIMPLETYPE))) {
                        checkForDuplicateNames(qName, fUnparsedTypeRegistry, fUnparsedTypeRegistrySub, globalComp, currSchemaDoc);
                    }
                    else if (componentType.equals(SchemaSymbols.ELT_ELEMENT)) {
                        checkForDuplicateNames(qName, fUnparsedElementRegistry, fUnparsedElementRegistrySub, globalComp, currSchemaDoc);
                    }
                    else if (componentType.equals(SchemaSymbols.ELT_GROUP)) {
                        checkForDuplicateNames(qName, fUnparsedGroupRegistry, fUnparsedGroupRegistrySub, globalComp, currSchemaDoc);
                    }
                    else if (componentType.equals(SchemaSymbols.ELT_NOTATION)) {
                        checkForDuplicateNames(qName, fUnparsedNotationRegistry, fUnparsedNotationRegistrySub, globalComp, currSchemaDoc);
                    }
                }
            } // end for
            
            // now we're done with this one!
           	DOMUtil.setHidden(currDoc, fHiddenNodes);
            // now add the schemas this guy depends on
            Vector currSchemaDepends = (Vector)fDependencyMap.get(currSchemaDoc);
            for (int i = 0; i < currSchemaDepends.size(); i++) {
                schemasToProcess.push(currSchemaDepends.elementAt(i));
            }
        } // while

    } // end buildGlobalNameRegistries
    
    // Beginning at the first schema processing was requested for
    // (fRoot), this method
    // examines each child (global schema information item) of each
    // schema document (and of each <redefine> element)
    // corresponding to an XSDocumentInfo object.  If the
    // readOnly field on that node has not been set, it calls an
    // appropriate traverser to traverse it.  Once all global decls in
    // an XSDocumentInfo object have been traversed, it marks that object
    // as traversed (or hidden) in order to avoid infinite loops.  It completes
    // when it has visited all XSDocumentInfo objects in the
    // DependencyMap and marked them as traversed.
    protected void traverseSchemas(ArrayList annotationInfo) {
        // the process here is very similar to that in
        // buildGlobalRegistries, except we can't set our schemas as
        // hidden for a second time; so make them all visible again
        // first!
        setSchemasVisible(fRoot);
        Stack schemasToProcess = new Stack();
        schemasToProcess.push(fRoot);
        while (!schemasToProcess.empty()) {
            XSDocumentInfo currSchemaDoc =
                (XSDocumentInfo)schemasToProcess.pop();
            Element currDoc = currSchemaDoc.fSchemaElement;
       
            SchemaGrammar currSG = fGrammarBucket.getGrammar(currSchemaDoc.fTargetNamespace);

            if(DOMUtil.isHidden(currDoc, fHiddenNodes)) {
                // must have processed this already!
                continue;
            }
            Element currRoot = currDoc;
            boolean sawAnnotation = false;
            // traverse this schema's global decls
            for (Element globalComp =
                DOMUtil.getFirstVisibleChildElement(currRoot, fHiddenNodes);
            globalComp != null;
            globalComp = DOMUtil.getNextVisibleSiblingElement(globalComp, fHiddenNodes)) {
                DOMUtil.setHidden(globalComp, fHiddenNodes); 
                String componentType = DOMUtil.getLocalName(globalComp);
                // includes and imports will not show up here!
                if (DOMUtil.getLocalName(globalComp).equals(SchemaSymbols.ELT_REDEFINE)) {
                    // use the namespace decls for the redefine, instead of for the parent <schema>
                    currSchemaDoc.backupNSSupport((SchemaNamespaceSupport)fRedefine2NSSupport.get(globalComp));
                    for (Element redefinedComp = DOMUtil.getFirstVisibleChildElement(globalComp, fHiddenNodes);
                    redefinedComp != null;
                    redefinedComp = DOMUtil.getNextVisibleSiblingElement(redefinedComp, fHiddenNodes)) {
                        String redefinedComponentType = DOMUtil.getLocalName(redefinedComp);
                        DOMUtil.setHidden(redefinedComp, fHiddenNodes);
                        if (redefinedComponentType.equals(SchemaSymbols.ELT_ATTRIBUTEGROUP)) {
                            fAttributeGroupTraverser.traverseGlobal(redefinedComp, currSchemaDoc, currSG);
                        }
                        else if (redefinedComponentType.equals(SchemaSymbols.ELT_COMPLEXTYPE)) {
                            fComplexTypeTraverser.traverseGlobal(redefinedComp, currSchemaDoc, currSG);
                        }
                        else if (redefinedComponentType.equals(SchemaSymbols.ELT_GROUP)) {
                            fGroupTraverser.traverseGlobal(redefinedComp, currSchemaDoc, currSG);
                        }
                        else if (redefinedComponentType.equals(SchemaSymbols.ELT_SIMPLETYPE)) {
                            fSimpleTypeTraverser.traverseGlobal(redefinedComp, currSchemaDoc, currSG);
                        }
                        // annotations will have been processed already; this is now
                        // unnecessary
                        //else if (redefinedComponentType.equals(SchemaSymbols.ELT_ANNOTATION)) {
                        //    fElementTraverser.traverseAnnotationDecl(redefinedComp, null, true, currSchemaDoc);
                        //}
                        else {
                            reportSchemaError("s4s-elt-must-match.1", new Object [] {DOMUtil.getLocalName(globalComp), "(annotation | (simpleType | complexType | group | attributeGroup))*", redefinedComponentType}, redefinedComp);
                        }
                    } // end march through <redefine> children
                    currSchemaDoc.restoreNSSupport();
                }
                else if (componentType.equals(SchemaSymbols.ELT_ATTRIBUTE)) {
                    fAttributeTraverser.traverseGlobal(globalComp, currSchemaDoc, currSG);
                }
                else if (componentType.equals(SchemaSymbols.ELT_ATTRIBUTEGROUP)) {
                    fAttributeGroupTraverser.traverseGlobal(globalComp, currSchemaDoc, currSG);
                }
                else if (componentType.equals(SchemaSymbols.ELT_COMPLEXTYPE)) {
                    fComplexTypeTraverser.traverseGlobal(globalComp, currSchemaDoc, currSG);
                }
                else if (componentType.equals(SchemaSymbols.ELT_ELEMENT)) {
                    fElementTraverser.traverseGlobal(globalComp, currSchemaDoc, currSG);
                }
                else if (componentType.equals(SchemaSymbols.ELT_GROUP)) {
                    fGroupTraverser.traverseGlobal(globalComp, currSchemaDoc, currSG);
                }
                else if (componentType.equals(SchemaSymbols.ELT_NOTATION)) {
                    fNotationTraverser.traverse(globalComp, currSchemaDoc, currSG);
                }
                else if (componentType.equals(SchemaSymbols.ELT_SIMPLETYPE)) {
                    fSimpleTypeTraverser.traverseGlobal(globalComp, currSchemaDoc, currSG);
                }
                else if (componentType.equals(SchemaSymbols.ELT_ANNOTATION)) {
                    currSG.addAnnotation(fElementTraverser.traverseAnnotationDecl(globalComp, currSchemaDoc.getSchemaAttrs(), true, currSchemaDoc));
                    sawAnnotation = true;
                }
                else {
                    reportSchemaError("s4s-elt-invalid-content.1", new Object [] {SchemaSymbols.ELT_SCHEMA, DOMUtil.getLocalName(globalComp)}, globalComp);
                }
            } // end for
            
            if (!sawAnnotation) {
                String text = DOMUtil.getSyntheticAnnotation(currRoot);
                if (text != null) {
                    currSG.addAnnotation(fElementTraverser.traverseSyntheticAnnotation(currRoot, text, currSchemaDoc.getSchemaAttrs(), true, currSchemaDoc));
                }
            }
            
            /** Collect annotation information for validation. **/
            if (annotationInfo != null) {
                XSAnnotationInfo info = currSchemaDoc.getAnnotations();
                /** Only add annotations to the list if there were any in this document. **/
                if (info != null) {
                    annotationInfo.add(doc2SystemId(currDoc));
                    annotationInfo.add(info);
                }
            }
            // now we're done with this one!
            currSchemaDoc.returnSchemaAttrs();
            DOMUtil.setHidden(currDoc, fHiddenNodes);

            // now add the schemas this guy depends on
            Vector currSchemaDepends = (Vector)fDependencyMap.get(currSchemaDoc);
            for (int i = 0; i < currSchemaDepends.size(); i++) {
                schemasToProcess.push(currSchemaDepends.elementAt(i));
            }
        } // while
    } // end traverseSchemas
    
    // store whether we have reported an error about that no grammar
    // is found for the given namespace uri
    private Vector fReportedTNS = null;
    // check whether we need to report an error against the given uri.
    // if we have reported an error, then we don't need to report again;
    // otherwise we reported the error, and remember this fact.
    private final boolean needReportTNSError(String uri) {
        if (fReportedTNS == null)
            fReportedTNS = new Vector();
        else if (fReportedTNS.contains(uri))
            return false;
        fReportedTNS.addElement(uri);
        return true;
    }
    
    private static final String[] COMP_TYPE = {
            null,               // index 0
            "attribute declaration",
            "attribute group",
            "element declaration",
            "group",
            "identity constraint",
            "notation",
            "type definition",
    };
    
    private static final String[] CIRCULAR_CODES = {
            "Internal-Error",
            "Internal-Error",
            "src-attribute_group.3",
            "e-props-correct.6",
            "mg-props-correct.2",
            "Internal-Error",
            "Internal-Error",
            "st-props-correct.2",       //or ct-props-correct.3
    };
    
    // since it is forbidden for traversers to talk to each other
    // directly (except wen a traverser encounters a local declaration),
    // this provides a generic means for a traverser to call
    // for the traversal of some declaration.  An XSDocumentInfo is
    // required because the XSDocumentInfo that the traverser is traversing
    // may bear no relation to the one the handler is operating on.
    // This method will:
    // 1.  See if a global definition matching declToTraverse exists;
    // 2. if so, determine if there is a path from currSchema to the
    // schema document where declToTraverse lives (i.e., do a lookup
    // in DependencyMap);
    // 3. depending on declType (which will be relevant to step 1 as
    // well), call the appropriate traverser with the appropriate
    // XSDocumentInfo object.
    // This method returns whatever the traverser it called returned;
    // this will be an Object of some kind
    // that lives in the Grammar.
    protected Object getGlobalDecl(XSDocumentInfo currSchema,
            int declType,
            QName declToTraverse,
            Element elmNode) {
        
        if (DEBUG_NODE_POOL) {
            System.out.println("TRAVERSE_GL: "+declToTraverse.toString());
        }
        // from the schema spec, all built-in types are present in all schemas,
        // so if the requested component is a type, and could be found in the
        // default schema grammar, we should return that type.
        // otherwise (since we would support user-defined schema grammar) we'll
        // use the normal way to get the decl
        if (declToTraverse.uri != null &&
                declToTraverse.uri == SchemaSymbols.URI_SCHEMAFORSCHEMA) {
            if (declType == TYPEDECL_TYPE) {
                Object retObj = SchemaGrammar.SG_SchemaNS.getGlobalTypeDecl(declToTraverse.localpart);
                if (retObj != null)
                    return retObj;
            }
        }
        
        // now check whether this document can access the requsted namespace
        if (!currSchema.isAllowedNS(declToTraverse.uri)) {
            // cannot get to this schema from the one containing the requesting decl
            if (currSchema.needReportTNSError(declToTraverse.uri)) {
                String code = declToTraverse.uri == null ? "src-resolve.4.1" : "src-resolve.4.2";
                reportSchemaError(code, new Object[]{fDoc2SystemId.get(currSchema.fSchemaElement), declToTraverse.uri, declToTraverse.rawname}, elmNode);
            }
            return null;
        }
        
        // check whether there is grammar for the requested namespace
        SchemaGrammar sGrammar = fGrammarBucket.getGrammar(declToTraverse.uri);
        if (sGrammar == null) {
            if (needReportTNSError(declToTraverse.uri))
                reportSchemaError("src-resolve", new Object[]{declToTraverse.rawname, COMP_TYPE[declType]}, elmNode);
            return null;
        }
        
        // if there is such grammar, check whether the requested component is in the grammar
        Object retObj = null;
        switch (declType) {
        case ATTRIBUTE_TYPE :
            retObj = sGrammar.getGlobalAttributeDecl(declToTraverse.localpart);
            break;
        case ATTRIBUTEGROUP_TYPE :
            retObj = sGrammar.getGlobalAttributeGroupDecl(declToTraverse.localpart);
            break;
        case ELEMENT_TYPE :
            retObj = sGrammar.getGlobalElementDecl(declToTraverse.localpart);
            break;
        case GROUP_TYPE :
            retObj = sGrammar.getGlobalGroupDecl(declToTraverse.localpart);
            break;
        case IDENTITYCONSTRAINT_TYPE :
            retObj = sGrammar.getIDConstraintDecl(declToTraverse.localpart);
            break;
        case NOTATION_TYPE :
            retObj = sGrammar.getGlobalNotationDecl(declToTraverse.localpart);
            break;
        case TYPEDECL_TYPE :
            retObj = sGrammar.getGlobalTypeDecl(declToTraverse.localpart);
            break;
        }
        
        // if the component is parsed, return it
        if (retObj != null)
            return retObj;
        
        XSDocumentInfo schemaWithDecl = null;
        Element decl = null;
        XSDocumentInfo declDoc = null;
        
        // the component is not parsed, try to find a DOM element for it
        String declKey = declToTraverse.uri == null? ","+declToTraverse.localpart:
            declToTraverse.uri+","+declToTraverse.localpart;
        switch (declType) {
        case ATTRIBUTE_TYPE :
            decl = (Element)fUnparsedAttributeRegistry.get(declKey);
            declDoc = (XSDocumentInfo)fUnparsedAttributeRegistrySub.get(declKey);
            break;
        case ATTRIBUTEGROUP_TYPE :
            decl = (Element)fUnparsedAttributeGroupRegistry.get(declKey);
            declDoc = (XSDocumentInfo)fUnparsedAttributeGroupRegistrySub.get(declKey);
            break;
        case ELEMENT_TYPE :
            decl = (Element)fUnparsedElementRegistry.get(declKey);
            declDoc = (XSDocumentInfo)fUnparsedElementRegistrySub.get(declKey);
            break;
        case GROUP_TYPE :
            decl = (Element)fUnparsedGroupRegistry.get(declKey);
            declDoc = (XSDocumentInfo)fUnparsedGroupRegistrySub.get(declKey);
            break;
        case IDENTITYCONSTRAINT_TYPE :
            decl = (Element)fUnparsedIdentityConstraintRegistry.get(declKey);
            declDoc = (XSDocumentInfo)fUnparsedIdentityConstraintRegistrySub.get(declKey);
            break;
        case NOTATION_TYPE :
            decl = (Element)fUnparsedNotationRegistry.get(declKey);
            declDoc = (XSDocumentInfo)fUnparsedNotationRegistrySub.get(declKey);
            break;
        case TYPEDECL_TYPE :
            decl = (Element)fUnparsedTypeRegistry.get(declKey);
            declDoc = (XSDocumentInfo)fUnparsedTypeRegistrySub.get(declKey);
            break;
        default:
            reportSchemaError("Internal-Error", new Object [] {"XSDHandler asked to locate component of type " + declType + "; it does not recognize this type!"}, elmNode);
        }
        
        // no DOM element found, so the component can't be located
        if (decl == null) {
            reportSchemaError("src-resolve", new Object[]{declToTraverse.rawname, COMP_TYPE[declType]}, elmNode);
            return null;
        }
        
        // get the schema doc containing the component to be parsed
        // it should always return non-null value, but since null-checking
        // comes for free, let's be safe and check again
        schemaWithDecl = findXSDocumentForDecl(currSchema, decl, declDoc);
        if (schemaWithDecl == null) {
            // cannot get to this schema from the one containing the requesting decl
            String code = declToTraverse.uri == null ? "src-resolve.4.1" : "src-resolve.4.2";
            reportSchemaError(code, new Object[]{fDoc2SystemId.get(currSchema.fSchemaElement), declToTraverse.uri, declToTraverse.rawname}, elmNode);
            return null;
        }
        // a component is hidden, meaning either it's traversed, or being traversed.
        // but we didn't find it in the grammar, so it's the latter case, and
        // a circular reference. error!
        if (DOMUtil.isHidden(decl, fHiddenNodes)) {
            String code = CIRCULAR_CODES[declType];
            if (declType == TYPEDECL_TYPE) {
                if (SchemaSymbols.ELT_COMPLEXTYPE.equals(DOMUtil.getLocalName(decl)))
                    code = "ct-props-correct.3";
            }
            // decl must not be null if we're here...
            reportSchemaError(code, new Object [] {declToTraverse.prefix+":"+declToTraverse.localpart}, elmNode);
            return null;
        }
        
        DOMUtil.setHidden(decl, fHiddenNodes);
        SchemaNamespaceSupport nsSupport = null;
        // if the parent is <redefine> use the namespace delcs for it.
        Element parent = DOMUtil.getParent(decl);
        if (DOMUtil.getLocalName(parent).equals(SchemaSymbols.ELT_REDEFINE))
            nsSupport = (SchemaNamespaceSupport)fRedefine2NSSupport.get(parent);
        // back up the current SchemaNamespaceSupport, because we need to provide
        // a fresh one to the traverseGlobal methods.
        schemaWithDecl.backupNSSupport(nsSupport);
        
        // traverse the referenced global component
        switch (declType) {
        case ATTRIBUTE_TYPE :
            retObj = fAttributeTraverser.traverseGlobal(decl, schemaWithDecl, sGrammar);
            break;
        case ATTRIBUTEGROUP_TYPE :
            retObj = fAttributeGroupTraverser.traverseGlobal(decl, schemaWithDecl, sGrammar);
            break;
        case ELEMENT_TYPE :
            retObj = fElementTraverser.traverseGlobal(decl, schemaWithDecl, sGrammar);
            break;
        case GROUP_TYPE :
            retObj = fGroupTraverser.traverseGlobal(decl, schemaWithDecl, sGrammar);
            break;
        case IDENTITYCONSTRAINT_TYPE :
            // identity constraints should have been parsed already...
            // we should never get here
            retObj = null;
            break;
        case NOTATION_TYPE :
            retObj = fNotationTraverser.traverse(decl, schemaWithDecl, sGrammar);
            break;
        case TYPEDECL_TYPE :
            if (DOMUtil.getLocalName(decl).equals(SchemaSymbols.ELT_COMPLEXTYPE))
                retObj = fComplexTypeTraverser.traverseGlobal(decl, schemaWithDecl, sGrammar);
            else
                retObj = fSimpleTypeTraverser.traverseGlobal(decl, schemaWithDecl, sGrammar);
        }
        
        // restore the previous SchemaNamespaceSupport, so that the caller can get
        // proper namespace binding.
        schemaWithDecl.restoreNSSupport();
        
        return retObj;
    } // getGlobalDecl(XSDocumentInfo, int, QName):  Object
    
    // This method determines whether there is a group
    // (attributeGroup) which the given one has redefined by
    // restriction.  If so, it returns it; else it returns null.
    // @param type:  whether what's been redefined is an
    // attributeGroup or a group;
    // @param name:  the QName of the component doing the redefining.
    // @param currSchema:  schema doc in which the redefining component lives.
    // @return:  Object representing decl redefined if present, null
    // otherwise.
    Object getGrpOrAttrGrpRedefinedByRestriction(int type, QName name, XSDocumentInfo currSchema, Element elmNode) {
        String realName = name.uri != null?name.uri+","+name.localpart:
            ","+name.localpart;
        String nameToFind = null;
        switch (type) {
        case ATTRIBUTEGROUP_TYPE:
            nameToFind = (String)fRedefinedRestrictedAttributeGroupRegistry.get(realName);
            break;
        case GROUP_TYPE:
            nameToFind = (String)fRedefinedRestrictedGroupRegistry.get(realName);
            break;
        default:
            return null;
        }
        if (nameToFind == null) return null;
        int commaPos = nameToFind.indexOf(",");
        QName qNameToFind = new QName(XMLSymbols.EMPTY_STRING, nameToFind.substring(commaPos+1),
                nameToFind.substring(commaPos), (commaPos == 0)? null : nameToFind.substring(0, commaPos));
        Object retObj = getGlobalDecl(currSchema, type, qNameToFind, elmNode);
        if(retObj == null) {
            switch (type) {
            case ATTRIBUTEGROUP_TYPE:
                reportSchemaError("src-redefine.7.2.1", new Object []{name.localpart}, elmNode);
                break;
            case GROUP_TYPE:
                reportSchemaError("src-redefine.6.2.1", new Object []{name.localpart}, elmNode);
                break;
            }
            return null;
        }
        return retObj;
    } // getGrpOrAttrGrpRedefinedByRestriction(int, QName, XSDocumentInfo):  Object
    
    // Since ID constraints can occur in local elements, unless we
    // wish to completely traverse all our DOM trees looking for ID
    // constraints while we're building our global name registries,
    // which seems terribly inefficient, we need to resolve keyrefs
    // after all parsing is complete.  This we can simply do by running through
    // fIdentityConstraintRegistry and calling traverseKeyRef on all
    // of the KeyRef nodes.  This unfortunately removes this knowledge
    // from the elementTraverser class (which must ignore keyrefs),
    // but there seems to be no efficient way around this...
    protected void resolveKeyRefs() {
        for (int i=0; i<fKeyrefStackPos; i++) {
            XSDocumentInfo keyrefSchemaDoc = fKeyrefsMapXSDocumentInfo[i];
            keyrefSchemaDoc.fNamespaceSupport.makeGlobal();
            keyrefSchemaDoc.fNamespaceSupport.setEffectiveContext( fKeyrefNamespaceContext[i] );
            SchemaGrammar keyrefGrammar = fGrammarBucket.getGrammar(keyrefSchemaDoc.fTargetNamespace);
            // need to set <keyref> to hidden before traversing it,
            // because it has global scope
           	DOMUtil.setHidden(fKeyrefs[i], fHiddenNodes);
            fKeyrefTraverser.traverse(fKeyrefs[i], fKeyrefElems[i], keyrefSchemaDoc, keyrefGrammar);
        }
    } // end resolveKeyRefs
    
    // an accessor method.  Just makes sure callers
    // who want the Identity constraint registry vaguely know what they're about.
    protected Hashtable getIDRegistry() {
        return fUnparsedIdentityConstraintRegistry;
    }
    // an accessor method.  
    protected Hashtable getIDRegistry_sub() {
        return fUnparsedIdentityConstraintRegistrySub;
    }
    
   
    
    // This method squirrels away <keyref> declarations--along with the element
    // decls and namespace bindings they might find handy.
    protected void storeKeyRef (Element keyrefToStore, XSDocumentInfo schemaDoc,
            XSElementDecl currElemDecl) {
        String keyrefName = DOMUtil.getAttrValue(keyrefToStore, SchemaSymbols.ATT_NAME);
        if (keyrefName.length() != 0) {
            String keyrefQName = schemaDoc.fTargetNamespace == null?
                    "," + keyrefName: schemaDoc.fTargetNamespace+","+keyrefName;
            checkForDuplicateNames(keyrefQName, fUnparsedIdentityConstraintRegistry, fUnparsedIdentityConstraintRegistrySub, keyrefToStore, schemaDoc);
        }
        // now set up all the registries we'll need...
        
        // check array sizes
        if (fKeyrefStackPos == fKeyrefs.length) {
            Element [] elemArray = new Element [fKeyrefStackPos + INC_KEYREF_STACK_AMOUNT];
            System.arraycopy(fKeyrefs, 0, elemArray, 0, fKeyrefStackPos);
            fKeyrefs = elemArray;
            XSElementDecl [] declArray = new XSElementDecl [fKeyrefStackPos + INC_KEYREF_STACK_AMOUNT];
            System.arraycopy(fKeyrefElems, 0, declArray, 0, fKeyrefStackPos);
            fKeyrefElems = declArray;
            String[][] stringArray = new String [fKeyrefStackPos + INC_KEYREF_STACK_AMOUNT][];
            System.arraycopy(fKeyrefNamespaceContext, 0, stringArray, 0, fKeyrefStackPos);
            fKeyrefNamespaceContext = stringArray;
            
            XSDocumentInfo [] xsDocumentInfo = new XSDocumentInfo [fKeyrefStackPos + INC_KEYREF_STACK_AMOUNT];
            System.arraycopy(fKeyrefsMapXSDocumentInfo, 0, xsDocumentInfo, 0, fKeyrefStackPos);
            fKeyrefsMapXSDocumentInfo = xsDocumentInfo;
            
        }
        fKeyrefs[fKeyrefStackPos] = keyrefToStore;
        fKeyrefElems[fKeyrefStackPos] = currElemDecl;
        fKeyrefNamespaceContext[fKeyrefStackPos] = schemaDoc.fNamespaceSupport.getEffectiveLocalContext();
        
        fKeyrefsMapXSDocumentInfo[fKeyrefStackPos++] = schemaDoc; 
    } // storeKeyref (Element, XSDocumentInfo, XSElementDecl): void
    
    
    /**
     * resolveSchema method is responsible for resolving location of the schema (using XMLEntityResolver),
     * and if it was succefully resolved getting the schema Document.
     * @param desc
     * @param mustResolve
     * @param referElement
     * @return A schema Element or null.
     */
    private Element resolveSchema(XSDDescription desc, boolean mustResolve,
                                  Element referElement, boolean usePairs) {
        XMLInputSource schemaSource = null;
        try {
            Hashtable pairs = usePairs ? fLocationPairs : EMPTY_TABLE;
            schemaSource = XMLSchemaLoader.resolveDocument(desc, pairs, fEntityResolver);
        }
        catch (IOException ex) {
            if (mustResolve) {
                reportSchemaError("schema_reference.4",
                        new Object[]{desc.getLocationHints()[0]},
                        referElement);
            }
            else {
                reportSchemaWarning("schema_reference.4",
                        new Object[]{desc.getLocationHints()[0]},
                        referElement);
            }
        }
        if (schemaSource instanceof DOMInputSource) {
            fHiddenNodes.clear();
            Node node = ((DOMInputSource)schemaSource).getNode();
 
            if (node instanceof Document) {
                return DOMUtil.getRoot((Document) node);
            }
            else if (node instanceof Element) {
                return (Element) node;
            }
            else {
                return null;
            }
        } // DOMInputSource
        else if (schemaSource instanceof SAXInputSource) {
            XMLReader parser = ((SAXInputSource)schemaSource).getXMLReader();
            InputSource inputSource = ((SAXInputSource)schemaSource).getInputSource(); 
            boolean namespacePrefixes = false;
            if (parser != null) {
                try {
                    namespacePrefixes = parser.getFeature(NAMESPACE_PREFIXES);
                }
                catch (SAXException se) {}
            }
            else {
                try {
                    parser = XMLReaderFactory.createXMLReader();
                }
                // If something went wrong with the factory
                // just use our own SAX parser.
                catch (SAXException se) {
                    parser = new SAXParser();
                }
                try {
                    parser.setFeature(NAMESPACE_PREFIXES, true);
                    namespacePrefixes = true;
                }
                catch (SAXException se) {}
            }
            // If XML names and Namespace URIs are already internalized we
            // can avoid running them through the SymbolTable.
            boolean stringsInternalized = false;
            try {
                stringsInternalized = parser.getFeature(STRING_INTERNING);
            }
            catch (SAXException exc) {
                // The feature isn't recognized or getting it is not supported.
                // In either case, assume that strings are not internalized.
            }
            if (fXSContentHandler == null) {
                fXSContentHandler = new SchemaContentHandler();
            }
            fXSContentHandler.reset(fSchemaParser, fSymbolTable, 
                    namespacePrefixes, stringsInternalized);
            parser.setContentHandler(fXSContentHandler);
            parser.setErrorHandler(fErrorReporter.getSAXErrorHandler());
            try {
                parser.parse(inputSource);  
            }
            catch (SAXException se) {
                return null;
            }
            catch (IOException ioe) {
                return null;
            }
            Document root = fXSContentHandler.getDocument();
            if (root == null) {
                // something went wrong right off the hop
                return null;
            }
            return DOMUtil.getRoot(root);          
        }
        else if (schemaSource instanceof StAXInputSource) {
            StAXInputSource sis = (StAXInputSource)schemaSource;
            if (fStAXSchemaParser == null) {
                fStAXSchemaParser = new StAXSchemaParser();
            }
            fStAXSchemaParser.reset(fSchemaParser, fSymbolTable);
            try {
                if (sis.getXMLEventReader() != null) {
                    fStAXSchemaParser.parse(sis.getXMLEventReader());
                }
                else {
                    fStAXSchemaParser.parse(sis.getXMLStreamReader());
                }
            }
            catch (XMLStreamException e) {
                return null;
            }
            Document root = fStAXSchemaParser.getDocument();
            if (root == null) {
                return null;
            }
            return DOMUtil.getRoot(root);
        }
        return getSchemaDocument(desc.getTargetNamespace(), schemaSource, mustResolve, desc.getContextType(), referElement);
    } // getSchema(String, String, String, boolean, short):  Document
    
    /**
     * getSchemaDocument method uses XMLInputSource to parse a schema document.
     * @param schemaNamespace
     * @param schemaSource
     * @param mustResolve
     * @param referType
     * @param referElement
     * @return A schema Element.
     */
    private Element getSchemaDocument(String schemaNamespace, XMLInputSource schemaSource,
            boolean mustResolve, short referType, Element referElement) {
        
        boolean hasInput = true;
        // contents of this method will depend on the system we adopt for entity resolution--i.e., XMLEntityHandler, EntityHandler, etc.
        Element schemaElement = null;
        try {
            // when the system id and byte stream and character stream
            // of the input source are all null, it's
            // impossible to find the schema document. so we skip in
            // this case. otherwise we'll receive some NPE or
            // file not found errors. but schemaHint=="" is perfectly
            // legal for import.
            if (schemaSource != null &&
                    (schemaSource.getSystemId() != null ||
                            schemaSource.getByteStream() != null ||
                            schemaSource.getCharacterStream() != null)) {
                
                // When the system id of the input source is used, first try to
                // expand it, and check whether the same document has been
                // parsed before. If so, return the document corresponding to
                // that system id.
                XSDKey key = null;
                String schemaId = null;
                if (referType != XSDDescription.CONTEXT_PREPARSE){
                    schemaId = XMLEntityManager.expandSystemId(schemaSource.getSystemId(), schemaSource.getBaseSystemId(), false);
                    key = new XSDKey(schemaId, referType, schemaNamespace);
                    if((schemaElement = (Element)fTraversed.get(key)) != null) {
                        fLastSchemaWasDuplicate = true;
                        return schemaElement;
                    }
                }
                
                fSchemaParser.parse(schemaSource);
                schemaElement = fSchemaParser.getDocument() == null ? null: DOMUtil.getRoot(fSchemaParser.getDocument());
                
                // now we need to store the mapping information from system id
                // to the document. also from the document to the system id.
                if (key != null)
                    fTraversed.put(key, schemaElement );
                if (schemaId != null)
                    fDoc2SystemId.put(schemaElement, schemaId );
                fLastSchemaWasDuplicate = false;
                return schemaElement;
            }
            else {
                hasInput = false;
            }
        }
        catch (IOException ex) {
        }
        
        // either an error occured (exception), or empty input source was
        // returned, we need to report an error or a warning
        if (mustResolve) {
            if (hasInput) {
                reportSchemaError("schema_reference.4",
                        new Object[]{schemaSource.getSystemId()},
                        referElement);
            }
            else {
                reportSchemaError("schema_reference.4",
                        new Object[]{schemaSource == null ? "" : schemaSource.getSystemId()},
                        referElement);
            }
        }
        else if (hasInput) {
            reportSchemaWarning("schema_reference.4",
                    new Object[]{schemaSource.getSystemId()},
                    referElement);
        }
        
        fLastSchemaWasDuplicate = false;
        return null;
    } // getSchema(String, XMLInputSource, boolean, boolean): Document
    
    // initialize all the traversers.
    // this should only need to be called once during the construction
    // of this object; it creates the traversers that will be used to
    
    // construct schemaGrammars.
    private void createTraversers() {
        fAttributeChecker = new XSAttributeChecker(this);
        fAttributeGroupTraverser = new XSDAttributeGroupTraverser(this, fAttributeChecker);
        fAttributeTraverser = new XSDAttributeTraverser(this, fAttributeChecker);
        fComplexTypeTraverser = new XSDComplexTypeTraverser(this, fAttributeChecker);
        fElementTraverser = new XSDElementTraverser(this, fAttributeChecker);
        fGroupTraverser = new XSDGroupTraverser(this, fAttributeChecker);
        fKeyrefTraverser = new XSDKeyrefTraverser(this, fAttributeChecker);
        fNotationTraverser = new XSDNotationTraverser(this, fAttributeChecker);
        fSimpleTypeTraverser = new XSDSimpleTypeTraverser(this, fAttributeChecker);
        fTypeAlternativeTraverser = new XSDTypeAlternativeTraverser(this, fAttributeChecker);
        fUniqueOrKeyTraverser = new XSDUniqueOrKeyTraverser(this, fAttributeChecker);
        fWildCardTraverser = new XSDWildcardTraverser(this, fAttributeChecker);
    } // createTraversers()
    
    // before parsing a schema, need to clear registries associated with
    // parsing schemas
    void prepareForParse() {
        fTraversed.clear();
        fDoc2SystemId.clear();
        fHiddenNodes.clear();
        fLastSchemaWasDuplicate = false;
    }
    
    // before traversing a schema's parse tree, need to reset all traversers and
    // clear all registries
    void prepareForTraverse() {
        fUnparsedAttributeRegistry.clear();
        fUnparsedAttributeGroupRegistry.clear();
        fUnparsedElementRegistry.clear();
        fUnparsedGroupRegistry.clear();
        fUnparsedIdentityConstraintRegistry.clear();
        fUnparsedNotationRegistry.clear();
        fUnparsedTypeRegistry.clear();
        
        fUnparsedAttributeRegistrySub.clear();
        fUnparsedAttributeGroupRegistrySub.clear();
        fUnparsedElementRegistrySub.clear();
        fUnparsedGroupRegistrySub.clear();
        fUnparsedIdentityConstraintRegistrySub.clear();
        fUnparsedNotationRegistrySub.clear();
        fUnparsedTypeRegistrySub.clear();
        
        fXSDocumentInfoRegistry.clear();
        fDependencyMap.clear();
        fDoc2XSDocumentMap.clear();
        fRedefine2XSDMap.clear();
        fRedefine2NSSupport.clear();
        fAllTNSs.removeAllElements();
        fImportMap.clear();
        fRoot = null;
        
        // clear local element stack
        for (int i = 0; i < fLocalElemStackPos; i++) {
            fParticle[i] = null;
            fLocalElementDecl[i] = null;
            fLocalElementDecl_schema[i] = null;
            fLocalElemNamespaceContext[i] = null;
        }
        fLocalElemStackPos = 0;
        
        // and do same for keyrefs.
        for (int i = 0; i < fKeyrefStackPos; i++) {
            fKeyrefs[i] = null;
            fKeyrefElems[i] = null;
            fKeyrefNamespaceContext[i] = null;
            fKeyrefsMapXSDocumentInfo[i] = null;
        }
        fKeyrefStackPos = 0;
        
        // create traversers if necessary
        if (fAttributeChecker == null) {
            createTraversers();
        }
        
        // reset traversers
        fAttributeChecker.reset(fSymbolTable);
        fAttributeGroupTraverser.reset(fSymbolTable, fValidateAnnotations);
        fAttributeTraverser.reset(fSymbolTable, fValidateAnnotations);
        fComplexTypeTraverser.reset(fSymbolTable, fValidateAnnotations);
        fElementTraverser.reset(fSymbolTable, fValidateAnnotations);
        fGroupTraverser.reset(fSymbolTable, fValidateAnnotations);
        fKeyrefTraverser.reset(fSymbolTable, fValidateAnnotations);
        fNotationTraverser.reset(fSymbolTable, fValidateAnnotations);
        fSimpleTypeTraverser.reset(fSymbolTable, fValidateAnnotations);
        fTypeAlternativeTraverser.reset(fSymbolTable, fValidateAnnotations);
        fUniqueOrKeyTraverser.reset(fSymbolTable, fValidateAnnotations);
        fWildCardTraverser.reset(fSymbolTable, fValidateAnnotations);
        
        fRedefinedRestrictedAttributeGroupRegistry.clear();
        fRedefinedRestrictedGroupRegistry.clear();
    }
    public void setDeclPool (XSDeclarationPool declPool){
        fDeclPool = declPool;
    }
    
    public void reset(XMLComponentManager componentManager) {
        
        // set symbol table
        fSymbolTable = (SymbolTable) componentManager.getProperty(SYMBOL_TABLE);
        
        //set entity resolver
        fEntityResolver = (XMLEntityResolver) componentManager.getProperty(ENTITY_MANAGER);
        XMLEntityResolver er = (XMLEntityResolver)componentManager.getProperty(ENTITY_RESOLVER);
        if (er != null)
            fSchemaParser.setEntityResolver(er);
        
        // set error reporter
        fErrorReporter =
            (XMLErrorReporter) componentManager.getProperty(ERROR_REPORTER);
        try {
            XMLErrorHandler currErrorHandler = fErrorReporter.getErrorHandler();
            // Setting a parser property can be much more expensive
            // than checking its value.  Don't set the ERROR_HANDLER
            // property unless it's actually changed.
            if (currErrorHandler != fSchemaParser.getProperty(ERROR_HANDLER)) {
                fSchemaParser.setProperty(ERROR_HANDLER, (currErrorHandler != null) ? currErrorHandler : new DefaultErrorHandler());
                if (fAnnotationValidator != null) {
                    fAnnotationValidator.setProperty(ERROR_HANDLER, (currErrorHandler != null) ? currErrorHandler : new DefaultErrorHandler());
            	}
            }
        } catch (XMLConfigurationException e) {
        }
        
        try {
            fValidateAnnotations = componentManager.getFeature(VALIDATE_ANNOTATIONS);
        } catch (XMLConfigurationException e) {
            fValidateAnnotations = false;
        }
        
        try {
            fHonourAllSchemaLocations = componentManager.getFeature(HONOUR_ALL_SCHEMALOCATIONS);
        } catch (XMLConfigurationException e) {
            fHonourAllSchemaLocations = false;
        }

        try {
            fSchemaParser.setFeature(
                    CONTINUE_AFTER_FATAL_ERROR,
                    fErrorReporter.getFeature(CONTINUE_AFTER_FATAL_ERROR));
        } catch (XMLConfigurationException e) {
        }
        
        try {
            fSchemaParser.setFeature(
                    ALLOW_JAVA_ENCODINGS,
                    componentManager.getFeature(ALLOW_JAVA_ENCODINGS));
        } catch (XMLConfigurationException e) {
        }
        try {
            fSchemaParser.setFeature(
                    STANDARD_URI_CONFORMANT_FEATURE,
                    componentManager.getFeature(STANDARD_URI_CONFORMANT_FEATURE));
        } catch (XMLConfigurationException e) {
        }
        
        try {
            fGrammarPool =
                (XMLGrammarPool) componentManager.getProperty(XMLGRAMMAR_POOL);
        } catch (XMLConfigurationException e) {
            fGrammarPool = null;
        }
        // security features
        try {
            fSchemaParser.setFeature( DISALLOW_DOCTYPE,
                    componentManager.getFeature(DISALLOW_DOCTYPE));
        } catch (XMLConfigurationException e) {
        }
        try {
            Object security = componentManager.getProperty(SECURITY_MANAGER);
            if (security != null){
                fSchemaParser.setProperty(SECURITY_MANAGER, security);
            }
        } catch (XMLConfigurationException e) {
        }
        
    } // reset(XMLComponentManager)
    
    
    /**
     * Traverse all the deferred local elements. This method should be called
     * by traverseSchemas after we've done with all the global declarations.
     */
    void traverseLocalElements() {
        fElementTraverser.fDeferTraversingLocalElements = false;
        
        for (int i = 0; i < fLocalElemStackPos; i++) {
            Element currElem = fLocalElementDecl[i];
            //XSDocumentInfo currSchema = (XSDocumentInfo)fDoc2XSDocumentMap.get(DOMUtil.getDocument(currElem));
            //XSDocumentInfo currSchema = (XSDocumentInfo)fDoc2XSDocumentMap.get(DOMUtil.getRoot(DOMUtil.getDocument(currElem)));
            XSDocumentInfo currSchema = fLocalElementDecl_schema[i];
            SchemaGrammar currGrammar = fGrammarBucket.getGrammar(currSchema.fTargetNamespace);
            fElementTraverser.traverseLocal (fParticle[i], currElem, currSchema, currGrammar, fAllContext[i], fParent[i], fLocalElemNamespaceContext[i]);
            // If it's an empty particle, remove it from the containing component.
            if (fParticle[i].fType == XSParticleDecl.PARTICLE_EMPTY) {
                XSModelGroupImpl group = null;
                if (fParent[i] instanceof XSComplexTypeDecl) {
                    XSParticle p = ((XSComplexTypeDecl)fParent[i]).getParticle();
                    if (p != null)
                        group = (XSModelGroupImpl)p.getTerm();
                }
                else {
                    group = ((XSGroupDecl)fParent[i]).fModelGroup;
                }
                if (group != null)
                    removeParticle(group, fParticle[i]);
            }
        }
    }
    
    private boolean removeParticle(XSModelGroupImpl group, XSParticleDecl particle) {
        XSParticleDecl member;
        for (int i = 0; i < group.fParticleCount; i++) {
            member = group.fParticles[i];
            if (member == particle) {
                for (int j = i; j < group.fParticleCount-1; j++)
                    group.fParticles[j] = group.fParticles[j+1];
                group.fParticleCount--;
                return true;
            }
            if (member.fType == XSParticleDecl.PARTICLE_MODELGROUP) {
                if (removeParticle((XSModelGroupImpl)member.fValue, particle))
                    return true;
            }
        }
        return false;
    }
    
    // the purpose of this method is to keep up-to-date structures
    // we'll need for the feferred traversal of local elements.
    void fillInLocalElemInfo(Element elmDecl,
            XSDocumentInfo schemaDoc,
            int allContextFlags,
            XSObject parent,
            XSParticleDecl particle) {
        
        // if the stack is full, increase the size
        if (fParticle.length == fLocalElemStackPos) {
            // increase size
            XSParticleDecl[] newStackP = new XSParticleDecl[fLocalElemStackPos+INC_STACK_SIZE];
            System.arraycopy(fParticle, 0, newStackP, 0, fLocalElemStackPos);
            fParticle = newStackP;
            Element[] newStackE = new Element[fLocalElemStackPos+INC_STACK_SIZE];
            System.arraycopy(fLocalElementDecl, 0, newStackE, 0, fLocalElemStackPos);
            fLocalElementDecl = newStackE;
            XSDocumentInfo [] newStackE_schema = new XSDocumentInfo[fLocalElemStackPos+INC_STACK_SIZE];
            System.arraycopy(fLocalElementDecl_schema, 0, newStackE_schema, 0, fLocalElemStackPos);
            fLocalElementDecl_schema = newStackE_schema;
            int[] newStackI = new int[fLocalElemStackPos+INC_STACK_SIZE];
            System.arraycopy(fAllContext, 0, newStackI, 0, fLocalElemStackPos);
            fAllContext = newStackI;
            XSObject[] newStackC = new XSObject[fLocalElemStackPos+INC_STACK_SIZE];
            System.arraycopy(fParent, 0, newStackC, 0, fLocalElemStackPos);
            fParent = newStackC;
            String [][] newStackN = new String [fLocalElemStackPos+INC_STACK_SIZE][];
            System.arraycopy(fLocalElemNamespaceContext, 0, newStackN, 0, fLocalElemStackPos);
            fLocalElemNamespaceContext = newStackN;
        }
        
        fParticle[fLocalElemStackPos] = particle;
        fLocalElementDecl[fLocalElemStackPos] = elmDecl;
        fLocalElementDecl_schema[fLocalElemStackPos] = schemaDoc;
        fAllContext[fLocalElemStackPos] = allContextFlags;
        fParent[fLocalElemStackPos] = parent;
        fLocalElemNamespaceContext[fLocalElemStackPos++] = schemaDoc.fNamespaceSupport.getEffectiveLocalContext();
    } // end fillInLocalElemInfo(...)
    
    /** This method makes sure that
     * if this component is being redefined that it lives in the
     * right schema.  It then renames the component correctly.  If it
     * detects a collision--a duplicate definition--then it complains.
     * Note that redefines must be handled carefully:  if there
     * is a collision, it may be because we're redefining something we know about
     * or because we've found the thing we're redefining.
     */
    void checkForDuplicateNames(String qName,
    		Hashtable registry, Hashtable registry_sub, Element currComp,
			XSDocumentInfo currSchema) {
        Object objElem = null;
        // REVISIT:  when we add derivation checking, we'll have to make
        // sure that ID constraint collisions don't necessarily result in error messages.
        if ((objElem = registry.get(qName)) == null) {
            // just add it in!
            registry.put(qName, currComp);
            registry_sub.put(qName, currSchema);
            
        }
        else {
            Element collidingElem = (Element)objElem;
            XSDocumentInfo collidingElemSchema = (XSDocumentInfo)registry_sub.get(qName);
            if (collidingElem == currComp) return;
            Element elemParent = null;
            XSDocumentInfo redefinedSchema = null;
            // case where we've collided with a redefining element
            // (the parent of the colliding element is a redefine)
            boolean collidedWithRedefine = true;
            if ((DOMUtil.getLocalName((elemParent = DOMUtil.getParent(collidingElem))).equals(SchemaSymbols.ELT_REDEFINE))) {
                redefinedSchema = (XSDocumentInfo)(fRedefine2XSDMap.get(elemParent));
                // case where we're a redefining element.
            }
            else if ((DOMUtil.getLocalName(DOMUtil.getParent(currComp)).equals(SchemaSymbols.ELT_REDEFINE))) {
                redefinedSchema = collidingElemSchema;
                collidedWithRedefine = false;
            }
            if (redefinedSchema != null) { //redefinition involved somehow
                // If both components belong to the same document then
                // report an error and return.
                if(collidingElemSchema == currSchema){
                    reportSchemaError("sch-props-correct.2", new Object[]{qName}, currComp);
                    return;
                }
                
                String newName = qName.substring(qName.lastIndexOf(',')+1)+REDEF_IDENTIFIER;
                if (redefinedSchema == currSchema) { // object comp. okay here
                    // now have to do some renaming...
                    currComp.setAttribute(SchemaSymbols.ATT_NAME, newName);
                    if (currSchema.fTargetNamespace == null){
                        registry.put(","+newName, currComp);
                        registry_sub.put(","+newName, currSchema);
                    }
                    else{
                        registry.put(currSchema.fTargetNamespace+","+newName, currComp);
                        registry_sub.put(currSchema.fTargetNamespace+","+newName, currSchema);
                    }
                    // and take care of nested redefines by calling recursively:
                    if (currSchema.fTargetNamespace == null)
                    	checkForDuplicateNames(","+newName, registry, registry_sub, currComp, currSchema);
                    else
                    	checkForDuplicateNames(currSchema.fTargetNamespace+","+newName, registry, registry_sub, currComp, currSchema);
                }
                else { // we may be redefining the wrong schema
                    if (collidedWithRedefine) {
                        if (currSchema.fTargetNamespace == null)
                            checkForDuplicateNames(","+newName, registry, registry_sub, currComp, currSchema);
                        else
                            checkForDuplicateNames(currSchema.fTargetNamespace+","+newName, registry, registry_sub, currComp, currSchema);
                    }
                    else {
                        // error that redefined element in wrong schema
                        reportSchemaError("sch-props-correct.2", new Object [] {qName}, currComp);
                    }
                }
            }
            else {
                // we've just got a flat-out collision
                reportSchemaError("sch-props-correct.2", new Object []{qName}, currComp);
            }
        }
    } // checkForDuplicateNames(String, Hashtable, Element, XSDocumentInfo):void
    
    // the purpose of this method is to take the component of the
    // specified type and rename references to itself so that they
    // refer to the object being redefined.  It takes special care of
    // <group>s and <attributeGroup>s to ensure that information
    // relating to implicit restrictions is preserved for those
    // traversers.
    private void renameRedefiningComponents(XSDocumentInfo currSchema,
            Element child, String componentType,
            String oldName, String newName) {
        if (componentType.equals(SchemaSymbols.ELT_SIMPLETYPE)) {
            Element grandKid = DOMUtil.getFirstChildElement(child);
            if (grandKid == null) {
                reportSchemaError("src-redefine.5.a.a", null, child);
            }
            else {
                String grandKidName = DOMUtil.getLocalName(grandKid);
                if (grandKidName.equals(SchemaSymbols.ELT_ANNOTATION)) {
                    grandKid = DOMUtil.getNextSiblingElement(grandKid);
                }
                if (grandKid == null) {
                    reportSchemaError("src-redefine.5.a.a", null, child);
                }
                else {
                    grandKidName = DOMUtil.getLocalName(grandKid);
                    if (!grandKidName.equals(SchemaSymbols.ELT_RESTRICTION)) {
                        reportSchemaError("src-redefine.5.a.b", new Object[]{grandKidName}, child);
                    }
                    else {
                        Object[] attrs = fAttributeChecker.checkAttributes(grandKid, false, currSchema);
                        QName derivedBase = (QName)attrs[XSAttributeChecker.ATTIDX_BASE];
                        if (derivedBase == null ||
                                derivedBase.uri != currSchema.fTargetNamespace ||
                                !derivedBase.localpart.equals(oldName)) {
                            reportSchemaError("src-redefine.5.a.c",
                                    new Object[]{grandKidName,
                                    (currSchema.fTargetNamespace==null?"":currSchema.fTargetNamespace)
                                    + "," + oldName},
                                    child);
                        }
                        else {
                            // now we have to do the renaming...
                            if (derivedBase.prefix != null && derivedBase.prefix.length() > 0)
                                grandKid.setAttribute( SchemaSymbols.ATT_BASE,
                                        derivedBase.prefix + ":" + newName );
                            else
                                grandKid.setAttribute( SchemaSymbols.ATT_BASE, newName );
                            //                            return true;
                        }
                        fAttributeChecker.returnAttrArray(attrs, currSchema);
                    }
                }
            }
        }
        else if (componentType.equals(SchemaSymbols.ELT_COMPLEXTYPE)) {
            Element grandKid = DOMUtil.getFirstChildElement(child);
            if (grandKid == null) {
                reportSchemaError("src-redefine.5.b.a", null, child);
            }
            else {
                if (DOMUtil.getLocalName(grandKid).equals(SchemaSymbols.ELT_ANNOTATION)) {
                    grandKid = DOMUtil.getNextSiblingElement(grandKid);
                }
                if (grandKid == null) {
                    reportSchemaError("src-redefine.5.b.a", null, child);
                }
                else {
                    // have to go one more level down; let another pass worry whether complexType is valid.
                    Element greatGrandKid = DOMUtil.getFirstChildElement(grandKid);
                    if (greatGrandKid == null) {
                        reportSchemaError("src-redefine.5.b.b", null, grandKid);
                    }
                    else {
                        String greatGrandKidName = DOMUtil.getLocalName(greatGrandKid);
                        if (greatGrandKidName.equals(SchemaSymbols.ELT_ANNOTATION)) {
                            greatGrandKid = DOMUtil.getNextSiblingElement(greatGrandKid);
                        }
                        if (greatGrandKid == null) {
                            reportSchemaError("src-redefine.5.b.b", null, grandKid);
                        }
                        else {
                            greatGrandKidName = DOMUtil.getLocalName(greatGrandKid);
                            if (!greatGrandKidName.equals(SchemaSymbols.ELT_RESTRICTION) &&
                                    !greatGrandKidName.equals(SchemaSymbols.ELT_EXTENSION)) {
                                reportSchemaError("src-redefine.5.b.c", new Object[]{greatGrandKidName}, greatGrandKid);
                            }
                            else {
                                Object[] attrs = fAttributeChecker.checkAttributes(greatGrandKid, false, currSchema);
                                QName derivedBase = (QName)attrs[XSAttributeChecker.ATTIDX_BASE];
                                if (derivedBase == null ||
                                        derivedBase.uri != currSchema.fTargetNamespace ||
                                        !derivedBase.localpart.equals(oldName)) {
                                    reportSchemaError("src-redefine.5.b.d",
                                            new Object[]{greatGrandKidName,
                                            (currSchema.fTargetNamespace==null?"":currSchema.fTargetNamespace)
                                            + "," + oldName},
                                            greatGrandKid);
                                }
                                else {
                                    // now we have to do the renaming...
                                    if (derivedBase.prefix != null && derivedBase.prefix.length() > 0)
                                        greatGrandKid.setAttribute( SchemaSymbols.ATT_BASE,
                                                derivedBase.prefix + ":" + newName );
                                    else
                                        greatGrandKid.setAttribute( SchemaSymbols.ATT_BASE,
                                                newName );
                                    //                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        else if (componentType.equals(SchemaSymbols.ELT_ATTRIBUTEGROUP)) {
            String processedBaseName = (currSchema.fTargetNamespace == null)?
                    ","+oldName:currSchema.fTargetNamespace+","+oldName;
            int attGroupRefsCount = changeRedefineGroup(processedBaseName, componentType, newName, child, currSchema);
            if (attGroupRefsCount > 1) {
                reportSchemaError("src-redefine.7.1", new Object []{new Integer(attGroupRefsCount)}, child);
            }
            else if (attGroupRefsCount == 1) {
                //                return true;
            }
            else
                if (currSchema.fTargetNamespace == null)
                    fRedefinedRestrictedAttributeGroupRegistry.put(processedBaseName, ","+newName);
                else
                    fRedefinedRestrictedAttributeGroupRegistry.put(processedBaseName, currSchema.fTargetNamespace+","+newName);
        }
        else if (componentType.equals(SchemaSymbols.ELT_GROUP)) {
            String processedBaseName = (currSchema.fTargetNamespace == null)?
                    ","+oldName:currSchema.fTargetNamespace+","+oldName;
            int groupRefsCount = changeRedefineGroup(processedBaseName, componentType, newName, child, currSchema);
            if (groupRefsCount > 1) {
                reportSchemaError("src-redefine.6.1.1", new Object []{new Integer(groupRefsCount)}, child);
            }
            else if (groupRefsCount == 1) {
                //                return true;
            }
            else {
                if (currSchema.fTargetNamespace == null)
                    fRedefinedRestrictedGroupRegistry.put(processedBaseName, ","+newName);
                else
                    fRedefinedRestrictedGroupRegistry.put(processedBaseName, currSchema.fTargetNamespace+","+newName);
            }
        }
        else {
            reportSchemaError("Internal-Error", new Object [] {"could not handle this particular <redefine>; please submit your schemas and instance document in a bug report!"}, child);
        }
        // if we get here then we must have reported an error and failed somewhere...
        //        return false;
    } // renameRedefiningComponents(XSDocumentInfo, Element, String, String, String):void
    
    // this method takes a name of the form a:b, determines the URI mapped
    // to by a in the current SchemaNamespaceSupport object, and returns this
    // information in the form (nsURI,b) suitable for lookups in the global
    // decl Hashtables.
    // REVISIT: should have it return QName, instead of String. this would
    //          save lots of string concatenation time. we can use
    //          QName#equals() to compare two QNames, and use QName directly
    //          as a key to the SymbolHash.
    //          And when the DV's are ready to return compiled values from
    //          validate() method, we should just call QNameDV.validate()
    //          in this method.
    private String findQName(String name, XSDocumentInfo schemaDoc) {
        SchemaNamespaceSupport currNSMap = schemaDoc.fNamespaceSupport;
        int colonPtr = name.indexOf(':');
        String prefix = XMLSymbols.EMPTY_STRING;
        if (colonPtr > 0)
            prefix = name.substring(0, colonPtr);
        String uri = currNSMap.getURI(fSymbolTable.addSymbol(prefix));
        String localpart = (colonPtr == 0)?name:name.substring(colonPtr+1);
        if (prefix == XMLSymbols.EMPTY_STRING && uri == null && schemaDoc.fIsChameleonSchema)
            uri = schemaDoc.fTargetNamespace;
        if (uri == null)
            return ","+localpart;
        return uri+","+localpart;
    } // findQName(String, XSDocumentInfo):  String
    
    // This function looks among the children of curr for an element of type elementSought.
    // If it finds one, it evaluates whether its ref attribute contains a reference
    // to originalQName.  If it does, it returns 1 + the value returned by
    // calls to itself on all other children.  In all other cases it returns 0 plus
    // the sum of the values returned by calls to itself on curr's children.
    // It also resets the value of ref so that it will refer to the renamed type from the schema
    // being redefined.
    private int changeRedefineGroup(String originalQName, String elementSought,
            String newName, Element curr, XSDocumentInfo schemaDoc) {
        int result = 0;
        for (Element child = DOMUtil.getFirstChildElement(curr);
        child != null; child = DOMUtil.getNextSiblingElement(child)) {
            String name = DOMUtil.getLocalName(child);
            if (!name.equals(elementSought))
                result += changeRedefineGroup(originalQName, elementSought, newName, child, schemaDoc);
            else {
                String ref = child.getAttribute( SchemaSymbols.ATT_REF );
                if (ref.length() != 0) {
                    String processedRef = findQName(ref, schemaDoc);
                    if (originalQName.equals(processedRef)) {
                        String prefix = XMLSymbols.EMPTY_STRING;
                        int colonptr = ref.indexOf(":");
                        if (colonptr > 0) {
                            prefix = ref.substring(0,colonptr);
                            child.setAttribute(SchemaSymbols.ATT_REF, prefix + ":" + newName);
                        }
                        else
                            child.setAttribute(SchemaSymbols.ATT_REF, newName);
                        result++;
                        if (elementSought.equals(SchemaSymbols.ELT_GROUP)) {
                            String minOccurs = child.getAttribute( SchemaSymbols.ATT_MINOCCURS );
                            String maxOccurs = child.getAttribute( SchemaSymbols.ATT_MAXOCCURS );
                            if (!((maxOccurs.length() == 0 || maxOccurs.equals("1"))
                                    && (minOccurs.length() == 0 || minOccurs.equals("1")))) {
                                reportSchemaError("src-redefine.6.1.2", new Object [] {ref}, child);
                            }
                        }
                    }
                } // if ref was null some other stage of processing will flag the error
            }
        }
        return result;
    } // changeRedefineGroup
    
    // this method returns the XSDocumentInfo object that contains the
    // component corresponding to decl.  If components from this
    // document cannot be referred to from those of currSchema, this
    // method returns null; it's up to the caller to throw an error.
    // @param:  currSchema:  the XSDocumentInfo object containing the
    // decl ref'ing us.
    // @param:  decl:  the declaration being ref'd.
    // this method is superficial now. ---Jack
    private XSDocumentInfo findXSDocumentForDecl(XSDocumentInfo currSchema,
            Element decl, XSDocumentInfo decl_Doc) {
        
        if (DEBUG_NODE_POOL) {
            System.out.println("DOCUMENT NS:"+ currSchema.fTargetNamespace+" hashcode:"+ ((Object)currSchema.fSchemaElement).hashCode());
        }
        Object temp = decl_Doc;
        if (temp == null) {
            // something went badly wrong; we don't know this doc?
            return null;
        }
        XSDocumentInfo declDocInfo = (XSDocumentInfo)temp;
        return declDocInfo;
        /*********
         Logic here is unnecessary after schema WG's recent decision to allow
         schema components from one document to refer to components of any other,
         so long as there's some include/import/redefine path amongst them.
         If they rver reverse this decision the code's right here though...  - neilg
         // now look in fDependencyMap to see if this is reachable
          if(((Vector)fDependencyMap.get(currSchema)).contains(declDocInfo)) {
          return declDocInfo;
          }
          // obviously the requesting doc didn't include, redefine or
           // import the one containing decl...
            return null;
            **********/
    } // findXSDocumentForDecl(XSDocumentInfo, Element):  XSDocumentInfo
    
    // returns whether more than <annotation>s occur in children of elem
    private boolean nonAnnotationContent(Element elem) {
        for(Element child = DOMUtil.getFirstChildElement(elem); child != null; child = DOMUtil.getNextSiblingElement(child)) {
            if(!(DOMUtil.getLocalName(child).equals(SchemaSymbols.ELT_ANNOTATION))) return true;
        }
        return false;
    } // nonAnnotationContent(Element):  boolean
    
    private void setSchemasVisible(XSDocumentInfo startSchema) {
        if (DOMUtil.isHidden(startSchema.fSchemaElement, fHiddenNodes)) {
            // make it visible
            DOMUtil.setVisible(startSchema.fSchemaElement, fHiddenNodes);           
            Vector dependingSchemas = (Vector)fDependencyMap.get(startSchema);
            for (int i = 0; i < dependingSchemas.size(); i++) {
                setSchemasVisible((XSDocumentInfo)dependingSchemas.elementAt(i));
            }
        }
        // if it's visible already than so must be its children
    } // setSchemasVisible(XSDocumentInfo): void
    
    private SimpleLocator xl = new SimpleLocator();
    
    /**
     * Extract location information from an Element node, and create a
     * new SimpleLocator object from such information. Returning null means
     * no information can be retrieved from the element.
     */
    public SimpleLocator element2Locator(Element e) {
        if (!( e instanceof ElementImpl))
            return null;
        
        SimpleLocator l = new SimpleLocator();
        return element2Locator(e, l) ? l : null;
    }
    
    /**
     * Extract location information from an Element node, store such
     * information in the passed-in SimpleLocator object, then return
     * true. Returning false means can't extract or store such information.
     */
    public boolean element2Locator(Element e, SimpleLocator l) {
        if (l == null)
            return false;
        if (e instanceof ElementImpl) {
            ElementImpl ele = (ElementImpl)e;
            // get system id from document object
            Document doc = ele.getOwnerDocument();
            String sid = (String)fDoc2SystemId.get(DOMUtil.getRoot(doc));
            // line/column numbers are stored in the element node
            int line = ele.getLineNumber();
            int column = ele.getColumnNumber();
            l.setValues(sid, sid, line, column, ele.getCharacterOffset());
            return true;
        }
        return false;
    }
    
    void reportSchemaError(String key, Object[] args, Element ele) {
        if (element2Locator(ele, xl)) {
            fErrorReporter.reportError(xl, XSMessageFormatter.SCHEMA_DOMAIN,
                    key, args, XMLErrorReporter.SEVERITY_ERROR);
        }
        else {
            fErrorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                    key, args, XMLErrorReporter.SEVERITY_ERROR);
        }
    }
    
    void reportSchemaWarning(String key, Object[] args, Element ele) {
        if (element2Locator(ele, xl)) {
            fErrorReporter.reportError(xl, XSMessageFormatter.SCHEMA_DOMAIN,
                    key, args, XMLErrorReporter.SEVERITY_WARNING);
        }
        else {
            fErrorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                    key, args, XMLErrorReporter.SEVERITY_WARNING);
        }
    }
    
    /**
     * Grammar pool used for validating annotations. This will return all of the
     * grammars from the grammar bucket. It will also return an object for the
     * schema for schemas which will contain at least the relevant declarations
     * for annotations.
     */
    private static class XSAnnotationGrammarPool implements XMLGrammarPool {
        
        private XSGrammarBucket fGrammarBucket;
        private Grammar [] fInitialGrammarSet;
        
        public Grammar[] retrieveInitialGrammarSet(String grammarType) {
            if (grammarType == XMLGrammarDescription.XML_SCHEMA) {
                if (fInitialGrammarSet == null) {
                    if (fGrammarBucket == null) {
                        fInitialGrammarSet = new Grammar [] {SchemaGrammar.Schema4Annotations.INSTANCE};
                    }
                    else {
                        SchemaGrammar [] schemaGrammars = fGrammarBucket.getGrammars();
                        /** 
                         * If the grammar bucket already contains the schema for schemas
                         * then we already have the definitions for the parts relevant
                         * to annotations.
                         */
                        for (int i = 0; i < schemaGrammars.length; ++i) {
                            if (SchemaSymbols.URI_SCHEMAFORSCHEMA.equals(schemaGrammars[i].getTargetNamespace())) {
                                fInitialGrammarSet = schemaGrammars;
                                return fInitialGrammarSet;
                            }
                        }
                        Grammar [] grammars = new Grammar[schemaGrammars.length + 1];
                        System.arraycopy(schemaGrammars, 0, grammars, 0, schemaGrammars.length);
                        grammars[grammars.length - 1] = SchemaGrammar.Schema4Annotations.INSTANCE;
                        fInitialGrammarSet = grammars;
                    }
                }
                return fInitialGrammarSet;
            }
            return new Grammar[0];
        }

        public void cacheGrammars(String grammarType, Grammar[] grammars) {
            
        }

        public Grammar retrieveGrammar(XMLGrammarDescription desc) {
            if (desc.getGrammarType() == XMLGrammarDescription.XML_SCHEMA) {
                final String tns = ((XMLSchemaDescription) desc).getTargetNamespace();
                if (fGrammarBucket != null) {
                    Grammar grammar = fGrammarBucket.getGrammar(tns);
                    if (grammar != null) {
                        return grammar;
                    }
                }
                if (SchemaSymbols.URI_SCHEMAFORSCHEMA.equals(tns)) {
                    return SchemaGrammar.Schema4Annotations.INSTANCE;
                }
            }
            return null;
        }
        
        public void refreshGrammars(XSGrammarBucket gBucket) {
            fGrammarBucket = gBucket;
            fInitialGrammarSet = null;
        }

        public void lockPool() {}

        public void unlockPool() {}

        public void clear() {}
    }

    /**
     * used to identify a reference to a schema document
     * if the same document is referenced twice with the same key, then
     * we only need to parse it once.
     *
     * When 2 XSDKey's are compared, the following table can be used to
     * determine whether they are equal:
     *      inc     red     imp     pre     ins
     * inc  N/L      ?      N/L     N/L     N/L
     * red   ?      N/L      ?       ?       ?
     * imp  N/L      ?      N/P     N/P     N/P
     * pre  N/L      ?      N/P     N/P     N/P
     * ins  N/L      ?      N/P     N/P     N/P
     *
     * Where: N/L: duplicate when they have the same namespace and location.
     *         ? : not clear from the spec.
     *             REVISIT: to simplify the process, also considering
     *             it's very rare, we treat them as not duplicate.
     *        N/P: not possible. imp/pre/ins are referenced by namespace.
     *             when the first time we encounter a schema document for a
     *             namespace, we create a grammar and store it in the grammar
     *             bucket. when we see another reference to the same namespace,
     *             we first check whether a grammar with the same namespace is
     *             already in the bucket, which is true in this case, so we
     *             won't create another XSDKey.
     *
     * Conclusion from the table: two XSDKey's are duplicate only when all of
     * the following are true:
     * 1. They are both "redefine", or neither is "redefine";
     * 2. They have the same namespace;
     * 3. They have the same non-null location.
     *
     * About 3: if neither has a non-null location, then it's the case where
     * 2 input streams are provided, but no system ID is provided. We can't tell
     * whether the 2 streams have the same content, so we treat them as not
     * duplicate.
     */
    private static class XSDKey {
        String systemId;
        short  referType;
        // for inclue/redefine, this is the enclosing namespace
        // for import/preparse/instance, this is the target namespace
        String referNS;
        
        XSDKey(String systemId, short referType, String referNS) {
            this.systemId = systemId;
            this.referType = referType;
            this.referNS = referNS;
        }
        
        public int hashCode() {
            // according to the description at the beginning of this class,
            // we use the hashcode of the namespace as the hashcoe of this key.
            return referNS == null ? 0 : referNS.hashCode();
        }
        
        public boolean equals(Object obj) {
            if (!(obj instanceof XSDKey)) {
                return false;
            }
            XSDKey key = (XSDKey)obj;
            
            // condition 1: both are redefine
            /** if (referType == XSDDescription.CONTEXT_REDEFINE ||
                    key.referType == XSDDescription.CONTEXT_REDEFINE) {
                if (referType != key.referType)
                    return false;
            }**/
            
            // condition 2: same namespace
            if (referNS != key.referNS)
                return false;
            
            // condition 3: same non-null location
            if (systemId == null || !systemId.equals(key.systemId)) {
                return false;
            }
            
            return true;
        }
    }
    
    /**
     * @param state
     */
    public void setGenerateSyntheticAnnotations(boolean state) {
        fSchemaParser.setFeature(GENERATE_SYNTHETIC_ANNOTATIONS, state);
    }
    
    /**
     * Set schema 1.1 support
     *
     * @param state
     */
    public void setSchemaVersion(short version) {
        fSchemaVersion = version;
        if (version < Constants.SCHEMA_VERSION_1_1) {
            fSupportedVersion = SUPPORTED_VERSION_1_0;
        }
        else {
            fSupportedVersion = SUPPORTED_VERSION_1_1;
        }
        fSchemaParser.setSupportedVersion(fSupportedVersion);
    }

    public short getSchemaVersion() {
        return fSchemaVersion;
    }

} // XSDHandler
