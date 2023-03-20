// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import org.jdom.Element;

import java.awt.*;

public final class LwIntroRectangleProperty extends LwIntrospectedProperty{
  LwIntroRectangleProperty(final String name){
    super(name, "java.awt.Rectangle");
  }

  @Override
  public Object read(final Element element) throws Exception{
    final int x = LwXmlReader.getRequiredInt(element, "x");
    final int y = LwXmlReader.getRequiredInt(element, "y");
    final int width = LwXmlReader.getRequiredInt(element, "width");
    final int height = LwXmlReader.getRequiredInt(element, "height");
    return new Rectangle(x, y, width, height);
  }
}
