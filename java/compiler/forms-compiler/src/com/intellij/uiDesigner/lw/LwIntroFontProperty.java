// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public class LwIntroFontProperty extends LwIntrospectedProperty {
  public LwIntroFontProperty(final String name) {
    super(name, "java.awt.Font");
  }

  @Override
  public Object read(Element element) throws Exception {
    return LwXmlReader.getFontDescriptor(element);
  }
}
