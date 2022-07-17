// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GoToLinkTargetAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GoToLinkTargetAction.class);

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = getEventProject(e);
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    e.getPresentation().setEnabledAndVisible(project != null && underSymlink(file));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = getEventProject(e);
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project != null && underSymlink(file)) {
      VirtualFile target = file.getCanonicalFile();
      PsiFileSystemItem psiFile = PsiUtilCore.findFileSystemItem(project, target);
      if (LOG.isDebugEnabled()) LOG.debug(file + " -> " + target + " (" + psiFile + ")");
      if (psiFile != null) {
        ProjectView.getInstance(project).select(psiFile, target, false);
      }
    }
  }

  private static boolean underSymlink(@Nullable VirtualFile file) {
    return file != null && (file.is(VFileProperty.SYMLINK) || underSymlink(file.getParent()));
  }
}