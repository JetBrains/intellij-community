// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui.actions;

import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog;
import com.intellij.history.integration.ui.views.FileHistoryDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.impl.ActivityScope;
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter;
import com.intellij.platform.lvcs.impl.ui.ActivityView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public class ShowHistoryAction extends LocalHistoryAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      IdeaGateway gateway = getGateway();
      Collection<VirtualFile> files = getFiles(e);
      presentation.setEnabled(gateway != null && isActionEnabled(gateway, files));
    }
  }

  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    Collection<VirtualFile> selectedFiles = getFiles(e);
    List<VirtualFile> enabledFiles = ContainerUtil.filter(selectedFiles, file -> isFileVersioned(gw, file));
    if (enabledFiles.isEmpty()) return;

    if (ActivityView.isViewEnabled()) {
      ActivityView.showInDialog(p, gw, ActivityScope.fromFiles(enabledFiles));
      return;
    }

    VirtualFile singleFile = ContainerUtil.getOnlyItem(enabledFiles);
    if (singleFile == null) return;

    if (singleFile.isDirectory()) {
      LocalHistoryCounter.INSTANCE.logLocalHistoryOpened(LocalHistoryCounter.Kind.Directory);
      new DirectoryHistoryDialog(p, gw, singleFile).show();
    }
    else {
      LocalHistoryCounter.INSTANCE.logLocalHistoryOpened(LocalHistoryCounter.Kind.File);
      new FileHistoryDialog(p, gw, singleFile).show();
    }
  }

  protected boolean isActionEnabled(@NotNull IdeaGateway gw, @NotNull Collection<VirtualFile> files) {
    if (files.size() > 1 && !ActivityView.isViewEnabled()) return false;
    return ContainerUtil.exists(files, file -> {
      return isFileVersioned(gw, file);
    });
  }

  protected static boolean isFileVersioned(@NotNull IdeaGateway gw, @NotNull VirtualFile file) {
    return gw.isVersioned(file) && (file.isDirectory() || gw.areContentChangesVersioned(file));
  }

  protected @NotNull Collection<VirtualFile> getFiles(@NotNull AnActionEvent e) {
    return JBIterable.from(e.getData(VcsDataKeys.VIRTUAL_FILES)).toSet();
  }
}
