// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configuration;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.xmlb.Constants;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

final class RunConfigurationPathMacroFilter extends PathMacroFilter {
  @Override
  public boolean skipPathMacros(@NotNull Attribute attribute) {
    return attribute.getName().equals(Constants.NAME) && attribute.getParent().getName().equals("configuration");
  }

  @Override
  public boolean skipPathMacros(@NotNull Element element) {
    if (!element.getName().equals("env")) {
      return false;
    }

    var name = element.getAttributeValue(Constants.NAME);
    var value = element.getAttributeValue(Constants.VALUE);

    return name != null && value != null && EnvironmentUtil.containsEnvKeySubstitution(name, value);
  }

  @Override
  public boolean recursePathMacros(@NotNull Attribute attribute) {
    final Element parent = attribute.getParent();
    if (parent != null && Constants.OPTION.equals(parent.getName())) {
      final Element grandParent = parent.getParentElement();
      return grandParent != null && "configuration".equals(grandParent.getName());
    }
    return false;
  }
}
