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

/**
 * Sometimes we need to know if we can silently change the file, without user's explicit permission.
 * By convention, permission is required for:<pre>
 * - never touched files,
 * - files under explicit write permission version control (such as Perforce, which asks "do you want to edit this file"),
 * - files in the middle of cut-n-paste operation.
 * </pre>
 * <p/>
 * To determine this, we need to compute several things, in two stages.
 * Some things require EDT for computation, e.g. {@link CanISilentlyChange#thisFile(PsiFileSystemItem)}, to query this file "undo" status.
 * Some things, on the other hand, are quite expensive to compute in EDT and thus require BGT, e.g. {@link SilentChangeVetoer#extensionsAllowToChangeFileSilently(Project, VirtualFile)}
 * The complete algorithm is the following:<pre>
 * (in BGT) {@code val extensionsAllowToChangeFileSilently = SilentChangeVetoer.extensionsAllowToChangeFileSilently(project, virtualFile);}
 * (in BGT) {@code val isFileInContent = ModuleUtilCore.projectContainsFile(project, virtualFile, false);}
 * (in EDT) {@code val result = CanISilentlyChange.thisFile(psiFile);}
 * (in any thread) {@code boolean canSilentlyChange = result.canIReally(isFileInContent, extensionsAllowToChangeFileSilently);}
 * </pre>
 */
final class CanISilentlyChange {
  private static boolean canUndo(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    // can call from any thread
    boolean canIReally(boolean isInContent, @NotNull ThreeState extensionsAllowToChangeFileSilently) {
      return switch (this) {
        case UH_HUH -> extensionsAllowToChangeFileSilently != ThreeState.NO;
        case UH_UH -> false;
        case ONLY_WHEN_IN_CONTENT -> extensionsAllowToChangeFileSilently != ThreeState.NO && isInContent;
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
    return canUndo(virtualFile, project) ? Result.ONLY_WHEN_IN_CONTENT : Result.UH_UH;
  }
}
