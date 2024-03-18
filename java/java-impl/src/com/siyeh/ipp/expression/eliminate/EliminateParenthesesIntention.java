// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.expression.eliminate;

import com.intellij.modcommand.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.modcommand.ModCommand.*;

public final class EliminateParenthesesIntention extends PsiBasedModCommandAction<PsiJavaToken> {
  public EliminateParenthesesIntention() {
    super(PsiJavaToken.class);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return IntentionPowerPackBundle.message("eliminate.parentheses.intention.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaToken leaf) {
    List<PsiParenthesizedExpression> possibleInnerExpressions = getPossibleInnerExpressions(leaf);
    return possibleInnerExpressions != null && !possibleInnerExpressions.isEmpty() ? Presentation.of(
      IntentionPowerPackBundle.message("eliminate.parentheses.intention.name")) : null;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiJavaToken leaf) {
    List<PsiParenthesizedExpression> possibleInnerExpressions = getPossibleInnerExpressions(leaf);
    if (possibleInnerExpressions == null) return nop();
    List<ModCommandAction> actions = ContainerUtil.map(
      possibleInnerExpressions,
      expr -> psiUpdateStep(expr, PsiExpressionTrimRenderer.render(expr), (e, u) -> replaceExpression(e)));
    return chooseAction(IntentionPowerPackBundle.message("eliminate.parentheses.intention.title"), actions);
  }

  private static void replaceExpression(@NotNull PsiParenthesizedExpression parenthesized) {
    EliminableExpression expression = createEliminableExpression(parenthesized);
    if (expression == null) return;
    StringBuilder sb = new StringBuilder();
    CommentTracker commentTracker = new CommentTracker();
    PsiExpression toReplace = expression.getExpressionToReplace();
    PsiPolyadicExpression outerExpression = ObjectUtils.tryCast(toReplace.getParent(), PsiPolyadicExpression.class);
    if (outerExpression == null || EliminateUtils.getOperator(outerExpression.getOperationTokenType()) == null) {
      if (!expression.eliminate(null, sb)) return;
      PsiReplacementUtil.replaceExpression(toReplace, sb.toString(), commentTracker);
      return;
    }
    for (PsiExpression operand : outerExpression.getOperands()) {
      PsiJavaToken tokenBefore = outerExpression.getTokenBeforeOperand(operand);
      if (operand == toReplace) {
        if (!expression.eliminate(tokenBefore, sb)) return;
        continue;
      }
      if (tokenBefore != null) sb.append(tokenBefore.getText());
      sb.append(operand.getText());
    }
    PsiReplacementUtil.replaceExpression(outerExpression, sb.toString(), commentTracker);
  }

  @Nullable
  private static EliminableExpression createEliminableExpression(@NotNull PsiParenthesizedExpression parenthesized) {
    DistributiveExpression distributive = DistributiveExpression.create(parenthesized);
    AssociativeExpression additive = AssociativeExpression.create(parenthesized);
    if (distributive == null) return additive;
    if (additive == null) return distributive;
    if (distributive.getExpression() == null) return additive;
    return distributive;
  }

  @Nullable
  private static List<PsiParenthesizedExpression> getPossibleInnerExpressions(@NotNull PsiElement element) {
    if (!(element instanceof PsiJavaToken)) return null;
    List<PsiParenthesizedExpression> possibleExpressions = new ArrayList<>();
    while ((element = PsiTreeUtil.getParentOfType(element, PsiParenthesizedExpression.class)) != null) {
      PsiParenthesizedExpression parenthesized = (PsiParenthesizedExpression)element;
      if (DistributiveExpression.create(parenthesized) != null || AssociativeExpression.create(parenthesized) != null) {
        possibleExpressions.add(parenthesized);
      }
    }
    return possibleExpressions;
  }
}
