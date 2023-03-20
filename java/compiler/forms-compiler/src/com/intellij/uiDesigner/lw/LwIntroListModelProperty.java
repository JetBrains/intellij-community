// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import java.util.List;

public final class LwIntroListModelProperty extends LwIntrospectedProperty {
  public LwIntroListModelProperty(final String name, final String propertyClassName) {
    super(name, propertyClassName);
  }

  @Override
  public Object read(Element element) throws Exception {
    final List<Element> list = element.getChildren(UIFormXmlConstants.ELEMENT_ITEM, element.getNamespace());
    String[] result = new String[list.size()];
    for (int i = 0; i < list.size(); i++) {
      Element itemElement = list.get(i);
      result[i] = LwXmlReader.getRequiredString(itemElement, UIFormXmlConstants.ATTRIBUTE_VALUE);
    }
    return result;
  }
}
