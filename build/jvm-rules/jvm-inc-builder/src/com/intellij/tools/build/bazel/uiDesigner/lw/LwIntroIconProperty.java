// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.uiDesigner.lw;

import com.intellij.tools.build.bazel.org.jdom.Element;
import com.intellij.tools.build.bazel.uiDesigner.UIFormXmlConstants;

public final class LwIntroIconProperty extends LwIntrospectedProperty {
  LwIntroIconProperty(final String name) {
    super(name, "javax.swing.Icon");
  }

  @Override
  public Object read(Element element) throws Exception {
    String value = LwXmlReader.getRequiredString(element, UIFormXmlConstants.ATTRIBUTE_VALUE);
    return new IconDescriptor(value);
  }
}
