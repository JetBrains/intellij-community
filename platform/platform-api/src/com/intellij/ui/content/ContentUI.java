// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface ContentUI {
  JComponent getComponent();

  void setManager(@NotNull ContentManager manager);

  boolean isSingleSelection();

  boolean isToSelectAddedContent();

  boolean canBeEmptySelection();

  default void beforeDispose() {
  }

  boolean canChangeSelectionTo(@NotNull Content content, boolean implicit);

  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  String getCloseActionName();

  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  String getCloseAllButThisActionName();

  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  String getPreviousContentActionName();

  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  String getNextContentActionName();
}
