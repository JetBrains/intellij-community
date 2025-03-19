// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class WarnOnDeletion extends ToggleAction implements DumbAware {
  private static final @NonNls String PROPERTY_NAME = "dir.diff.do.not.show.warnings.when.deleting";

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isWarnWhenDeleteItems();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    setWarnWhenDeleteItems(state);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final DirDiffTableModel model = SetOperationToBase.getModel(e);
    e.getPresentation().setEnabled(model != null && model.isOperationsEnabled());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  public static boolean isWarnWhenDeleteItems() {
    return PropertiesComponent.getInstance().getBoolean(PROPERTY_NAME);
  }

  public static void setWarnWhenDeleteItems(boolean warn) {
    PropertiesComponent.getInstance().setValue(PROPERTY_NAME, warn);
  }
}
