// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CastCanBeReplacedWithVariableInspection extends AbstractBaseJavaLocalInspectionTool
  implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTypeCastExpression(@NotNull PsiTypeCastExpression typeCastExpression) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(typeCastExpression, PsiMethod.class);

        if (method == null) {
          return;
        }

        final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(typeCastExpression.getOperand());
        if (!(operand instanceof PsiReferenceExpression operandReference)) {
          return;
        }

        final PsiElement resolved = operandReference.resolve();
        if (!(resolved instanceof PsiParameter) && !(resolved instanceof PsiLocalVariable)) {
          return;
        }

        final PsiVariable replacement = findReplacement(method, (PsiVariable)resolved, typeCastExpression);
        if (replacement == null) {
          return;
        }

        final String variableName = replacement.getName();
        final String castExpressionText = typeCastExpression.getText();
        final LocalQuickFix fix = new ReplaceCastWithVariableFix(castExpressionText, replacement);
        holder.registerProblem(typeCastExpression,
                               InspectionGadgetsBundle.message("inspection.cast.can.be.replaced.with.variable.message",
                                                               variableName, castExpressionText), fix);
      }
    };
  }

  @Nullable
  private static PsiVariable findReplacement(@NotNull PsiMethod method,
                                             @NotNull PsiVariable castedVar,
                                             @NotNull PsiTypeCastExpression expression) {
    if (InstanceOfUtils.isUncheckedCast(expression)) return null;
    PsiTypeElement expressionCastType = expression.getCastType();
    if (expressionCastType == null) return null;
    final PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return null;
    final TextRange expressionTextRange = expression.getTextRange();
    if (expressionTextRange == null) return null;
    PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
    if (operand == null) return null;
    PsiType castType = expressionCastType.getType();
    List<PsiTypeCastExpression> found =
      SyntaxTraverser.psiTraverser(method)
        .filter(PsiTypeCastExpression.class)
        .filter(cast -> EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(cast.getOperand(), operand))
        .filter(cast -> {
          PsiTypeElement typeElement = cast.getCastType();
          return typeElement != null && InstanceOfUtils.typeCompatible(typeElement.getType(), castType, operand);
        })
        .toList();
    PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(method.getProject());
    for (PsiTypeCastExpression occurrence : found) {
      if (!isAtRightLocation(expression, expressionTextRange, occurrence)) continue;

      final PsiLocalVariable variable = getVariable(occurrence);

      if (variable != null &&
          resolveHelper.resolveReferencedVariable(variable.getName(), expression) == variable &&
          !isChangedBetween(castedVar, methodBody, occurrence, expression) &&
          !isChangedBetween(variable, methodBody, occurrence, expression)) {
        return variable;
      }
    }

    PsiInstanceOfExpression instanceOf = InstanceOfUtils.findPatternCandidate(expression);
    if (instanceOf != null) {
      PsiPattern pattern = instanceOf.getPattern();
      PsiPatternVariable patternVariable = JavaPsiPatternUtil.getPatternVariable(pattern);
      if (patternVariable != null &&
          !isChangedBetween(castedVar, methodBody, instanceOf, expression) &&
          !isChangedBetween(patternVariable, methodBody, instanceOf, expression)) {
        return patternVariable;
      }
    }

    List<PsiAssignmentExpression> narrowVariables =
      SyntaxTraverser.psiTraverser(method)
        .filter(PsiAssignmentExpression.class)
        .filter(assignment -> EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(assignment.getLExpression(), operand))
        .filter(assignment -> {
          PsiExpression narrowVariable = assignment.getRExpression();
          return narrowVariable != null && narrowVariable.getType() != null && InstanceOfUtils.typeCompatible(narrowVariable.getType(), castType, operand);
        })
        .toList();
    for (PsiAssignmentExpression narrowVariable : narrowVariables) {
      if (!isAtRightLocation(expression,  expressionTextRange, narrowVariable)) continue;

      final PsiLocalVariable declaration = getVariable(narrowVariable);

      if (declaration != null &&
          resolveHelper.resolveReferencedVariable(declaration.getName(), expression) == declaration &&
          !isChangedBetween(castedVar, methodBody, narrowVariable, expression) &&
          !isChangedBetween(declaration, methodBody, narrowVariable, expression)) {
        return declaration;
      }
    }

    if (!(operand instanceof PsiReferenceExpression wideReferenceExpression)
        || !(wideReferenceExpression.resolve() instanceof PsiLocalVariable wideVariable)) return null;

    PsiExpression narrowExpression = wideVariable.getInitializer();
    if (!(narrowExpression instanceof PsiReferenceExpression narrowReferenceExpression)
        || !(narrowReferenceExpression.resolve() instanceof PsiVariable narrowVariable)
        || narrowExpression.getType() == null
        || !InstanceOfUtils.typeCompatible(narrowExpression.getType(), castType, operand)) return null;

    if (!isAtRightLocation(expression, expressionTextRange, narrowExpression)) return null;

    if (narrowVariable.getName() == null
        || resolveHelper.resolveReferencedVariable(narrowVariable.getName(), expression) != narrowVariable
        || isChangedBetween(castedVar, methodBody, narrowExpression, expression)
        || isChangedBetween(narrowVariable, methodBody, narrowExpression, expression)) return null;

    return narrowVariable;
  }

  private static boolean isAtRightLocation(@NotNull PsiTypeCastExpression expression,
                                           @NotNull TextRange expressionTextRange,
                                           @NotNull PsiExpression occurrence) {
    ProgressIndicatorProvider.checkCanceled();
    final TextRange occurrenceTextRange = occurrence.getTextRange();
    return occurrence != expression && occurrenceTextRange.getEndOffset() < expressionTextRange.getStartOffset();
  }

  public static boolean isChangedBetween(@NotNull final PsiVariable variable,
                                          @NotNull final PsiElement scope,
                                          @NotNull final PsiElement start,
                                          @NotNull final PsiElement end) {
    if (variable.hasModifierProperty(PsiModifier.FINAL) || HighlightControlFlowUtil.isEffectivelyFinal(variable, scope, null)) {
      return false;
    }

    PsiElement broadEnd = getBroadEnd(scope, start, end);

    ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(variable.getProject()).getControlFlow(scope, new LocalsControlFlowPolicy(scope), true);
    }
    catch (AnalysisCanceledException ignored) {
      controlFlow = ControlFlow.EMPTY;
    }
    int startOffset = controlFlow.getEndOffset(start) + 1;
    int endOffset = controlFlow.getEndOffset(broadEnd);
    return ControlFlowUtil.getWrittenVariables(controlFlow, startOffset, endOffset, true).contains(variable);
  }

  @NotNull
  private static PsiElement getBroadEnd(@NotNull PsiElement scope, @NotNull PsiElement start, @NotNull PsiElement end) {
    List<PsiElement> parentsOfStart = new ArrayList<>();
    PsiElement currentElement = start.getParent();

    while (currentElement != null && currentElement != scope) {
      parentsOfStart.add(currentElement);
      currentElement = currentElement.getParent();
    }

    PsiElement broadEnd = end;
    currentElement = end.getParent();

    while (currentElement != null && currentElement != scope && !parentsOfStart.contains(currentElement)) {
      if (currentElement instanceof PsiLoopStatement) {
        broadEnd = currentElement;
      }

      currentElement = currentElement.getParent();
    }

    return broadEnd;
  }

  @Nullable
  private static PsiLocalVariable getVariable(@NotNull PsiTypeCastExpression occurrence) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(occurrence.getParent());

    if (parent instanceof PsiLocalVariable localVariable) {
      return localVariable;
    }

    if (parent instanceof PsiAssignmentExpression assignmentExpression &&
        assignmentExpression.getLExpression() instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.resolve() instanceof PsiLocalVariable localVariable) {
      return localVariable;
    }

    return null;
  }

  @Nullable
  private static PsiLocalVariable getVariable(@NotNull PsiAssignmentExpression occurrence) {
    if (PsiUtil.skipParenthesizedExprDown(occurrence.getRExpression()) instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.resolve() instanceof PsiLocalVariable localVariable) {
      return localVariable;
    }

    return null;
  }

  private static class ReplaceCastWithVariableFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull String myText;
    private final @NotNull String myVariableName;

    private ReplaceCastWithVariableFix(@NotNull String text, @NotNull PsiVariable variable) {
      myText = text;
      myVariableName = Objects.requireNonNull(variable.getName());
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", myText, myVariableName);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.cast.can.be.replaced.with.variable.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiTypeCastExpression typeCastExpression) {
        final PsiElement toReplace =
          typeCastExpression.getParent() instanceof PsiParenthesizedExpression ? typeCastExpression.getParent() : typeCastExpression;
        new CommentTracker().replaceAndRestoreComments(toReplace, myVariableName);
      }
    }
  }
}
