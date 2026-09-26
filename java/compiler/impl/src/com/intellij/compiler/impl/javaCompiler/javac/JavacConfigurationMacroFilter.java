// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil;
import com.intellij.util.xmlb.Constants;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

final class JavacConfigurationMacroFilter extends PathMacroFilter {
  @Override
  public boolean recursePathMacros(@NotNull Attribute attribute) {
    if (attribute.getName().equals(Constants.VALUE)) {
      Element parent = attribute.getParent();
      if (parent != null && Constants.OPTION.equals(parent.getName()) && "ADDITIONAL_OPTIONS_STRING".equals(parent.getAttributeValue(Constants.NAME))) {
        Element grandParent = parent.getParentElement();
        return grandParent != null && grandParent.getName().equals(ComponentStorageUtil.COMPONENT)
               && "JavacSettings".equals(grandParent.getAttributeValue(Constants.NAME));
      }
    }
    return false;
  }
}
