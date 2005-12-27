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

  private static final int NONE_CONTEXT = 0;
  public static final int JAVA_CODE_CONTEXT = 1;
  public static final int JAVA_COMMENT_CONTEXT = 2;
  public static final int JAVA_STRING_CONTEXT = 3;
  public static final int XML_CONTEXT = 4;
  public static final int HTML_CONTEXT = 5;
  public static final int JSP_CONTEXT = 6;
  public static final int OTHER_CONTEXT = 7;
  public static final int COMPLETION_CONTEXT = 8;

  public Object clone()  {
    try {
      return super.clone();
    }
    catch(CloneNotSupportedException e) {
      return null;
    }
  }

  public boolean isInContext(int contextType) {
    switch(contextType){
      case NONE_CONTEXT:
        return false;

      case JAVA_CODE_CONTEXT:
        return JAVA_CODE;

      case JAVA_COMMENT_CONTEXT:
        return JAVA_COMMENT;

      case JAVA_STRING_CONTEXT:
        return JAVA_STRING;

      case XML_CONTEXT:
        return XML;

      case HTML_CONTEXT:
        return HTML;
      case JSP_CONTEXT:
        return JSP;

      case COMPLETION_CONTEXT:
        return COMPLETION;

      case OTHER_CONTEXT:
        return OTHER;

      default:
        throw new IllegalArgumentException();
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
