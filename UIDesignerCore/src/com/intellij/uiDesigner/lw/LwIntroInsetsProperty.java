package com.intellij.uiDesigner.lw;

import org.jdom.Element;

import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public final class LwIntroInsetsProperty extends LwIntrospectedProperty{
  public LwIntroInsetsProperty(final String name){
    super(name, Insets.class.getName());
  }

  public Object read(final Element element) throws Exception{
    final int top = LwXmlReader.getRequiredInt(element, "top");
    final int left = LwXmlReader.getRequiredInt(element, "left");
    final int bottom = LwXmlReader.getRequiredInt(element, "bottom");
    final int right = LwXmlReader.getRequiredInt(element, "right");
    return new Insets(top, left, bottom, right);
  }
}
