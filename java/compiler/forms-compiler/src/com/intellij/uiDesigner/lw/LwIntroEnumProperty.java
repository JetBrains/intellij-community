// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import java.lang.reflect.Method;

public final class LwIntroEnumProperty extends LwIntrospectedProperty {
  private final Class<?> myEnumClass;

  public LwIntroEnumProperty(final String name, final Class enumClass) {
    super(name, enumClass.getName());
    myEnumClass = enumClass;
  }

  @Override
  public Object read(Element element) throws Exception {
    String value = element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_VALUE);
    final Method method = myEnumClass.getMethod("valueOf", String.class);
    return method.invoke(null, value);
  }

  @Override
  public String getCodeGenPropertyClassName() {
    return "java.lang.Enum";
  }
}
