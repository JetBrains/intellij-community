// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configuration;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.util.ArrayUtil;
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
    //noinspection IO_FILE_USAGE,UnnecessaryFullyQualifiedName
    return name != null && value != null && ArrayUtil.find(value.split(java.io.File.pathSeparator), "$" + name + "$") != -1;
  }

  @Override
  public boolean recursePathMacros(@NotNull Attribute attribute) {
    var parent = attribute.getParent();
    if (parent != null && Constants.OPTION.equals(parent.getName())) {
      var grandParent = parent.getParentElement();
      return grandParent != null && "configuration".equals(grandParent.getName());
    }
    return false;
  }
}
