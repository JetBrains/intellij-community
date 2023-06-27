// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteSideEffectsAwareFix;
import com.intellij.codeInsight.daemon.impl.quickfix.UnwrapSwitchLabelFix;
import com.intellij.codeInsight.options.JavaInspectionButtons;
import com.intellij.codeInsight.options.JavaInspectionControls;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.fix.BoxPrimitiveInTernaryFix;
import com.intellij.codeInspection.dataFlow.fix.FindDfaProblemCauseFix;
import com.intellij.codeInspection.dataFlow.fix.ReplaceWithBooleanEqualsFix;
import com.intellij.codeInspection.dataFlow.fix.SurroundWithRequireNonNullFix;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.dataflow.CreateNullBranchFix;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.java.JavaBundle.message;

public class DataFlowInspection extends DataFlowInspectionBase {
  private static final Logger LOG = Logger.getInstance(DataFlowInspection.class);

  @Override
  protected LocalQuickFix createMutabilityViolationFix(PsiElement violation) {
    return WrapWithMutableCollectionFix.createFix(violation);
  }

  @Nullable
  @Override
  protected LocalQuickFix createExplainFix(PsiExpression anchor, TrackingRunner.DfaProblemType problemType) {
    return new FindDfaProblemCauseFix(IGNORE_ASSERT_STATEMENTS, anchor, problemType);
  }

  @Nullable
  @Override
  protected LocalQuickFix createUnwrapSwitchLabelFix() {
    return new UnwrapSwitchLabelFix();
  }

  @Override
  protected LocalQuickFix createIntroduceVariableFix() {
    return new IntroduceVariableFix(true);
  }

  private static boolean isVolatileFieldReference(PsiExpression qualifier) {
    PsiElement target = qualifier instanceof PsiReferenceExpression ? ((PsiReferenceExpression)qualifier).resolve() : null;
    return target instanceof PsiField && ((PsiField)target).hasModifierProperty(PsiModifier.VOLATILE);
  }

  @Override
  protected @NotNull List<@NotNull LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef, boolean onTheFly) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    ContainerUtil.addIfNotNull(fixes, StreamFilterNotNullFix.makeFix(methodRef));
    if (onTheFly) {
      fixes.add(new ReplaceWithTernaryOperatorFix.ReplaceMethodRefWithTernaryOperatorFix());
    }
    return fixes;
  }

  @Override
  protected LocalQuickFix createRemoveAssignmentFix(PsiAssignmentExpression assignment) {
    if (assignment == null || assignment.getRExpression() == null || !(assignment.getParent() instanceof PsiExpressionStatement)) {
      return null;
    }
    return new DeleteSideEffectsAwareFix((PsiStatement)assignment.getParent(), assignment.getRExpression(), true).asQuickFix();
  }

  @Override
  protected @NotNull List<@NotNull LocalQuickFix> createCastFixes(PsiTypeCastExpression castExpression,
                                                                  PsiType realType,
                                                                  boolean onTheFly,
                                                                  boolean alwaysFails) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    PsiExpression operand = castExpression.getOperand();
    PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && operand != null) {
      if (!alwaysFails && !SideEffectChecker.mayHaveSideEffects(operand) && CodeBlockSurrounder.canSurround(castExpression)) {
        String suffix = " instanceof " + typeElement.getText();
        fixes.add(new AddAssertStatementFix(ParenthesesUtils.getText(operand, PsiPrecedenceUtil.RELATIONAL_PRECEDENCE) + suffix));
        if (SurroundWithIfFix.isAvailable(operand)) {
          fixes.add(new SurroundWithIfFix(operand, suffix));
        }
      }
      if (realType != null) {
        PsiType operandType = operand.getType();
        if (operandType != null) {
          PsiType type = typeElement.getType();
          PsiType[] types = {realType};
          if (realType instanceof PsiIntersectionType) {
            types = ((PsiIntersectionType)realType).getConjuncts();
          }
          for (PsiType psiType : types) {
            if (!psiType.isAssignableFrom(operandType)) {
              psiType = DfaPsiUtil.tryGenerify(operand, psiType);
              fixes.add(new ReplaceTypeInCastFix(type, psiType));
            }
          }
        }
      }
    }
    return fixes;
  }

  @Override
  protected @NotNull List<@NotNull LocalQuickFix> createNPEFixes(@Nullable PsiExpression qualifier,
                                                                 PsiExpression expression,
                                                                 boolean onTheFly,
                                                                 boolean alwaysNull) {
    qualifier = PsiUtil.deparenthesizeExpression(qualifier);

    final List<LocalQuickFix> fixes = new SmartList<>();
    if (qualifier == null || expression == null) return Collections.emptyList();

    try {
      ContainerUtil.addIfNotNull(fixes, StreamFilterNotNullFix.makeFix(qualifier));
      ContainerUtil.addIfNotNull(fixes, ReplaceComputeWithComputeIfPresentFix.makeFix(qualifier));
      if (isVolatileFieldReference(qualifier)) {
        ContainerUtil.addIfNotNull(fixes, createIntroduceVariableFix());
      }
      else if (!alwaysNull && !SideEffectChecker.mayHaveSideEffects(qualifier))  {
        String suffix = " != null";
        if (PsiUtil.getLanguageLevel(qualifier).isAtLeast(LanguageLevel.JDK_1_4) && CodeBlockSurrounder.canSurround(expression)) {
          String replacement = ParenthesesUtils.getText(qualifier, ParenthesesUtils.EQUALITY_PRECEDENCE) + suffix;
          fixes.add(new AddAssertStatementFix(replacement));
        }

        if (SurroundWithIfFix.isAvailable(qualifier)) {
          fixes.add(new SurroundWithIfFix(qualifier, suffix));
        }

        if (onTheFly && ReplaceWithTernaryOperatorFix.isAvailable(qualifier, expression)) {
          fixes.add(new ReplaceWithTernaryOperatorFix(qualifier));
        }
      }

      if (!alwaysNull && PsiUtil.isLanguageLevel7OrHigher(qualifier)) {
        fixes.add(new SurroundWithRequireNonNullFix(qualifier));
      }

      if (!ExpressionUtils.isNullLiteral(qualifier)) {
        ContainerUtil.addIfNotNull(fixes, createExplainFix(qualifier, new TrackingRunner.NullableDfaProblemType()));
      }

      ContainerUtil.addIfNotNull(fixes, DfaOptionalSupport.registerReplaceOptionalOfWithOfNullableFix(qualifier));

      addCreateNullBranchFix(qualifier, fixes);
    }

    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return fixes;
  }

  @Override
  protected @NotNull List<@NotNull LocalQuickFix> createUnboxingNullableFixes(@NotNull PsiExpression qualifier, PsiElement anchor, boolean onTheFly) {
    List<LocalQuickFix> result = new SmartList<>();
    if (TypeConversionUtil.isBooleanType(qualifier.getType())) {
      result.add(new ReplaceWithBooleanEqualsFix(qualifier));
    }
    ContainerUtil.addIfNotNull(result, BoxPrimitiveInTernaryFix.makeFix(ObjectUtils.tryCast(anchor, PsiExpression.class)));
    addCreateNullBranchFix(qualifier, result);
    return result;
  }

  private static void addCreateNullBranchFix(@NotNull PsiExpression qualifier, @NotNull List<? super @NotNull LocalQuickFix> fixes) {
    if (!HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(qualifier)) return;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(qualifier.getParent());
    if (parent instanceof PsiSwitchBlock && PsiUtil.skipParenthesizedExprDown(((PsiSwitchBlock)parent).getExpression()) == qualifier) {
      fixes.add(new CreateNullBranchFix((PsiSwitchBlock)parent));
    }
  }

  @Override
  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return new NullableStuffInspection.NavigateToNullLiteralArguments(parameter);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("SUGGEST_NULLABLE_ANNOTATIONS",
               message("inspection.data.flow.nullable.quickfix.option")),
      checkbox("TREAT_UNKNOWN_MEMBERS_AS_NULLABLE",
               message("inspection.data.flow.treat.non.annotated.members.and.parameters.as.nullable")),
      checkbox("REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER",
               message("inspection.data.flow.report.not.null.required.parameter.with.null.literal.argument.usages")),
      checkbox("REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL",
               message("inspection.data.flow.report.nullable.methods.that.always.return.a.non.null.value")),
      checkbox("IGNORE_ASSERT_STATEMENTS",
               message("inspection.data.flow.ignore.assert.statements")),
      checkbox("REPORT_UNSOUND_WARNINGS",
               message("inspection.data.flow.report.problems.that.happen.only.on.some.code.paths")),
      JavaInspectionControls.button(
        JavaInspectionButtons.ButtonKind.NULLABILITY_ANNOTATIONS)
    );
  }
}