// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Use {@link MultiCaretCodeInsightAction} for supporting multiple carets.
 *
 * @author Dmitry Avdeev
 */
public abstract class CodeInsightAction extends AnAction implements UpdateInBackground, PerformWithDocumentsCommitted {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      Editor editor = getEditor(e.getDataContext(), project, false);
      actionPerformedImpl(project, editor);
    }
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
        !(EditorModificationUtil.checkModificationAllowed(editor) &&
          FileModificationService.getInstance().preparePsiElementsForWrite(elementToMakeWritable))) {
      return;
    }
    if (elementToMakeWritable instanceof PsiCompiledElement) {
      return;
    }

    CommandProcessor.getInstance().executeCommand(project, () -> {
      final Runnable action = () -> {
        if (!UIUtil.isShowing(editor.getContentComponent())) return;
        handler.invoke(project, editor, psiFile);
      };
      if (handler.startInWriteAction()) {
        ApplicationManager.getApplication().runWriteAction(action);
      }
      else {
        action.run();
      }
    }, getCommandName(), DocCommandGroupId.noneGroupId(editor.getDocument()), editor.getDocument());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    final DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext, project, true);
    if (editor == null) {
      presentation.setVisible(!ActionPlaces.isPopupPlace(e.getPlace()));
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

  protected @NlsContexts.Command String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text == null ? "" : text;
  }
}
