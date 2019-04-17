// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui;

import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;

public interface CellAppearanceEx {
  @NotNull String getText();

  void customize(@NotNull HtmlListCellRenderer renderer);

  void customize(@NotNull SimpleColoredComponent component);
}