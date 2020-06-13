// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class UndoUtil {
  private UndoUtil() {
  }

  /**
   * make undoable action in current document in order to Undo action work from current file
   *
   * @param file to make editors of to respond to undo action.
   */
  public static void markPsiFileForUndo(@NotNull final PsiFile file) {
    Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;
    CommandProcessor.getInstance().addAffectedDocuments(project, document);
  }

  public static void disableUndoFor(@NotNull Document document) {
    document.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
  }

  public static boolean isUndoDisabledFor(@NotNull Document document) {
    return Boolean.TRUE.equals(document.getUserData(UndoConstants.DONT_RECORD_UNDO));
  }
}
