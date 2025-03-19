// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class EditSourceInNewWindowAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    FileEditorManager manager = FileEditorManager.getInstance(project);
    ((FileEditorManagerImpl)manager).openFileInNewWindow(getVirtualFiles(e)[0]);
  }

  private static VirtualFile[] getVirtualFiles(@NotNull AnActionEvent e) {
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null) return Arrays.stream(files).filter(file -> !file.isDirectory()).toArray(VirtualFile[]::new);

    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    return file == null || file.isDirectory() ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{file};
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getEventProject(e) != null && getVirtualFiles(e).length == 1);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
