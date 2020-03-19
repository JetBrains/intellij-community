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

public final class LightEditGotoOpenedFileAction extends FileChooserAction implements LightEditCompatible {

  @Override
  protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      VirtualFile file = ArrayUtil.getFirstElement(FileEditorManager.getInstance(project).getSelectedFiles());
      if (file != null) {
        fileSystemTree.select(file, () -> fileSystemTree.expand(file, null));
      }
    }
  }

  @Override
  protected void update(FileSystemTree fileChooser, AnActionEvent e) {
    Project project = e.getProject();
    if (!LightEdit.owns(project)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabled(FileEditorManager.getInstance(project).hasOpenFiles());
  }
}
