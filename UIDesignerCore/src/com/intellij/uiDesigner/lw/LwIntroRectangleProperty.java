package com.intellij.uiDesigner.lw;

import org.jdom.Element;

import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public final class LwIntroRectangleProperty extends LwIntrospectedProperty{
  public LwIntroRectangleProperty(final String name){
    super(name, Rectangle.class.getName());
  }

  public Object read(final Element element) throws Exception{
    final int x = LwXmlReader.getRequiredInt(element, "x");
    final int y = LwXmlReader.getRequiredInt(element, "y");
    final int width = LwXmlReader.getRequiredInt(element, "width");
    final int height = LwXmlReader.getRequiredInt(element, "height");
    return new Rectangle(x, y, width, height);
  }
}
