// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class AddAssertStatementFix extends PsiUpdateModCommandQuickFix {
  private final String myText;

  public AddAssertStatementFix(@NotNull String text) {
    myText = text;
  }

  @Override
  @NotNull
  public String getName() {
    return JavaBundle.message("inspection.assert.quickfix", myText);
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiExpression expr = PsiTreeUtil.getNonStrictParentOfType(element, PsiExpression.class);
    if (expr == null) return;
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(expr);
    if (surrounder == null) return;
    CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    expr = result.getExpression();
    PsiElement anchorElement = result.getAnchor();
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(anchorElement);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      anchorElement = prev;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    @NonNls String text = "assert " + myText + ";";
    PsiAssertStatement assertStatement = (PsiAssertStatement)factory.createStatementFromText(text, expr);

    anchorElement.getParent().addBefore(assertStatement, anchorElement);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("inspection.quickfix.assert.family");
  }
}
