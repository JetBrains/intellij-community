// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import com.intellij.dvcs.push.ui.PushTargetEditorListener;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class PushTargetPanel<T extends PushTarget> extends JPanel {

  /**
   * @param isActive true if appropriate repository changes will be pushed, a.e. if repository checked
   */
  public abstract void render(@NotNull ColoredTreeCellRenderer renderer,
                              boolean isSelected,
                              boolean isActive,
                              @Nullable @Nls String forceRenderedText);

  public abstract @Nullable T getValue();

  public void editingStarted() { }

  public abstract void fireOnCancel();

  public abstract void fireOnChange();

  public abstract @Nullable ValidationInfo verify();

  public abstract void setFireOnChangeAction(@NotNull Runnable action);

  /**
   * Add an ability to track edit field process
   */
  public abstract void addTargetEditorListener(@NotNull PushTargetEditorListener listener);

  public void forceUpdateEditableUiModel(@NotNull String forcedText) {
  }

  public boolean showSourceWhenEditing() {
    return true;
  }
}
