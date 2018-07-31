// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.openapi.application.PathMacroFilter;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class JavacConfigurationMacroFilter extends PathMacroFilter {
  @Override
  public boolean recursePathMacros(@NotNull Attribute attribute) {
    if (attribute.getName().equals("value")) {
      Element parent = attribute.getParent();
      if (parent != null && "option".equals(parent.getName()) && "ADDITIONAL_OPTIONS_STRING".equals(parent.getAttributeValue("name"))) {
        Element grandParent = parent.getParentElement();
        return grandParent != null && grandParent.getName().equals("component")
               && "JavacSettings".equals(grandParent.getAttributeValue("name"));
      }
    }
    return false;
  }
}
