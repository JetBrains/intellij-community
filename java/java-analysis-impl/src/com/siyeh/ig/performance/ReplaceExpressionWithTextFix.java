// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceExpressionWithTextFix extends PsiUpdateModCommandQuickFix {
  private final @NotNull String myReplacementText;
  private final @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String myName;

  public ReplaceExpressionWithTextFix(@NotNull @NonNls String replacementText,
                                      @NotNull
                                      @Nls(capitalization = Nls.Capitalization.Sentence) String name) {
    myReplacementText = replacementText;
    myName = name;
  }


  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
    PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(startElement, PsiNewExpression.class);
    if (newExpression == null) return;
    PsiElement result = new CommentTracker().replaceAndRestoreComments(newExpression, myReplacementText);
    RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(result);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
    return myName;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return myName;
  }
}
