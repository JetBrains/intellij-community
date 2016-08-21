/*
 * Copyright 2015 Jo Rabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.linguafranca.pwdb.kdbx.dom;

import org.linguafranca.pwdb.kdbx.Salsa20Encryption;
import org.linguafranca.pwdb.kdbx.SerializableDatabase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;

import static org.linguafranca.pwdb.kdbx.dom.DomHelper.*;

/**
 * This class is an XML DOM implementation of a KDBX database. The data is maintained as a DOM,
 * despite the obvious inefficiency of doing do, in order to maintain transparency on loading and
 * saving of elements and attributes this implementation knows nothing about.
 *
 * <p>Obviously, perhaps, if the database is added to, or under certain types of modification,
 * those elements will be missing from a re-serialization.
 *
 * @author jo
 */
public class DomSerializableDatabase implements SerializableDatabase {

    private Document doc;
    private Encryption encryption;

    private DomSerializableDatabase() {}

    public static DomSerializableDatabase createEmptyDatabase() throws IOException {
        DomSerializableDatabase result = new DomSerializableDatabase();
        // read in the template KeePass XML database
        result.load(result.getClass().getClassLoader().getResourceAsStream("base.kdbx.xml"));
        try {
            // replace all placeholder dates with now
            String now = dateFormatter.format(new Date());
            NodeList list = (NodeList) xpath.evaluate("//*[contains(text(),'${creationDate}')]", result.doc.getDocumentElement(), XPathConstants.NODESET);
            for (int i = 0; i < list.getLength(); i++) {
                list.item(i).setTextContent(now);
            }
            // set the root group UUID
            Node uuid = (Node) xpath.evaluate("//"+ UUID_ELEMENT_NAME, result.doc.getDocumentElement(), XPathConstants.NODE);
            uuid.setTextContent(base64RandomUuid());
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
        result.setEncryption(new Salsa20Encryption(SecureRandom.getSeed(32)));
        return result;
    }

    @Override
    public SerializableDatabase load(InputStream inputStream) throws IOException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            doc = dBuilder.parse(inputStream);

            // we need to decrypt all protected fields
            // TODO we assume they are all strings, which is wrong
            NodeList protectedContent = (NodeList) xpath.evaluate("//*[@Protected='True']", doc, XPathConstants.NODESET);
            for (int i = 0; i < protectedContent.getLength(); i++){
                Element element = ((Element) protectedContent.item(i));
                String base64 = getElementContent(".", element);
                byte[] encrypted = DatatypeConverter.parseBase64Binary(base64);
                String decrypted = new String(encryption.decrypt(encrypted), "UTF-8");
                setElementContent(".", element, decrypted);
                element.removeAttribute("Protected");
            }

            return this;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Instantiating Document Builder", e);
        } catch (SAXException e) {
            throw new IllegalStateException("Parsing exception", e);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("XPath Exception", e);
        }
    }

    @Override
    public void save(OutputStream outputStream) {
        Document copyDoc = (Document) doc.cloneNode(true);
        try {
            // check whether protection is required and if so mark the element with @Protected='True'
            prepareProtection(copyDoc, "Title");
            prepareProtection(copyDoc, "UserName");
            prepareProtection(copyDoc, "Password");
            prepareProtection(copyDoc, "Notes");
            prepareProtection(copyDoc, "URL");

            // encrypt and base64 every element marked as protected
            NodeList protectedContent = (NodeList) xpath.evaluate("//*[@Protected='True']", copyDoc, XPathConstants.NODESET);
            for (int i = 0; i < protectedContent.getLength(); i++){
                Element element = ((Element) protectedContent.item(i));
                String decrypted = getElementContent(".", element);
                if (decrypted == null) {
                    decrypted = "";
                }
                byte[] encrypted = encryption.encrypt(decrypted.getBytes(StandardCharsets.UTF_8));
                setElementContent(".", element, Base64.getEncoder().encodeToString(encrypted));
            }

        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }

        Source xmlSource = new DOMSource(copyDoc);
        Result outputTarget = new StreamResult(outputStream);
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(xmlSource, outputTarget);
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    public static String getStringFromDocument(Document doc) throws TransformerException {
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private static final String protectQuery = "//Meta/MemoryProtection/Protect%s";
    private static final String pattern = "//String/Key[text()='%s']/following-sibling::Value";
    private static void prepareProtection(Document doc, String protect) throws XPathExpressionException {
        // does this require encryption
        String query = String.format(protectQuery, protect);
        if (!((String) xpath.evaluate(query, doc, XPathConstants.STRING)).toLowerCase(Locale.ENGLISH).equals("true")) {
            return;
        }
        // mark the field as Protected but don't actually encrypt yet, that comes later
        String path = String.format(pattern, protect);
        NodeList nodelist = (NodeList) xpath.evaluate(path, doc, XPathConstants.NODESET);
        for (int i = 0; i < nodelist.getLength(); i++) {
            Element element = (Element) nodelist.item(i);
            element.setAttribute("Protected", "True");
        }
    }

    @Override
    public byte[] getHeaderHash() {
        try {
            String base64 = (String) xpath.evaluate("//HeaderHash", doc, XPathConstants.STRING);
            return DatatypeConverter.parseBase64Binary(base64);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Can't get header hash", e);
        }
    }

    @Override
    public void setHeaderHash(byte[] hash) {
        String base64String = DatatypeConverter.printBase64Binary(hash);
        try {
            ((Element) xpath.evaluate("//HeaderHash", doc, XPathConstants.NODE)).setTextContent(base64String);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Can't set header hash", e);
        }
    }


    @Override
    public Encryption getEncryption() {
        return encryption;
    }

    @Override
    public void setEncryption(Encryption encryption) {
        this.encryption = encryption;
    }

    public Document getDoc() {
        return doc;
    }
}
