// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

public final class LwIntroEnumProperty extends LwIntrospectedProperty {
  public LwIntroEnumProperty(final String name, final String enumClassName) {
    super(name, enumClassName);
  }

  /**
   * @return an {@link EnumDescriptor} rather than an {@code Enum}: the enum class is resolved by whoever applies
   * the value, which is the only place that knows the right class loader.
   */
  @Override
  public EnumDescriptor read(Element element) {
    String value = element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_VALUE);
    return new EnumDescriptor(getPropertyClassName(), value);
  }

  @Override
  public String getCodeGenPropertyClassName() {
    return "java.lang.Enum";
  }
}
