// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

public final class LwRbIntroStringProperty extends LwIntrospectedProperty {
  LwRbIntroStringProperty(final String name){
    super(name, String.class.getName());
  }

  /**
   * @return instance of {@link StringDescriptor}
   */
  @Override
  public Object read(final Element element) {
    final StringDescriptor descriptor = LwXmlReader.getStringDescriptor(element,
                                                                        UIFormXmlConstants.ATTRIBUTE_VALUE,
                                                                        UIFormXmlConstants.ATTRIBUTE_RESOURCE_BUNDLE,
                                                                        UIFormXmlConstants.ATTRIBUTE_KEY);
    if (descriptor == null) {
      throw new IllegalArgumentException("String descriptor value required");
    }
    return descriptor;
  }
}

