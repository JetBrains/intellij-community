package com.intellij.openapi.deployment;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author cdr
 * @deprecated
 */
class OrderEntryInfo implements JDOMExternalizable {
  public boolean copy;
  public String URI="";
  private final Map<String,String> attributes = new HashMap<String, String>();
  @NonNls protected static final String ATTRIBUTE_ELEMENT_NAME = "attribute";
  @NonNls protected static final String NAME_ATTR_NAME = "name";
  @NonNls protected static final String VALUE_ATTR_NAME = "value";
  @NonNls protected static final String ATTRIBUTES_ELEMENT_NAME = "attributes";

  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizer.write(element,"copy",copy);
    JDOMExternalizer.write(element,"URI",URI);
    writeAttributes(element);
  }

  private void writeAttributes(Element element) {
    if (attributes.size() == 0) return;
    Element root = new Element(ATTRIBUTES_ELEMENT_NAME);
    element.addContent(root);
    Set<String> names = attributes.keySet();
    for (String name : names) {
      String value = attributes.get(name);
      Element attr = new Element(ATTRIBUTE_ELEMENT_NAME);
      attr.setAttribute(NAME_ATTR_NAME, name);
      attr.setAttribute(VALUE_ATTR_NAME, value);
      root.addContent(attr);
    }
  }
  private void readAttributes(Element element) {
    Element attrs = element.getChild(ATTRIBUTES_ELEMENT_NAME);
    if (attrs == null) return;
    List roots = attrs.getChildren(ATTRIBUTE_ELEMENT_NAME);
    if (roots.size() == 0) return;
    for (Object root : roots) {
      Element attr = (Element)root;
      String name = attr.getAttributeValue(NAME_ATTR_NAME);
      String value = attr.getAttributeValue(VALUE_ATTR_NAME);
      attributes.put(name, value);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    copy = JDOMExternalizer.readBoolean(element,"copy");
    URI = JDOMExternalizer.readString(element,"URI");
    readAttributes(element);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OrderEntryInfo)) return false;

    final OrderEntryInfo orderEntryInfo = (OrderEntryInfo)o;

    if (copy != orderEntryInfo.copy) return false;
    if (URI != null ? !URI.equals(orderEntryInfo.URI) : orderEntryInfo.URI != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = copy ? 1 : 0;
    result = 29 * result + (URI != null ? URI.hashCode() : 0);
    return result;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }
}
