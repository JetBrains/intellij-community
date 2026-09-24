// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.daemon.impl.quickfix.UnwrapSwitchLabelFix;
import com.intellij.codeInsight.options.JavaConfigurationDialogKind;
import com.intellij.codeInspection.AddAssertNonNullFromTestFrameworksFix;
import com.intellij.codeInspection.AddAssertNonNullFromTestFrameworksFix.Variant;
import com.intellij.codeInspection.AddAssertStatementFix;
import com.intellij.codeInspection.IntroduceVariableAndAssertFix;
import com.intellij.codeInspection.IntroduceVariableAndReplaceWithTernaryFix;
import com.intellij.codeInspection.IntroduceVariableAndSurroundWithIfFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.RemoveAssignmentFix;
import com.intellij.codeInspection.ReplaceComputeWithComputeIfPresentFix;
import com.intellij.codeInspection.ReplaceTypeInCastFix;
import com.intellij.codeInspection.ReplaceWithTernaryOperatorFix;
import com.intellij.codeInspection.StreamFilterNotNullFix;
import com.intellij.codeInspection.SurroundWithIfFix;
import com.intellij.codeInspection.WrapWithMutableCollectionFix;
import com.intellij.codeInspection.dataFlow.fix.BoxPrimitiveInTernaryFix;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.codeInspection.dataFlow.fix.FindDfaProblemCauseFix;
import com.intellij.codeInspection.dataFlow.fix.ReplaceWithBooleanEqualsFix;
import com.intellij.codeInspection.dataFlow.fix.SurroundWithRequireNonNullFix;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.nullable.NavigateToNullLiteralArguments;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCaseLabelElement;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
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

@SuppressWarnings("InspectionDescriptionNotFoundInspection")
public final class DataFlowInspection extends DataFlowInspectionBase {
  private static final Logger LOG = Logger.getInstance(DataFlowInspection.class);

  @Override
  protected LocalQuickFix createMutabilityViolationFix(PsiElement violation) {
    return WrapWithMutableCollectionFix.createFix(violation);
  }

  @Override
  protected @NotNull LocalQuickFix createExplainFix(PsiExpression anchor, TrackingRunner.DfaProblemType problemType) {
    return new FindDfaProblemCauseFix(IGNORE_ASSERT_STATEMENTS, anchor, problemType);
  }

  @Override
  protected @NotNull LocalQuickFix createUnwrapSwitchLabelFix() {
    return new UnwrapSwitchLabelFix();
  }

  @Override
  protected LocalQuickFix createIntroduceVariableFix() {
    return new IntroduceVariableFix(true);
  }

  @Override
  protected @NotNull LocalQuickFix createDeleteLabelFix(PsiCaseLabelElement label) {
    return LocalQuickFix.from(new DeleteSwitchLabelFix(label, true));
  }

  private static boolean isVolatileFieldReference(PsiExpression qualifier) {
    return qualifier instanceof PsiReferenceExpression ref &&
           ref.resolve() instanceof PsiField field &&
           field.hasModifierProperty(PsiModifier.VOLATILE);
  }

  /**
   * Creates a fix that asserts {@code operand + suffix}.
   *
   * @param operand    expression the assertion is about
   * @param suffix     text to append to the operand to get the assertion condition (e.g., {@code " != null"})
   * @param precedence precedence the operand text should be parenthesized for
   * @return a fix that adds the assertion in-place if the operand may be checked as is;
   * otherwise a fix that extracts the operand into a local variable first; null if no assertion could be added
   * @see #canCheckAsIs(PsiExpression)
   */
  private static @Nullable LocalQuickFix createAssertFix(@NotNull PsiExpression operand, @NotNull String suffix, int precedence) {
    if (canCheckAsIs(operand)) {
      return new AddAssertStatementFix(ParenthesesUtils.getText(operand, precedence) + suffix);
    }
    return IntroduceVariableAndAssertFix.create(operand, suffix);
  }

  /**
   * Creates a fix that asserts that the qualifier is not null using the assertion method of the given test framework.
   *
   * @param qualifier expression the assertion is about
   * @param variant   test framework to take the assertion method from
   * @return a fix that adds the assertion call in-place if the qualifier may be checked as is;
   * otherwise a fix that extracts the qualifier into a local variable first; null if no assertion could be added
   * @see #canCheckAsIs(PsiExpression)
   */
  private static @Nullable LocalQuickFix createTestFrameworkAssertFix(@NotNull PsiExpression qualifier, @NotNull Variant variant) {
    if (canCheckAsIs(qualifier)) {
      return new AddAssertNonNullFromTestFrameworksFix(qualifier, variant);
    }
    return IntroduceVariableAndAssertFix.create(qualifier, variant);
  }

  /**
   * Creates a fix that surrounds the statement with an {@code if} that checks {@code operand + suffix}.
   *
   * @param operand expression the check is about
   * @param suffix  text to append to the operand to get the condition (e.g., {@code " != null"})
   * @return a fix that checks the operand in-place if it may be checked as is;
   * otherwise a fix that extracts the operand into a local variable first; null if no check could be added
   * @see #canCheckAsIs(PsiExpression)
   */
  private static @Nullable LocalQuickFix createSurroundWithIfFix(@NotNull PsiExpression operand, @NotNull String suffix) {
    if (canCheckAsIs(operand)) {
      return SurroundWithIfFix.isAvailable(operand) ? new SurroundWithIfFix(operand, suffix) : null;
    }
    return IntroduceVariableAndSurroundWithIfFix.create(operand, suffix);
  }

  /**
   * Creates a fix that replaces the dereference of the qualifier with a conditional expression checking it for null.
   *
   * @param qualifier  expression the check is about
   * @param expression expression that dereferences the qualifier
   * @return a fix that checks the qualifier in-place if it may be checked as is;
   * otherwise a fix that extracts the qualifier into a local variable first; null if no check could be added
   * @see #canCheckAsIs(PsiExpression)
   */
  private static @Nullable LocalQuickFix createTernaryFix(@NotNull PsiExpression qualifier, @NotNull PsiExpression expression) {
    if (!ReplaceWithTernaryOperatorFix.isAvailable(qualifier, expression)) return null;
    if (canCheckAsIs(qualifier)) {
      return new ReplaceWithTernaryOperatorFix(qualifier);
    }
    return IntroduceVariableAndReplaceWithTernaryFix.create(qualifier);
  }

  /**
   * A generated check mentions the expression one more time, which is only useful if re-evaluating it has no visible
   * effect and the analysis knows that both occurrences produce the same value. Otherwise, the expression should be
   * extracted into a local variable, which is evaluated exactly once and is checked instead of the expression.
   *
   * @return true if the expression may be checked in-place
   */
  private static boolean canCheckAsIs(@NotNull PsiExpression expression) {
    return !SideEffectChecker.mayHaveSideEffects(expression) && isTrackedByDfa(expression);
  }

  /**
   * @return true if the dataflow analysis represents the expression as a variable, so an assertion about it
   * is remembered for the subsequent occurrences of the same expression. Note that it's kinda heuristical method,
   * which may not always work.
   */
  private static boolean isTrackedByDfa(@NotNull PsiExpression expression) {
    // casts to a reference type are transparent for the analysis, see JavaDfaValueFactory#getQualifierOrThisValue
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(expression);
    while (stripped instanceof PsiTypeCastExpression cast && cast.getType() instanceof PsiClassType) {
      stripped = PsiUtil.skipParenthesizedExprDown(cast.getOperand());
    }
    if (stripped == null) return false;
    DfaValueFactory factory = new DfaValueFactory(expression.getProject());
    return JavaDfaValueFactory.getExpressionDfaValue(factory, stripped) instanceof DfaVariableValue;
  }

  @Override
  protected @NotNull List<@NotNull LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    ContainerUtil.addIfNotNull(fixes, StreamFilterNotNullFix.makeFix(methodRef));
    fixes.add(new ReplaceWithTernaryOperatorFix.ReplaceMethodRefWithTernaryOperatorFix());
    return fixes;
  }

  @Override
  protected LocalQuickFix createRemoveAssignmentFix(PsiAssignmentExpression assignment) {
    if (assignment == null || assignment.getRExpression() == null || !(assignment.getParent() instanceof PsiExpressionStatement)) {
      return null;
    }
    return new RemoveAssignmentFix();
  }

  @Override
  protected @NotNull List<@NotNull LocalQuickFix> createCastFixes(PsiTypeCastExpression castExpression,
                                                                  PsiType realType,
                                                                  boolean alwaysFails) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    PsiExpression operand = castExpression.getOperand();
    PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && operand != null) {
      if (!alwaysFails && CodeBlockSurrounder.canSurround(castExpression)) {
        String suffix = " instanceof " + typeElement.getText();
        ContainerUtil.addIfNotNull(fixes, createAssertFix(operand, suffix, PsiPrecedenceUtil.RELATIONAL_PRECEDENCE));
        ContainerUtil.addIfNotNull(fixes, createSurroundWithIfFix(operand, suffix));
      }
      if (realType != null) {
        PsiType operandType = operand.getType();
        if (operandType != null) {
          PsiType type = typeElement.getType();
          PsiType[] types = realType instanceof PsiIntersectionType intersectionType ?
                            intersectionType.getConjuncts() : new PsiType[]{realType};
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
      else if (!alwaysNull) {
        String suffix = " != null";

        Variant testFrameworkFixVariant = AddAssertNonNullFromTestFrameworksFix.isAvailable(expression);
        if (testFrameworkFixVariant != null) {
          ContainerUtil.addIfNotNull(fixes, createTestFrameworkAssertFix(qualifier, testFrameworkFixVariant));
        }
        else if (PsiUtil.isAvailable(JavaFeature.ASSERTIONS, qualifier) && CodeBlockSurrounder.canSurround(expression)) {
          ContainerUtil.addIfNotNull(fixes, createAssertFix(qualifier, suffix, ParenthesesUtils.EQUALITY_PRECEDENCE));
        }

        ContainerUtil.addIfNotNull(fixes, createSurroundWithIfFix(qualifier, suffix));
        ContainerUtil.addIfNotNull(fixes, createTernaryFix(qualifier, expression));
      }

      if (!alwaysNull && PsiUtil.isAvailable(JavaFeature.OBJECTS_CLASS, qualifier)) {
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
  protected @NotNull List<@NotNull LocalQuickFix> createUnboxingNullableFixes(@NotNull PsiExpression qualifier, PsiElement anchor) {
    List<LocalQuickFix> result = new SmartList<>();
    if (TypeConversionUtil.isBooleanType(qualifier.getType())) {
      result.add(new ReplaceWithBooleanEqualsFix(qualifier));
    }
    ContainerUtil.addIfNotNull(result, BoxPrimitiveInTernaryFix.makeFix(ObjectUtils.tryCast(anchor, PsiExpression.class)));
    addCreateNullBranchFix(qualifier, result);
    return result;
  }

  private static void addCreateNullBranchFix(@NotNull PsiExpression qualifier, @NotNull List<? super @NotNull LocalQuickFix> fixes) {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, qualifier)) return;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(qualifier.getParent());
    if (parent instanceof PsiSwitchBlock block && PsiUtil.skipParenthesizedExprDown(block.getExpression()) == qualifier) {
      fixes.add(LocalQuickFix.from(new CreateNullBranchFix(block)));
    }
  }

  @Override
  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return new NavigateToNullLiteralArguments(parameter);
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
      checkbox("REPORT_MATCHED_EXCEPTION",
               message("inspection.data.flow.report.match.exception.problem")),
      checkbox("REPORT_UNSOUND_WARNINGS",
               message("inspection.data.flow.report.problems.that.happen.only.on.some.code.paths")),
      checkbox("REPORT_UNSPECIFIED_PARAMETRIC_NULLNESS",
               message("inspection.data.flow.report.unspecified.parametric.nullness")),
      JavaConfigurationDialogKind.NULLABILITY_ANNOTATIONS.button()
    );
  }
}
