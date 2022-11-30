// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Set of tabs
 *
 * @param tabs tabs description 
 */
public record OptTabSet(@NotNull List<@NotNull TabInfo> tabs) implements OptComponent {

  @Override
  public @NotNull List<@NotNull OptComponent> children() {
    return tabs.stream().flatMap(tab -> tab.content().stream()).toList();
  }

  /**
   * @param label tab label
   * @param content tab content
   */
  public record TabInfo(@NotNull LocMessage label, @NotNull List<@NotNull OptComponent> content) {}
}
