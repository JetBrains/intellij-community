package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public final class LwIntroIntProperty extends LwIntrospectedProperty {
  public LwIntroIntProperty(final String name){
    super(name, Integer.class.getName());
  }

  public Object read(final Element element) throws Exception{
    return new Integer(LwXmlReader.getRequiredInt(element, "value"));
  }
}
