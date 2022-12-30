// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

class CanISilentlyChange {
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

  enum Result {
    UH_HUH, UH_UH, ONLY_WHEN_IN_CONTENT;
    boolean canIReally(boolean isInContent) {
      return switch (this) {
        case UH_HUH -> true;
        case UH_UH -> false;
        case ONLY_WHEN_IN_CONTENT -> isInContent;
      };
    }
  }

  @NotNull
  static Result thisFile(@NotNull PsiFileSystemItem file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Project project = file.getProject();
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return Result.UH_UH;
    }
    if (file instanceof PsiCodeFragment) {
      return Result.UH_HUH;
    }
    if (ScratchUtil.isScratch(virtualFile)) {
      return canUndo(virtualFile, project) ? Result.UH_HUH : Result.UH_UH;
    }
    for (SilentChangeVetoer extension : SilentChangeVetoer.EP_NAME.getExtensionList()) {
      ThreeState result = extension.canChangeFileSilently(project, virtualFile);
      if (result == ThreeState.YES) {
        return Result.ONLY_WHEN_IN_CONTENT;
      }
      if (result == ThreeState.NO) {
        return Result.UH_UH;
      }
    }

    return canUndo(virtualFile, project) ? Result.ONLY_WHEN_IN_CONTENT : Result.UH_UH;
  }
}
