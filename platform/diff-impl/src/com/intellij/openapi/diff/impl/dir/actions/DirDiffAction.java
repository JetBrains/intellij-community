// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public abstract class DirDiffAction extends ToggleAction implements ShortcutProvider, DumbAware {
  private final DirDiffTableModel myModel;

  protected DirDiffAction(DirDiffTableModel model) {
    myModel = model;
  }

  public DirDiffTableModel getModel() {
    return myModel;
  }

  protected abstract void updateState(boolean state);

  @Override
  public final void setSelected(@NotNull AnActionEvent e, boolean state) {
    updateState(state);
    if (isReloadNeeded()) {
      if (isFullReload()) {
        getModel().reloadModel(true);
      } else {
        if (state) {
          getModel().applySettings();
        } else {
          getModel().applyRemove();
        }
      }
    }
    getModel().updateFromUI();
  }

  protected boolean isFullReload() {
    return false;
  }

  protected boolean isReloadNeeded() {
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(!getModel().isUpdating());
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    return getShortcutSet();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
