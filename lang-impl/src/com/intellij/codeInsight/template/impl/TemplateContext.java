package com.intellij.codeInsight.template.impl;


import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class TemplateContext implements Cloneable {

  public final ContextElement JAVA_CODE;
  public final ContextElement JAVA_COMMENT;
  public final ContextElement JAVA_STRING;
  public final ContextElement XML;
  public final ContextElement HTML;
  public final ContextElement JSP;
  public final ContextElement COMPLETION;
  public final ContextElement OTHER;

  private final Map<String, Boolean> myAdditionalContexts;

  public TemplateContext() {
    myAdditionalContexts = new LinkedHashMap<String, Boolean>();

    JAVA_CODE = new ContextElement("JAVA_CODE", true);
    JAVA_COMMENT = new ContextElement("JAVA_COMMENT");
    JAVA_STRING = new ContextElement("JAVA_STRING");
    XML = new ContextElement("XML");
    HTML= new ContextElement("HTML");
    JSP = new ContextElement("JSP");
    COMPLETION = new ContextElement("COMPLETION");
    OTHER = new ContextElement("OTHER");

  }

  public class ContextElement {
    private final String myKey;

    public ContextElement(final String key) {
      myKey = key;
    }

    public ContextElement(final String key, boolean defValue) {
      this(key);
      setValue(defValue);
    }

    public boolean getValue() {
      return isEnabled(myKey);
    }

    public void setValue(boolean value) {
      setEnabled(myKey, value);
    }

    @Override
    public String toString() {
      return myKey + " " + getValue();
    }
  }
  public Object clone()  {
    try {
      return super.clone();
    }
    catch(CloneNotSupportedException e) {
      return null;
    }
  }

  public boolean isEnabled(String contextName) {
    Boolean storedValue;
    synchronized (this) {
      storedValue = myAdditionalContexts.get(contextName);
    }
    return storedValue == null ? false : storedValue;
  }

  public synchronized void setEnabled(String contextName, boolean value) {
    myAdditionalContexts.put(contextName, value);
  }

  public void readExternal(Element element) throws InvalidDataException {
    List options = element.getChildren("option");
    for (Object e : options) {
      if (e instanceof Element) {
        Element option = (Element)e;
        String name = option.getAttributeValue("name");
        String value = option.getAttributeValue("value");
        if (name != null && value != null) {
          setEnabled(name, Boolean.parseBoolean(value));
        }
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (String contextName : myAdditionalContexts.keySet()) {
      Element optionElement = new Element("option");
      optionElement.setAttribute("name", contextName);
      optionElement.setAttribute("value", myAdditionalContexts.get(contextName).toString());
      element.addContent(optionElement);
    }
  }
}
