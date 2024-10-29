// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AssociateFileTypeAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null) return;
    FileTypeChooser.associateFileType(file.getName());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    Project project = e.getProject();
    boolean haveSmthToDo;
    if (project == null || file == null || file.isDirectory()) {
      haveSmthToDo = false;
    }
    else {
      // the action should also be available for files which have been auto-detected as text or as a particular language (IDEA-79574)
      haveSmthToDo = FileTypeManager.getInstance().getFileTypeByFileName(file.getNameSequence()) == FileTypes.UNKNOWN &&
                     !(file.getFileSystem() instanceof NonPhysicalFileSystem) &&
                     !ScratchRootType.getInstance().containsFile(file);
      haveSmthToDo |= ActionPlaces.isMainMenuOrActionSearch(e.getPlace());
    }
    presentation.setVisible(haveSmthToDo || ActionPlaces.isMainMenuOrActionSearch(e.getPlace()));
    presentation.setEnabled(haveSmthToDo);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
