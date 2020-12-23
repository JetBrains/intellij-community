// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.FileChooserAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LightEditGotoOpenedFileAction extends FileChooserAction implements LightEditCompatible {

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile file = getSelectedFile(fileSystemTree, project);
    if (project != null && file != null) {
      fileSystemTree.select(file, () -> fileSystemTree.expand(file, null));
    }
  }

  @Override
  protected void update(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (LightEdit.owns(project)) {
      e.getPresentation().setEnabled(getSelectedFile(fileSystemTree, project) != null);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  private static @Nullable VirtualFile getSelectedFile(@NotNull FileSystemTree fileSystemTree, @Nullable Project project) {
    if (LightEdit.owns(project)) {
      VirtualFile file = ArrayUtil.getFirstElement(FileEditorManager.getInstance(project).getSelectedFiles());
      return file != null && file.getParent() != null && fileSystemTree.isUnderRoots(file) ? file : null;
    }
    return null;
  }
}
