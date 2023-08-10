// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Basic interface for all the components that could be displayed in options panel
 */
public sealed interface OptComponent permits OptControl, OptRegularComponent, OptTab {
  /**
   * @return list of all components that are nested inside this component
   */
  default @NotNull List<? extends @NotNull OptComponent> children() {
    return List.of();
  }

  /**
   * @param bindPrefix prefix to add to bindId values
   * @return an equivalent component but every control has bindId prefixed with bindPrefix and dot.
   * Could be useful to compose a complex form from independent parts. To process prefixed options,
   * use {@link OptionController#onPrefix(String, OptionController)} 
   */
  default @NotNull OptComponent prefix(@NotNull String bindPrefix) {
    return this;
  }
}
