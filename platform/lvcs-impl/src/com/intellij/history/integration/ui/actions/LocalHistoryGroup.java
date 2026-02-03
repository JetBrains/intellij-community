// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.actions;

import com.intellij.ide.actions.NonTrivialActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VersionManagingFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LocalHistoryGroup extends NonTrivialActionGroup implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (e.isFromContextMenu()) {
      VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
      if (file == null || !(file.isInLocalFileSystem() || VersionManagingFileSystem.isEnforcedNonLocal(file))) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
    }

    super.update(e);
  }
}
