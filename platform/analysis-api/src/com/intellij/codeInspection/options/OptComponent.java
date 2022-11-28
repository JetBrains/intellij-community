// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Basic interface for all the components that could be displayed in options panel
 */
public sealed interface OptComponent permits OptControl, OptGroup, OptHorizontalStack, OptSeparator, OptTabSet {
  /**
   * @return list of all components that are nested inside this component
   */
  default @NotNull List<@NotNull OptComponent> children() {
    return List.of();
  }
}
