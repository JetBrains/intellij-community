// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public final class LwIntroInsetsProperty extends LwIntrospectedProperty{
  LwIntroInsetsProperty(final String name){
    super(name, "java.awt.Insets");
  }

  @Override
  public Object read(final Element element) throws Exception{
    return LwXmlReader.readInsets(element);
  }
}
