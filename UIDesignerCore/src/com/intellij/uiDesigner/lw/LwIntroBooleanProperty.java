package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public final class LwIntroBooleanProperty extends LwIntrospectedProperty {
  public LwIntroBooleanProperty(final String name){
    super(name, Boolean.class.getName());
  }

  public Object read(final Element element) throws Exception{
    return Boolean.valueOf(LwXmlReader.getRequiredString(element, "value"));
  }
}

