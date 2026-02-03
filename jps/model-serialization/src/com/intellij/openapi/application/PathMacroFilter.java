// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Allows disabling expansion of path macros in the values of certain properties.
 */
public abstract class PathMacroFilter {
  public boolean skipPathMacros(@NotNull Element element) {
    return false;
  }

  public boolean skipPathMacros(@NotNull Attribute attribute) {
    return false;
  }

  public boolean recursePathMacros(@NotNull Attribute attribute) {
    return false;
  }
}
