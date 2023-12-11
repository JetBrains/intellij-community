// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.whileloop;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class ExtractWhileLoopConditionToIfStatementIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("extract.while.loop.condition.to.if.statement.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("extract.while.loop.condition.to.if.statement.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    WhileLoopPredicate predicate = new WhileLoopPredicate();
    return e -> predicate.satisfiedBy(e) &&
                !(ExpressionUtils
                    .isLiteral(PsiUtil.skipParenthesizedExprDown(((PsiWhileStatement)e.getParent()).getCondition()), Boolean.TRUE));
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiWhileStatement whileStatement = (PsiWhileStatement)element.getParent();
    if (whileStatement == null) {
      return;
    }
    final PsiExpression condition = whileStatement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = BoolUtils.getNegatedExpressionText(condition);
    final Project project = whileStatement.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    condition.replace(factory.createExpressionFromText("true", whileStatement));
    final PsiStatement body = whileStatement.getBody();
    final PsiStatement ifStatement = factory.createStatementFromText("if (" + conditionText + ") break;", whileStatement);
    final PsiElement newElement;
    if (body instanceof PsiBlockStatement blockStatement) {
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      newElement = codeBlock.addBefore(ifStatement, codeBlock.getFirstBodyElement());
    }
    else if (body != null) {
      final PsiStatement newStatement = BlockUtils.expandSingleStatementToBlockStatement(body);
      newElement = newStatement.getParent().addBefore(ifStatement, newStatement);
    }
    else {
      return;
    }
    CodeStyleManager.getInstance(project).reformat(newElement);
  }
}