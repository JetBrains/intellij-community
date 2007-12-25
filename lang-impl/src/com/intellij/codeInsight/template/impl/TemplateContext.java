package com.intellij.codeInsight.template.impl;


import com.intellij.openapi.util.*;
import org.jdom.Element;

public class TemplateContext implements JDOMExternalizable, Cloneable {

  public boolean JAVA_CODE = true;
  public boolean JAVA_COMMENT = false;
  public boolean JAVA_STRING = false;
  public boolean XML = false;
  public boolean HTML = false;
  public boolean JSP = false;
  public boolean COMPLETION = false;
  public boolean OTHER = false;

  public Object clone()  {
    try {
      return super.clone();
    }
    catch(CloneNotSupportedException e) {
      return null;
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
