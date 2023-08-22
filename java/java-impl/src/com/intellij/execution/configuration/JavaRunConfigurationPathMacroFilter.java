// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configuration;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.util.xmlb.Constants;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

final class JavaRunConfigurationPathMacroFilter extends PathMacroFilter {
  @Override
  public boolean skipPathMacros(@NotNull Attribute attribute) {
    Element parent = attribute.getParent();
    if (parent.getName().equals(Constants.OPTION)) {
      String optionName = parent.getAttributeValue(Constants.NAME);
      if ("MAIN_CLASS_NAME".equals(optionName) || "METHOD_NAME".equals(optionName) || "TEST_OBJECT".equals(optionName)) {
        return true;
      }
    }
    return false;
  }
}
