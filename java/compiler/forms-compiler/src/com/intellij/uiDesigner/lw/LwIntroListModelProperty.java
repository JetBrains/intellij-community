// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import java.util.List;

public class LwIntroListModelProperty extends LwIntrospectedProperty {
  public LwIntroListModelProperty(final String name, final String propertyClassName) {
    super(name, propertyClassName);
  }

  @Override
  public Object read(Element element) throws Exception {
    final List list = element.getChildren(UIFormXmlConstants.ELEMENT_ITEM, element.getNamespace());
    String[] result = new String[list.size()];
    for(int i=0; i<list.size(); i++) {
      Element itemElement = (Element) list.get(i);
      result [i] = LwXmlReader.getRequiredString(itemElement, UIFormXmlConstants.ATTRIBUTE_VALUE);
    }
    return result;
  }
}
