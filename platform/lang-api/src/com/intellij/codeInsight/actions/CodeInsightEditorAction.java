// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public final class CodeInsightEditorAction {

  /**
   * Commit all PSI if there is editor and project in data context. Should be used in
   * {@link com.intellij.openapi.actionSystem.AnAction#beforeActionPerformedUpdate(AnActionEvent)} implementations before calling super,
   * if the action's {@code update} method should work with up-to-date PSI, and the action is invoked in editor.
   */
  public static void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Editor hostEditor = e.getData(CommonDataKeys.HOST_EDITOR);
    if (project != null && hostEditor != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getCachedPsiFile(hostEditor.getDocument());
      if (file != null) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      }
    }
  }
}
