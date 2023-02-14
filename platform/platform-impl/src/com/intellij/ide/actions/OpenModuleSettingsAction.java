// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class OpenModuleSettingsAction extends EditSourceAction {
  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    if (!isModuleInProjectViewPopup(event)) {
      event.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return super.getActionUpdateThread();
  }

  protected static boolean isModuleInProjectViewPopup(@NotNull AnActionEvent e) {
    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(e.getPlace())) {
      return isModuleInContext(e);
    }
    return false;
  }

  public static boolean isModuleInContext(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    final Module module = e.getData(PlatformCoreDataKeys.MODULE);
    if (project != null && module != null) {
      final VirtualFile moduleFolder = e.getData(CommonDataKeys.VIRTUAL_FILE);
      if (moduleFolder == null) {
        return false;
      }
      if (ProjectRootsUtil.isModuleContentRoot(moduleFolder, project) || ProjectRootsUtil.isModuleSourceRoot(moduleFolder, project)) {
        return true;
      }
    }
    return false;
  }
}