// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchSession;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;

public final class RestorePreviousSettingsAction extends AnAction implements ShortcutProvider, DumbAware, LightEditCompatible {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    EditorSearchSession search = e.getData(EditorSearchSession.SESSION_KEY);
    e.getPresentation().setEnabled(project != null && search != null && !project.isDisposed() &&
                                   search.getTextInField().isEmpty() &&
                                   FindManager.getInstance(project).getPreviousFindModel() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    EditorSearchSession session = e.getData(EditorSearchSession.SESSION_KEY);
    if (session == null) return;
    FindModel findModel = session.getFindModel();
    findModel.copyFrom(FindManager.getInstance(project).getPreviousFindModel());
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    return new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
  }
}
