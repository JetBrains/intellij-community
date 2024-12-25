// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* @author Konstantin Bulenkov
*/
@ApiStatus.Internal
class ChangeCompareModeAction extends DumbAwareAction {
  private static final Icon ON = PlatformIcons.CHECK_ICON;
  private static final Icon ON_SELECTED = PlatformIcons.CHECK_ICON_SELECTED;
  private static final Icon OFF = EmptyIcon.create(ON.getIconHeight());

  private final DirDiffTableModel myModel;
  private final DirDiffSettings.CompareMode myMode;

  ChangeCompareModeAction(DirDiffTableModel model, DirDiffSettings.CompareMode mode) {
    super(mode.getPresentableName());
    getTemplatePresentation().setIcon(OFF);
    myModel = model;
    myMode = mode;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myModel.setCompareMode(myMode);
    myModel.reloadModel(false);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final boolean on = myModel.getCompareMode() == myMode;
    e.getPresentation().setIcon(on ? ON : OFF);
    e.getPresentation().setSelectedIcon(on ? ON_SELECTED : OFF);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
