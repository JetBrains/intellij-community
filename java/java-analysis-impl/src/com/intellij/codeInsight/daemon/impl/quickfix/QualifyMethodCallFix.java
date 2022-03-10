// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QualifyMethodCallFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myQualifierText;

  public QualifyMethodCallFix(@NotNull PsiMethodCallExpression call,
                              @NotNull String qualifierText) {
    super(call);
    myQualifierText = qualifierText;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("qualify.method.call.fix", myQualifierText);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("qualify.method.call.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiReferenceExpression expression = ((PsiMethodCallExpression)startElement).getMethodExpression();
    expression.setQualifierExpression(JavaPsiFacade.getElementFactory(project).createExpressionFromText(myQualifierText, null));
  }
}