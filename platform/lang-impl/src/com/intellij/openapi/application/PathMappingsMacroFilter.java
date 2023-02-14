// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Filter remote path in {@link com.intellij.util.PathMappingSettings.PathMapping}
 */
final class PathMappingsMacroFilter extends PathMacroFilter {
  @Override
  public boolean skipPathMacros(@NotNull Attribute attribute) {
    final Element parent = attribute.getParent();
    if ("mapping".equals(parent.getName()) && "remote-root".equals(attribute.getName())) {
      return true;
    }
    return false;
  }
}
