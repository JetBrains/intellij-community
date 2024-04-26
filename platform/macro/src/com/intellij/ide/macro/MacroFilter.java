// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Allows hiding macros irrelevant or invalid in a certain IDE-related context
 * (e.g. "JDKPath" macro is useless when the IDE doesn't support Java at all).
 * Register in {@code com.intellij.macroFilter} extension point.
 */
public abstract class MacroFilter {
  public static final ExtensionPointName<MacroFilter> EP_NAME = ExtensionPointName.create("com.intellij.macroFilter");

  public abstract boolean accept(@NotNull Macro macro);

  public static final MacroFilter GLOBAL = new MacroFilter() {
    @Override
    public boolean accept(@NotNull Macro macro) {
      for (MacroFilter filter : EP_NAME.getExtensionList()) {
        if (!filter.accept(macro)) return false;
      }
      return true;
    }
  };
}
