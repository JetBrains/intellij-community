// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.actions;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.FileChooserAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

final class LightEditGotoOpenedFileAction extends FileChooserAction implements LightEditCompatible {
  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    Path file = getSelectedNioPath(e.getProject());
    Path parent = file != null ? file.getParent() : null;
    if (parent != null) {
      panel.load(parent);
    }
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile file = getSelectedFile(fileSystemTree, project);
    if (project != null && file != null) {
      fileSystemTree.select(file, () -> fileSystemTree.expand(file, null));
    }
  }

  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getSelectedNioPath(e.getProject()) != null);
  }

  @Override
  protected void update(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getSelectedFile(fileSystemTree, e.getProject()) != null);
  }

  private static @Nullable Path getSelectedNioPath(@Nullable Project project) {
    VirtualFile file = getSelectedFile(project);
    return file != null ? file.toNioPath() : null;
  }

  private static @Nullable VirtualFile getSelectedFile(@NotNull FileSystemTree fileSystemTree, @Nullable Project project) {
    VirtualFile file = getSelectedFile(project);
    return file != null && file.getParent() != null && fileSystemTree.isUnderRoots(file) ? file : null;
  }

  private static @Nullable VirtualFile getSelectedFile(@Nullable Project project) {
    if (LightEdit.owns(project)) {
      return ArrayUtil.getFirstElement(FileEditorManager.getInstance(project).getSelectedFiles());
    }
    return null;
  }
}
