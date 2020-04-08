// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.numeric;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

final class ConvertNumericLiteralQuickFix implements LocalQuickFix {

  @NotNull private final String myConvertedValue;
  @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) private final String myName;

  ConvertNumericLiteralQuickFix(@NotNull final String convertedValue,
                                @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) final String name) {
    myConvertedValue = convertedValue;
    myName = name;
  }

  @Override
  public @NlsContexts.ListItem @NotNull String getFamilyName() {
    return JavaBundle.message("inspection.underscores.in.literals.family");
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
    return myName;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    assert element != null : "Problem descriptor cannot be without PsiElement";

    final PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(myConvertedValue, null);
    element.replace(replacement);
  }
}
