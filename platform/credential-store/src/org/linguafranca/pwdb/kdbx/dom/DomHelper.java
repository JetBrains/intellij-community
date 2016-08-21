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

import com.intellij.util.text.SyncDateFormat;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

//import javax.xml.bind.DatatypeConverter;

/**
 * The class contains static helper methods for access to the underlying XML DOM
 *
 * @author jo
 */
public class DomHelper {

    public static final XPath xpath = XPathFactory.newInstance().newXPath();

    public static SyncDateFormat dateFormatter = new SyncDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"));

    public static final String GROUP_ELEMENT_NAME = "Group";
    public static final String ENTRY_ELEMENT_NAME = "Entry";
    public static final String ICON_ELEMENT_NAME = "IconID";
    public static final String UUID_ELEMENT_NAME = "UUID";
    public static final String NAME_ELEMENT_NAME = "Name";
    public static final String NOTES_ELEMENT_NAME = "Notes";
    public static final String TIMES_ELEMENT_NAME = "Times";
    public static final String IS_EXPANDED = "IsExpanded";

    static final String HISTORY_ELEMENT_NAME = "History";

    public static final String LAST_MODIFICATION_TIME_ELEMENT_NAME = "Times/LastModificationTime";
    public static final String CREATION_TIME_ELEMENT_NAME = "Times/CreationTime";
    public static final String LAST_ACCESS_TIME_ELEMENT_NAME = "Times/LastAccessTime";
    public static final String EXPIRY_TIME_ELEMENT_NAME = "Times/ExpiryTime";
    public static final String EXPIRES_ELEMENT_NAME = "Times/Expires";
    public static final String USAGE_COUNT_ELEMENT_NAME = "Times/UsageCount";
    public static final String LOCATION_CHANGED = "Times/LocationChanged";

    public static final String PROPERTY_ELEMENT_FORMAT = "String[Key/text()='%s']";
    public static final String VALUE_ELEMENT_NAME = "Value";

    public interface ValueCreator {
        String getValue();
    }

    public static class ConstantValueCreator implements ValueCreator {
        String value;
        public ConstantValueCreator(String value) {
            this.value = value;
        }
        @Override
        public String getValue() {
            return value;
        }
    }

    public static class DateValueCreator implements ValueCreator {
        @Override
        public String getValue() {
            return dateFormatter.format(new Date());
        }
    }

    public static class UuidValueCreator implements ValueCreator {
        @Override
        public String getValue() {
            return base64RandomUuid();
        }

    }

    public static void ensureElements (Element element, Map<String, ValueCreator> childElements) {
        for (Map.Entry<String, ValueCreator> entry: childElements.entrySet()) {
            ensureElementContent(entry.getKey(), element, entry.getValue().getValue());
        }
    }


    @Nullable @Contract("_,_,true -> !null")
    public static Element getElement(String elementPath, Element parentElement, boolean create) {
        try {
            Element result = (Element) xpath.evaluate(elementPath, parentElement, XPathConstants.NODE);
            if (result == null && create) {
                result = createHierarchically(elementPath, parentElement);
            }
            return result;
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<Element> getElements (String elementPath, Element parentElement) {
        try {
            NodeList nodes = (NodeList) xpath.evaluate(elementPath, parentElement, XPathConstants.NODESET);
            ArrayList<Element> result = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                result.add(((Element) nodes.item(i)));
            }
            return result;
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int getElementsCount (String elementPath, Element parentElement) {
        try {
            NodeList nodes = (NodeList) xpath.evaluate(elementPath, parentElement, XPathConstants.NODESET);
            return nodes.getLength();
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Element newElement(String elementName, Element parentElement) {
        Element newElement = parentElement.getOwnerDocument().createElement(elementName);
        parentElement.appendChild(newElement);
        return newElement;
    }

    @Nullable
    public static String getElementContent(String elementPath, Element parentElement) {
        Element result = getElement(elementPath, parentElement, false);
        return (result == null) ? null : result.getTextContent();
    }

    @NotNull
    static String ensureElementContent(String elementPath, Element parentElement, @NotNull String value) {
        Element result = getElement(elementPath, parentElement, false);
        if (result == null) {
            result = createHierarchically(elementPath, parentElement);
            result.setTextContent(value);
        }
        return result.getTextContent();
    }

    @NotNull
    public static Element setElementContent(String elementPath, Element parentElement, String value) {
        Element result = getElement(elementPath, parentElement, true);
        result.setTextContent(value);
        return result;
    }

    @NotNull
    public static Element touchElement(String elementPath, Element parentElement) {
        return setElementContent(elementPath, parentElement, dateFormatter.format(new Date()));
    }

    private static Element createHierarchically(String elementPath, Element startElement) {
        Element currentElement = startElement;
        for (String elementName : elementPath.split("/")) {
            try {
                Element nextElement = (Element) xpath.evaluate(elementName, currentElement, XPathConstants.NODE);
                if (nextElement == null) {
                    nextElement = (Element) currentElement.appendChild(currentElement.getOwnerDocument().createElement(elementName));
                }
                currentElement = nextElement;
            } catch (XPathExpressionException e) {
                throw new IllegalStateException(e);
            }
        }
        return currentElement;
    }

    static String base64RandomUuid () {
        return base64FromUuid(UUID.randomUUID());
    }

    static String base64FromUuid(UUID uuid) {
        byte[] buffer = new byte[16];
        ByteBuffer b = ByteBuffer.wrap(buffer);
        b.putLong(uuid.getMostSignificantBits());
        b.putLong(8, uuid.getLeastSignificantBits());
        return Base64.getEncoder().encodeToString(buffer);
    }

    static String hexStringFromUuid(UUID uuid) {
        byte[] buffer = new byte[16];
        ByteBuffer b = ByteBuffer.wrap(buffer);
        b.putLong(uuid.getMostSignificantBits());
        b.putLong(8, uuid.getLeastSignificantBits());
        // round the houses for Android
        return new String(Hex.encodeHex(buffer));
    }

    static String hexStringFromBase64(String base64) {
        byte[] buffer = Base64.getDecoder().decode(base64);
        return new String(Hex.encodeHex(buffer));
    }

    public static UUID uuidFromBase64(@NotNull String base64) {
        byte[] buffer = Base64.getDecoder().decode(base64);
        ByteBuffer b = ByteBuffer.wrap(buffer);
        return new UUID(b.getLong(), b.getLong(8));
    }
}
