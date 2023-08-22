// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class DeleteSideEffectsAwareFix extends PsiUpdateModCommandAction<PsiStatement> {
  private final SmartPsiElementPointer<PsiExpression> myExpressionPtr;
  private final boolean myAlwaysAvailable;

  public DeleteSideEffectsAwareFix(@NotNull PsiStatement statement, PsiExpression expression) {
    this(statement, expression, false);
  }

  public DeleteSideEffectsAwareFix(@NotNull PsiStatement statement, PsiExpression expression, boolean alwaysAvailable) {
    super(statement);
    myAlwaysAvailable = alwaysAvailable;
    SmartPointerManager manager = SmartPointerManager.getInstance(statement.getProject());
    myExpressionPtr = manager.createSmartPsiElementPointer(expression);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("extract.side.effects.family.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiStatement statement) {
    PsiExpression expression = myExpressionPtr.getElement();
    if (expression == null) return null;
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(expression);
    String message = getMessage(expression, sideEffects);
    if (!myAlwaysAvailable &&
        // "Remove unnecessary parentheses" action is already present which will do the same
        sideEffects.size() == 1 && statement instanceof PsiExpressionStatement &&
        sideEffects.get(0) == PsiUtil.skipParenthesizedExprDown(expression)) {
      return null;
    }
    return Presentation.of(message).withPriority(PriorityAction.Priority.LOW)
      .withHighlighting(ContainerUtil.map2Array(sideEffects, TextRange.EMPTY_ARRAY, effect -> effect.getTextRange()));
  }

  /**
   * @param expression expression to remove
   * @param sideEffects side effects
   * @return inspection message
   */
  @IntentionName
  public static @NotNull String getMessage(@NotNull PsiExpression expression, @NotNull List<@NotNull PsiExpression> sideEffects) {
    if (sideEffects.isEmpty()) {
      JavaElementKind kind = expression.getParent() instanceof PsiExpressionStatement ? JavaElementKind.EXPRESSION : JavaElementKind.STATEMENT;
      return CommonQuickFixBundle.message("fix.remove.title", kind.object());
    }
    PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, expression);
    if (statements.length == 1 && statements[0] instanceof PsiIfStatement) {
      return QuickFixBundle.message("extract.side.effects.convert.to.if");
    }
    return QuickFixBundle.message("extract.side.effects", statements.length);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiStatement statement, @NotNull ModPsiUpdater updater) {
    PsiExpression expression = updater.getWritable(myExpressionPtr.getElement());
    if (expression == null) return;
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(expression);
    CommentTracker ct = new CommentTracker();
    sideEffects.forEach(ct::markUnchanged);
    PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, expression);
    if (statements.length > 0) {
      PsiStatement lastAdded = BlockUtils.addBefore(statement, statements);
      statement = Objects.requireNonNull(PsiTreeUtil.getNextSiblingOfType(lastAdded, PsiStatement.class));
    }
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiStatement &&
        !(parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getElseBranch() == statement) &&
        !(parent instanceof PsiForStatement && ((PsiForStatement)parent).getUpdate() == statement)) {
      ct.replaceAndRestoreComments(statement, "{}");
    } else {
      ct.deleteAndRestoreComments(statement);
    }
  }
}
