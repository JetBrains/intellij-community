// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.actions;

import com.intellij.ide.actions.NonTrivialActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class LocalHistoryGroup extends NonTrivialActionGroup implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
    if (project == null ||
        ActionPlaces.isPopupPlace(e.getPlace()) && (
          file != null && !file.isInLocalFileSystem() || file == null && element != null)) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      super.update(e);
    }
  }
}

