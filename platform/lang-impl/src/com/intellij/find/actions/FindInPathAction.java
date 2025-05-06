// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class FindInPathAction extends AnAction implements DumbAware {

  public static final NotificationGroup NOTIFICATION_GROUP = Cancellation.forceNonCancellableSectionInClassInitializer(
    () -> NotificationGroupManager.getInstance().getNotificationGroup("Find in Path")
  );

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    FindInProjectManager findManager = FindInProjectManager.getInstance(project);
    findManager.findInProject(dataContext, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e){
    doUpdate(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  static void doUpdate(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    presentation.setEnabled(project != null && !LightEdit.owns(project));
    if (e.isFromContextMenu() && !ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION.equals(e.getPlace())) {
      presentation.setVisible(isValidSearchScope(e));
    }
  }

  private static boolean isValidSearchScope(@NotNull AnActionEvent e) {
    final PsiElement[] elements = e.getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY);
    if (elements != null && elements.length == 1 && elements[0] instanceof PsiDirectoryContainer) {
      return true;
    }
    final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles == null) {
      return false;
    }
    return virtualFiles.length > 1 || virtualFiles.length == 1 && virtualFiles[0].isDirectory();
  }
}
