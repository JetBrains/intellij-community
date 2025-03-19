// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class TogglePopupHintsAction extends AnAction implements ActionRemoteBehaviorSpecification.Frontend {
  private static final Logger LOG = Logger.getInstance(TogglePopupHintsAction.class);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    PsiFile psiFile = getTargetFile(e.getDataContext());
    e.getPresentation().setEnabled(psiFile != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PsiFile psiFile = getTargetFile(e.getDataContext());
    LOG.assertTrue(psiFile != null);
    Project project = e.getProject();
    LOG.assertTrue(project != null);
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    codeAnalyzer.setImportHintsEnabled(psiFile, !codeAnalyzer.isImportHintsEnabled(psiFile));
  }

  private static PsiFile getTargetFile(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
    if (files.length == 0) return null;
    return PsiManager.getInstance(project).findFile(files[0]);
  }
}
