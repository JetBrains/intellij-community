/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
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
