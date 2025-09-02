// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Use {@link MultiCaretCodeInsightAction} for supporting multiple carets.
 *
 * @author Dmitry Avdeev
 */
public abstract class CodeInsightAction extends AnAction implements PerformWithDocumentsCommitted {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      Editor editor = getEditor(e.getDataContext(), project, false);
      actionPerformedImpl(project, editor, e.getDataContext());
    }
  }

  protected @Nullable Editor getEditor(@NotNull DataContext dataContext, @NotNull Project project, boolean forUpdate) {
    return CommonDataKeys.EDITOR.getData(dataContext);
  }

  public void actionPerformedImpl(final @NotNull Project project, final Editor editor) {
    actionPerformedImpl(project, editor, DataContext.EMPTY_CONTEXT);
  }

  private void actionPerformedImpl(final @NotNull Project project, final Editor editor, @NotNull DataContext dataContext) {
    if (editor == null) return;
    //final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;
    final CodeInsightActionHandler handler = getHandler(dataContext);

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
      presentation.setVisible(!e.isFromContextMenu());
      presentation.setEnabled(false);
      return;
    }

    final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) {
      presentation.setEnabled(false);
      return;
    }

    update(presentation, project, editor, psiFile, dataContext, e.getPlace());
  }

  protected void update(@NotNull Presentation presentation, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    presentation.setEnabled(isValidForFile(project, editor, psiFile));
  }

  protected void update(@NotNull Presentation presentation, @NotNull Project project,
                        @NotNull Editor editor, @NotNull PsiFile psiFile, @NotNull DataContext dataContext, @Nullable String actionPlace) {
    update(presentation, project, editor, psiFile);
  }

  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return true;
  }

  /**
   * Use {@link CodeInsightAction#getHandler(DataContext)}
   */
  @ApiStatus.Obsolete
  protected abstract @NotNull CodeInsightActionHandler getHandler();

  protected @NotNull CodeInsightActionHandler getHandler(@NotNull DataContext dataContext) {
    return getHandler();
  }

  protected @NlsContexts.Command String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text == null ? "" : text;
  }
}
