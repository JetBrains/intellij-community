// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class SurroundWithRequireNonNullFix extends PsiUpdateModCommandQuickFix {
  private final String myText;
  private final SmartPsiElementPointer<PsiExpression> myQualifierPointer;

  public SurroundWithRequireNonNullFix(@NotNull PsiExpression expressionToSurround) {
    myText = expressionToSurround.getText();
    myQualifierPointer =
      SmartPointerManager.getInstance(expressionToSurround.getProject()).createSmartPsiElementPointer(expressionToSurround);
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return JavaAnalysisBundle.message("inspection.surround.requirenonnull.quickfix", myText);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("inspection.surround.requirenonnull.quickfix", "");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiExpression qualifier = updater.getWritable(myQualifierPointer.getElement());
    if (qualifier == null) return;
    PsiExpression replacement = JavaPsiFacade.getElementFactory(project)
      .createExpressionFromText("java.util.Objects.requireNonNull(" + qualifier.getText() + ")", qualifier);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(qualifier.replace(replacement));
  }
}
