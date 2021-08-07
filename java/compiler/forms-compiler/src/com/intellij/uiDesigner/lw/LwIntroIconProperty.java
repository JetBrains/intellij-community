// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

public class LwIntroIconProperty extends LwIntrospectedProperty {
  public LwIntroIconProperty(final String name) {
    super(name, "javax.swing.Icon");
  }

  @Override
  public Object read(Element element) throws Exception {
    String value = LwXmlReader.getRequiredString(element, UIFormXmlConstants.ATTRIBUTE_VALUE);
    return new IconDescriptor(value);
  }
}
