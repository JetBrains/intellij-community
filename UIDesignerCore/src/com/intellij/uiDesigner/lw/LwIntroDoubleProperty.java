package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public final class LwIntroDoubleProperty extends LwIntrospectedProperty {
  public LwIntroDoubleProperty(final String name){
    super(name, Double.class.getName());
  }

  public Object read(final Element element) throws Exception{
    return new Double(LwXmlReader.getRequiredDouble(element, "value"));
  }
}
