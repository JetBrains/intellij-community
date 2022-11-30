// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be a string or enum
 * @param splitLabel label to display around the control
 * @param options drop-down options
 */
public record OptDropdown(@NotNull String bindId, @NotNull LocMessage splitLabel, @NotNull List<@NotNull Option> options) implements OptControl {
  /**
   * Drop down option
   * 
   * @param key key to assign to a variable (either enum constant name, or string content)
   * @param label label for a given option
   */
  public record Option(@NotNull String key, @NotNull LocMessage label) {}
}
