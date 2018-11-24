/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class CodeInsightEditorAction {

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
