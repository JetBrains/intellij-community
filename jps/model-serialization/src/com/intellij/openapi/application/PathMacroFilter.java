// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to disable expansion of path macros in the values of certain properties.
 *
 * @author yole
 */
public abstract class PathMacroFilter {
  public boolean skipPathMacros(@NotNull Element element) {
    return false;
  }

  public boolean skipPathMacros(Text element) {
    return false;
  }

  public boolean skipPathMacros(@NotNull Attribute attribute) {
    return false;
  }

  public boolean recursePathMacros(Text element) {
    return false;
  }

  public boolean recursePathMacros(Attribute attribute) {
    return false;
  }
}
