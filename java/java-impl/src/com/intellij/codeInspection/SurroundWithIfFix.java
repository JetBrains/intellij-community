// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.trivialif.MergeIfAndIntention;
import org.jetbrains.annotations.NotNull;

public class SurroundWithIfFix extends PsiUpdateModCommandQuickFix {
  private static final Logger LOG = Logger.getInstance(SurroundWithIfFix.class);
  private final String myText;
  private final String mySuffix;

  @Override
  @NotNull
  public String getName() {
    return JavaBundle.message("inspection.surround.if.quickfix", myText, mySuffix);
  }

  public SurroundWithIfFix(@NotNull PsiExpression expressionToAssert, String suffix) {
    myText = ParenthesesUtils.getText(expressionToAssert, ParenthesesUtils.BINARY_AND_PRECEDENCE);
    mySuffix = suffix;
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiElement anchorStatement = CommonJavaRefactoringUtil.getParentStatement(element, false);
    LOG.assertTrue(anchorStatement != null);
    if (anchorStatement.getParent() instanceof PsiLambdaExpression) {
      final PsiCodeBlock body = CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock((PsiLambdaExpression)anchorStatement.getParent());
      anchorStatement = body.getStatements()[0];
    }
    PsiElement[] elements = {anchorStatement};
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(anchorStatement);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      elements = new PsiElement[] {prev, anchorStatement};
    }
    final PsiIfStatement ifStatement = new JavaWithIfSurrounder()
      .surroundStatements(project, anchorStatement.getParent(), elements, myText + mySuffix);
    new MergeIfAndIntention().processIntention(ifStatement.getFirstChild());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("inspection.surround.if.family");
  }

  public static boolean isAvailable(PsiExpression qualifier) {
    if (!qualifier.isValid() || qualifier.getText() == null) {
      return false;
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(qualifier, PsiStatement.class);
    if (statement == null) return false;
    PsiElement parent = statement.getParent();
    return !(parent instanceof PsiForStatement);
  }
}
