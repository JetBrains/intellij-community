// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public final class LwIntroFontProperty extends LwIntrospectedProperty {
  LwIntroFontProperty(final String name) {
    super(name, "java.awt.Font");
  }

  @Override
  public Object read(Element element) throws Exception {
    return LwXmlReader.getFontDescriptor(element);
  }
}
