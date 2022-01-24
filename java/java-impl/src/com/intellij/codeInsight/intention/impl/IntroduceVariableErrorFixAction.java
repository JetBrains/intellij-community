// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
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
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    RefactoringActionHandler handler = LanguageRefactoringSupport.INSTANCE.forLanguage(JavaLanguage.INSTANCE).getIntroduceVariableHandler();
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
}
