// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content;

import com.intellij.openapi.util.NlsActions.ActionText;
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

  @ActionText
  @NotNull
  String getCloseActionName();

  @ActionText
  @NotNull
  String getCloseAllButThisActionName();

  @ActionText
  @NotNull
  String getPreviousContentActionName();

  @ActionText
  @NotNull
  String getNextContentActionName();
}
