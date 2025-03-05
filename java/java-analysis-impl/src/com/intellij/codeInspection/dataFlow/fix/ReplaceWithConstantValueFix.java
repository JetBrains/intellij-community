// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.*;
import com.intellij.refactoring.extractMethod.ExtractMethodUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class ReplaceWithConstantValueFix extends PsiBasedModCommandAction<PsiExpression> {
  private final @NotNull String myPresentableName;
  private final @NotNull String myReplacementText;
  private final @NotNull ThreeState myExtractSideEffects;

  public ReplaceWithConstantValueFix(@NotNull PsiExpression expression, @NotNull String presentableName, @NotNull String replacementText) {
    super(expression);
    myPresentableName = presentableName;
    myReplacementText = replacementText;
    myExtractSideEffects = ThreeState.UNSURE;
  }

  private ReplaceWithConstantValueFix(@NotNull PsiExpression expression,
                                      @NotNull String presentableName,
                                      @NotNull String replacementText,
                                      boolean extractSideEffects) {
    super(expression);
    myPresentableName = presentableName;
    myReplacementText = replacementText;
    myExtractSideEffects = ThreeState.fromBoolean(extractSideEffects);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression element) {
    return switch (myExtractSideEffects) {
      case YES -> Presentation.of(JavaBundle.message("intention.family.name.extract.possible.side.effects"));
      case NO -> Presentation.of(JavaBundle.message("intention.family.name.delete.possible.side.effects"));
      case UNSURE -> Presentation.of(CommonQuickFixBundle.message("fix.replace.with.x", myPresentableName));
    };
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("replace.with.constant.value");
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiExpression expression) {
    List<PsiExpression> sideEffects =
      myExtractSideEffects == ThreeState.NO ? List.of() : SideEffectChecker.extractSideEffectExpressions(expression);
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(expression);
    if (surrounder == null) {
      sideEffects = List.of();
    }
    if (sideEffects.isEmpty()) {
      return ModCommand.psiUpdate(expression, expr -> applyFix(expr, PsiStatement.EMPTY_ARRAY));
    }
    if (myExtractSideEffects == ThreeState.UNSURE) {
      return ModCommand.chooseAction(JavaAnalysisBundle.message("replace.with.constant.value.title"),
                                     new ReplaceWithConstantValueFix(expression, myPresentableName, myReplacementText, true),
                                     new ReplaceWithConstantValueFix(expression, myPresentableName, myReplacementText, false));
    }
    PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, expression);
    return ModCommand.psiUpdate(expression, (expr, updater) -> applyFix(expr, statements));
  }
  
  private void applyFix(@NotNull PsiExpression expression, @NotNull PsiStatement @NotNull [] sideEffects) {
    if (sideEffects.length > 0) {
      CodeBlockSurrounder surrounder = Objects.requireNonNull(CodeBlockSurrounder.forExpression(expression));
      CodeBlockSurrounder.SurroundResult result = surrounder.surround();
      expression = result.getExpression();
      BlockUtils.addBefore(result.getAnchor(), sideEffects);
    }
    PsiMethodCallExpression call = expression.getParent() instanceof PsiExpressionList list &&
                                   list.getParent() instanceof PsiMethodCallExpression grandParent ? grandParent : null;
    PsiMethod targetMethod = null;
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (call != null) {
      JavaResolveResult result = call.resolveMethodGenerics();
      substitutor = result.getSubstitutor();
      targetMethod = ObjectUtils.tryCast(result.getElement(), PsiMethod.class);
    }

    new CommentTracker().replaceAndRestoreComments(expression, myReplacementText);

    if (targetMethod != null) {
      ExtractMethodUtil.addCastsToEnsureResolveTarget(targetMethod, substitutor, call);
    }
  }
}
