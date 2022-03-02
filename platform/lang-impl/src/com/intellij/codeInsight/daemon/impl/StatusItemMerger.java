// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.markup.SeverityStatusItem;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class StatusItemMerger {
  static final ExtensionPointName<StatusItemMerger> EP_NAME = ExtensionPointName.create("com.intellij.daemon.statusItemMerger");

  /**
   * Invoked for adjacent severity-based icons in the code analysis indicator to check whether they should be merged
   * @param higher the item with the higher of the two adjacent severities
   * @param lower the item with the lower of the two adjacent severities
   * @return the merged item, if any
   */
  @Nullable
  public abstract SeverityStatusItem mergeItems(@NotNull SeverityStatusItem higher, @NotNull SeverityStatusItem lower);

  static @Nullable SeverityStatusItem runMerge(@NotNull SeverityStatusItem higher, @NotNull SeverityStatusItem lower) {
    return EP_NAME.extensions().map(e -> e.mergeItems(higher, lower)).filter(Objects::nonNull).findFirst().orElse(null);
  }
}
