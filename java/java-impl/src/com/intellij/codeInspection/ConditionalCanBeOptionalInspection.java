// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.codeInspection.util.OptionalRefactoringUtil;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class ConditionalCanBeOptionalInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitConditionalExpression(PsiConditionalExpression ternary) {
        TernaryNullCheck ternaryNullCheck = TernaryNullCheck.from(ternary);
        if (ternaryNullCheck == null) return;
        PsiVariable variable = ternaryNullCheck.myVariable;
        PsiExpression nullBranch = ternaryNullCheck.myNullBranch;
        PsiExpression notNullBranch = ternaryNullCheck.myNotNullBranch;
        if (!ExpressionUtils.isSafelyRecomputableExpression(nullBranch) && !LambdaGenerationUtil.canBeUncheckedLambda(nullBranch, variable::equals)) {
          return;
        }
        List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(variable, notNullBranch);
        if (references.isEmpty() ||
            variable instanceof PsiField && references.stream().noneMatch(ExpressionUtil::isEffectivelyUnqualified)) {
          return;
        }
        if (!LambdaGenerationUtil.canBeUncheckedLambda(notNullBranch, variable::equals)) {
          return;
        }
        if (!areTypesCompatible(nullBranch, notNullBranch)) return;
        boolean mayChangeSemantics =
          !ExpressionUtils.isNullLiteral(nullBranch) && NullabilityUtil.getExpressionNullability(notNullBranch, true) != Nullability.NOT_NULL;
        if (!isOnTheFly && mayChangeSemantics) return;
        holder.registerProblem(ternary.getCondition(),
                               "Can be replaced with Optional.ofNullable()",
                               mayChangeSemantics ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new ReplaceConditionWithOptionalFix(mayChangeSemantics));
      }

      private boolean areTypesCompatible(PsiExpression nullBranch, PsiExpression notNullBranch) {
        PsiType notNullType = ((PsiExpression)notNullBranch.copy()).getType();
        PsiType nullType = ((PsiExpression)nullBranch.copy()).getType();
        if (nullType == null || notNullType == null) return false;
        if (nullType.isAssignableFrom(notNullType)) return true;
        if (nullType.equals(PsiType.NULL)) return true;
        if (OptionalUtil.isOptionalEmptyCall(nullBranch) && TypeUtils.isOptional(notNullType)) return true;
        return false;
      }
    };
  }

  private static class ReplaceConditionWithOptionalFix implements LocalQuickFix {
    private final boolean myChangesSemantics;

    ReplaceConditionWithOptionalFix(boolean changesSemantics) {
      myChangesSemantics = changesSemantics;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName() + (myChangesSemantics ? " (may change semantics)" : "");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with Optional.ofNullable() chain";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiConditionalExpression ternary = PsiTreeUtil.getNonStrictParentOfType(descriptor.getStartElement(), PsiConditionalExpression.class);
      TernaryNullCheck ternaryNullCheck = TernaryNullCheck.from(ternary);
      if (ternaryNullCheck == null) return;
      PsiVariable variable = ternaryNullCheck.myVariable;
      String name = variable.getName();
      if (name == null) return;
      String inLambdaName =
        new VariableNameGenerator(ternary, VariableKind.PARAMETER).byName(name).byType(variable.getType()).generate(true);
      PsiExpression nullBranch = ternaryNullCheck.myNullBranch;
      PsiExpression notNullBranch = ternaryNullCheck.myNotNullBranch;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CommentTracker ct = new CommentTracker();
      for (PsiReferenceExpression reference : VariableAccessUtils.getVariableReferences(variable, nullBranch)) {
        if (ExpressionUtil.isEffectivelyUnqualified(reference)) {
          PsiElement result = ct.replace(reference, "null");
          if (nullBranch == reference) {
            nullBranch = (PsiExpression)result;
          }
        }
      }
      String origExpression = null;
      for (PsiReferenceExpression reference : VariableAccessUtils.getVariableReferences(variable, notNullBranch)) {
        PsiExpression qualifier = reference.getQualifierExpression();
        if (qualifier != null) {
          if (!ExpressionUtil.isEffectivelyUnqualified(reference)) continue;
          if (origExpression == null) {
            origExpression = ct.text(reference);
          }
          ct.delete(qualifier);
        }
        ExpressionUtils.bindReferenceTo(reference, inLambdaName);
      }
      PsiLambdaExpression trueLambda =
        (PsiLambdaExpression)factory.createExpressionFromText("(" + variable.getType().getCanonicalText() + " " +
                                                              inLambdaName + ")->" + ct.text(notNullBranch), ternary);
      PsiParameter lambdaParameter = trueLambda.getParameterList().getParameters()[0];
      PsiExpression trueBody = Objects.requireNonNull((PsiExpression)trueLambda.getBody());
      String ofNullableText = CommonClassNames.JAVA_UTIL_OPTIONAL + ".ofNullable(" + (origExpression == null ? name : origExpression) + ")";
      String replacement = OptionalRefactoringUtil.generateOptionalUnwrap(ofNullableText,
                                                                          lambdaParameter, trueBody, ct.markUnchanged(nullBranch), ternary.getType(),
                                                                          !ExpressionUtils.isSafelyRecomputableExpression(nullBranch));
      PsiElement result = ct.replaceAndRestoreComments(ternary, replacement);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    }
  }

  private static class TernaryNullCheck {
    final PsiVariable myVariable;
    final PsiExpression myNullBranch;
    final PsiExpression myNotNullBranch;

    private TernaryNullCheck(PsiVariable variable, PsiExpression nullBranch, PsiExpression notNullBranch) {
      myVariable = variable;
      myNullBranch = nullBranch;
      myNotNullBranch = notNullBranch;
    }

    @Contract("null -> null")
    @Nullable
    public static TernaryNullCheck from(@Nullable PsiConditionalExpression ternary) {
      if (ternary == null) return null;
      PsiExpression condition = ternary.getCondition();
      boolean isNull = true;
      PsiReferenceExpression ref = ExpressionUtils.getReferenceExpressionFromNullComparison(condition, true);
      if (ref == null) {
        isNull = false;
        ref = ExpressionUtils.getReferenceExpressionFromNullComparison(condition, false);
      }
      if (ref == null) return null;
      PsiVariable variable = tryCast(ref.resolve(), PsiVariable.class);
      if (variable == null || (variable instanceof PsiField && !ExpressionUtil.isEffectivelyUnqualified(ref))) return null;
      PsiExpression nullBranch = isNull ? ternary.getThenExpression() : ternary.getElseExpression();
      PsiExpression notNullBranch = isNull ? ternary.getElseExpression() : ternary.getThenExpression();
      if (nullBranch == null || notNullBranch == null) {
        return null;
      }
      return new TernaryNullCheck(variable, nullBranch, notNullBranch);
    }
  }
}
