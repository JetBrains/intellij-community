package com.intellij.uiDesigner.lw;

import org.jdom.Element;

import java.awt.*;

/**
 * @author Anton Katilin
 */
public final class LwIntroDimensionProperty extends LwIntrospectedProperty {
  public LwIntroDimensionProperty(final String name){
    super(name, Dimension.class.getName());
  }

  public Object read(final Element element) throws Exception{
    final int width = LwXmlReader.getRequiredInt(element, "width");
    final int height = LwXmlReader.getRequiredInt(element, "height");
    return new Dimension(width, height);
  }
}
