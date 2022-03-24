// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public class CanISilentlyChange {
  private static boolean canUndo(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(virtualFile);
    if (editors.length == 0) {
      return false;
    }

    UndoManager undoManager = UndoManager.getInstance(project);
    for (FileEditor editor : editors) {
      if (undoManager.isUndoAvailable(editor)) {
        return true;
      }
    }
    return false;
  }

  public static boolean thisFile(@NotNull PsiFileSystemItem file) {
    Project project = file.getProject();
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    if (file instanceof PsiCodeFragment) {
      return true;
    }
    if (ScratchUtil.isScratch(virtualFile)) {
      return canUndo(virtualFile, project);
    }
    if (!ModuleUtilCore.projectContainsFile(project, virtualFile, false)) {
      return false;
    }

    for (SilentChangeVetoer extension : SilentChangeVetoer.EP_NAME.getExtensionList()) {
      ThreeState result = extension.canChangeFileSilently(project, virtualFile);
      if (result != ThreeState.UNSURE) {
        return result.toBoolean();
      }
    }

    return canUndo(virtualFile, project);
  }
}
