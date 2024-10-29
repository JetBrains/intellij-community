// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.NlsContexts.HintText;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class SelectionBasedPsiElementInternalAction<T extends PsiElement> extends AnAction {
  protected final @NotNull Class<T> myClass;
  protected final @NotNull Class<? extends PsiFile> myFileClass;

  protected SelectionBasedPsiElementInternalAction(@NotNull Class<T> aClass, @NotNull Class<? extends PsiFile> fileClass) {
    myClass = aClass;
    myFileClass = fileClass;
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    final Editor editor = getEditor(e);
    final PsiFile file = getPsiFile(e);
    if (editor == null || file == null) return;

    final List<T> expressions = getElement(editor, file);
    T first = ContainerUtil.getFirstItem(expressions);

    if (expressions.size() > 1) {
      IntroduceTargetChooser.showChooser(
        editor, expressions,
        new Pass<>() {
          @Override
          public void pass(@NotNull T expression) {
            performOnElement(editor, expression);
          }
        },
        expression -> expression.getText()
      );
    }
    else if (expressions.size() == 1 && first != null) {
      performOnElement(editor, first);
    }
    else if (expressions.isEmpty()) {
      showError(editor);
    }
  }

  protected void showError(final @NotNull Editor editor) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final String errorHint = "Cannot find element of class " + myClass.getSimpleName() + " at selection/offset";
      HintManager.getInstance().showErrorHint(editor, errorHint);
    });
  }

  private void performOnElement(final @NotNull Editor editor, @NotNull T first) {
    final TextRange textRange = first.getTextRange();
    editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());
    final String informationHint = getInformationHint(first);
    if (informationHint != null) {
      ApplicationManager.getApplication().invokeLater(() -> HintManager.getInstance().showInformationHint(editor, informationHint));
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> HintManager.getInstance().showErrorHint(editor, getErrorHint()));
    }
  }

  protected abstract @Nullable String getInformationHint(@NotNull T element);

  protected abstract @HintText @NotNull String getErrorHint();

  protected @NotNull List<T> getElement(@NotNull Editor editor, @NotNull PsiFile file) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      return Collections.singletonList(getElementFromSelection(file, selectionModel));
    }
    return getElementAtOffset(editor, file);
  }

  protected @NotNull List<T> getElementAtOffset(@NotNull Editor editor, @NotNull PsiFile file) {
    return Collections.singletonList(PsiTreeUtil.findElementOfClassAtOffset(file, editor.getCaretModel().getOffset(), myClass, false));
  }

  protected @Nullable T getElementFromSelection(@NotNull PsiFile file, @NotNull SelectionModel selectionModel) {
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    return PsiTreeUtil.findElementOfClassAtRange(file, selectionStart, selectionEnd, myClass);
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean enabled = ApplicationManager.getApplication().isInternal() && getEditor(e) != null && myFileClass.isInstance(getPsiFile(e));
    presentation.setEnabledAndVisible(enabled);
  }

  private static @Nullable Editor getEditor(@NotNull AnActionEvent e) {
    return e.getData(CommonDataKeys.EDITOR);
  }

  private static @Nullable PsiFile getPsiFile(@NotNull AnActionEvent e) {
    return e.getData(CommonDataKeys.PSI_FILE);
  }
}
