// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.search.actions;

import com.intellij.largeFilesEditor.search.LfeSearchManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ComponentNotRegistered")
public class LargeFileToggleAction extends CheckboxAction implements DumbAware {
  private final LfeSearchManager searchManager;

  private boolean isSelected;

  public LargeFileToggleAction(LfeSearchManager searchManager, @NlsContexts.Checkbox String name) {
    super(name);
    this.searchManager = searchManager;
  }

  public boolean isSelected() {
    return isSelected;
  }

  public void setSelected(boolean selected) {
    if (isSelected != selected) {
      isSelected = selected;
      searchManager.onSearchParametersChanged();
    }
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isSelected;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    setSelected(state);
  }
}