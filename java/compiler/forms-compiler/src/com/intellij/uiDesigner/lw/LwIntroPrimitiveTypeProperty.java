// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

public final class LwIntroPrimitiveTypeProperty extends LwIntrospectedProperty {
  private final Class<?> myValueClass;

  LwIntroPrimitiveTypeProperty(final String name, final Class<?> valueClass){
    super(name, valueClass.getName());
    myValueClass = valueClass;
  }

  @Override
  public Object read(final Element element) throws Exception{
    return LwXmlReader.getRequiredPrimitiveTypeValue(element, UIFormXmlConstants.ATTRIBUTE_VALUE, myValueClass);
  }
}