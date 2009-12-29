/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.highlighting.actions;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class HighlightUsagesAction extends AnAction implements DumbAware {

  public HighlightUsagesAction() {
    setInjectedContext(true);
  }

  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();

    presentation.setEnabled(PlatformDataKeys.PROJECT.getData(dataContext) != null &&
                            PlatformDataKeys.EDITOR.getData(dataContext) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (editor == null || project == null) return;
    String commandName = getTemplatePresentation().getText();
    if (commandName == null) commandName = "";

    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
          try {
            HighlightUsagesHandler.invoke(project, editor, psiFile);
          }
          catch (IndexNotReadyException e1) {
            DumbService.getInstance(project).showDumbModeNotification("This usage search requires indices and cannot be performed until they are built");
          }
        }
      },
      commandName,
      null
    );
  }
}
