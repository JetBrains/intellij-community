// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.util.NlsContexts.ListItem;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;

public interface CellAppearanceEx {
  @NotNull @ListItem String getText();

  /** @deprecated don't implement this method - it's never called */
  @Deprecated(forRemoval = true)
  default void customize(@SuppressWarnings("ALL") com.intellij.ui.HtmlListCellRenderer renderer) { }

  void customize(@NotNull SimpleColoredComponent component);
}