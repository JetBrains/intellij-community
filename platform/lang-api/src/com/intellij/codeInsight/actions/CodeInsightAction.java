/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class CodeInsightAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      Editor editor = getEditor(e.getDataContext(), project, false);
      actionPerformedImpl(project, editor);
    }
  }

  @Override
  public boolean startInTransaction() {
    return true;
  }

  @Nullable
  protected Editor getEditor(@NotNull DataContext dataContext, @NotNull Project project, boolean forUpdate) {
    return CommonDataKeys.EDITOR.getData(dataContext);
  }

  public void actionPerformedImpl(@NotNull final Project project, final Editor editor) {
    if (editor == null) return;
    //final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;
    final CodeInsightActionHandler handler = getHandler();
    PsiElement elementToMakeWritable = handler.getElementToMakeWritable(psiFile);
    if (elementToMakeWritable != null &&
        !(EditorModificationUtil.showReadOnlyViewWarning(editor) &&
          FileModificationService.getInstance().preparePsiElementsForWrite(elementToMakeWritable))) {
      return;
    }

    CommandProcessor.getInstance().executeCommand(project, () -> {
      final Runnable action = () -> {
        if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) return;
        handler.invoke(project, editor, psiFile);
      };
      if (handler.startInWriteAction()) {
        ApplicationManager.getApplication().runWriteAction(action);
      }
      else {
        action.run();
      }
    }, getCommandName(), DocCommandGroupId.noneGroupId(editor.getDocument()));
  }

  @Override
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }
    super.beforeActionPerformedUpdate(e);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    final DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext, project, true);
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (file == null) {
      presentation.setEnabled(false);
      return;
    }

    update(presentation, project, editor, file, dataContext, e.getPlace());
  }

  protected void update(@NotNull Presentation presentation, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    presentation.setEnabled(isValidForFile(project, editor, file));
  }

  protected void update(@NotNull Presentation presentation, @NotNull Project project,
                        @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext, @Nullable String actionPlace) {
    update(presentation, project, editor, file);
  }

  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return true;
  }

  @NotNull
  protected abstract CodeInsightActionHandler getHandler();

  protected String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text == null ? "" : text;
  }
}
