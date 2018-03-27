// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configuration;

import com.intellij.openapi.application.PathMacroFilter;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaRunConfigurationPathMacroFilter extends PathMacroFilter {
  @Override
  public boolean skipPathMacros(@NotNull Attribute attribute) {
    final Element parent = attribute.getParent();

    if (parent.getName().equals("option")) {
      String optionName = parent.getAttributeValue("name");
      if ("MAIN_CLASS_NAME".equals(optionName) || "METHOD_NAME".equals(optionName)) {
        return true;
      }
    }

    return false;
  }

}
