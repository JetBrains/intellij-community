// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.PreviewableRefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntroduceVariableErrorFixAction extends LocalQuickFixAndIntentionActionOnPsiElement {
  public IntroduceVariableErrorFixAction(@NotNull PsiExpression expression) {
    super(expression);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    RefactoringActionHandler handler = LanguageRefactoringSupport.getInstance().forLanguage(JavaLanguage.INSTANCE).getIntroduceVariableHandler();
    assert handler != null;
    ((JavaIntroduceVariableHandlerBase)handler).invoke(project, editor, (PsiExpression)startElement);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }


  @Override
  public @NotNull String getText() {
    return JavaBundle.message("intention.introduce.variable.text");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiElement element = PsiTreeUtil.findSameElementInCopy(myStartElement.getElement(), psiFile);
    if (element == null) return IntentionPreviewInfo.EMPTY;
    RefactoringActionHandler handler = LanguageRefactoringSupport.getInstance().forLanguage(JavaLanguage.INSTANCE).getIntroduceVariableHandler();
    assert handler != null;
    if (handler instanceof PreviewableRefactoringActionHandler previewableRefactoringActionHandler) {
      return previewableRefactoringActionHandler.generatePreview(project, element);
    }
    return IntentionPreviewInfo.EMPTY;
  }
}
