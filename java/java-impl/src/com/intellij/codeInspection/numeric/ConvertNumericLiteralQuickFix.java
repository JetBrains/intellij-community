// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.numeric;

import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

final class ConvertNumericLiteralQuickFix extends LocalQuickFixOnPsiElement {

  @NotNull private final String myConvertedValue;
  @NotNull private final String myText;

  ConvertNumericLiteralQuickFix(@NotNull final PsiLiteralExpression expression,
                                @NotNull final String convertedValue,
                                @NotNull final String text) {
    super(expression);
    this.myConvertedValue = convertedValue;
    this.myText = text;
  }

  @Override
  public @NlsContexts.ListItem @NotNull String getFamilyName() {
    return JavaBundle.message("inspection.underscores.in.literals.family");
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getText() {
    return myText;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(myConvertedValue, null);
    startElement.replace(replacement);
  }
}
