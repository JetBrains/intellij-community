// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        if (!VariableAccessUtils.variableIsUsed(variable, notNullBranch) ||
            !LambdaGenerationUtil.canBeUncheckedLambda(notNullBranch, variable::equals)) {
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

    public ReplaceConditionWithOptionalFix(boolean changesSemantics) {
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
      String inLambdaName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(name, ternary, true);
      PsiExpression nullBranch = ternaryNullCheck.myNullBranch;
      PsiExpression notNullBranch = ternaryNullCheck.myNotNullBranch;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      for (PsiReference reference : ReferencesSearch.search(variable, new LocalSearchScope(nullBranch)).findAll()) {
        if (reference instanceof PsiReferenceExpression) {
          PsiElement result = ((PsiReferenceExpression)reference).replace(factory.createExpressionFromText("null", ternary));
          if (nullBranch == reference) {
            nullBranch = (PsiExpression)result;
          }
        }
      }
      for (PsiReference reference : ReferencesSearch.search(variable, new LocalSearchScope(notNullBranch)).findAll()) {
        if (reference instanceof PsiReferenceExpression) {
          ExpressionUtils.bindReferenceTo((PsiReferenceExpression)reference, inLambdaName);
        }
      }
      CommentTracker ct = new CommentTracker();
      PsiLambdaExpression trueLambda =
        (PsiLambdaExpression)factory.createExpressionFromText("(" + variable.getType().getCanonicalText() + " " +
                                                              inLambdaName + ")->" + ct.text(notNullBranch), ternary);
      PsiParameter lambdaParameter = trueLambda.getParameterList().getParameters()[0];
      PsiExpression trueBody = (PsiExpression)trueLambda.getBody();
      String replacement = OptionalUtil.generateOptionalUnwrap(CommonClassNames.JAVA_UTIL_OPTIONAL + ".ofNullable(" + name + ")",
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

    public TernaryNullCheck(PsiVariable variable, PsiExpression nullBranch, PsiExpression notNullBranch) {
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
      PsiVariable variable = ExpressionUtils.getVariableFromNullComparison(condition, true);
      if (variable == null) {
        isNull = false;
        variable = ExpressionUtils.getVariableFromNullComparison(condition, false);
      }
      if (variable == null || variable instanceof PsiField) return null;
      PsiExpression nullBranch = isNull ? ternary.getThenExpression() : ternary.getElseExpression();
      PsiExpression notNullBranch = isNull ? ternary.getElseExpression() : ternary.getThenExpression();
      if (nullBranch == null || notNullBranch == null) {
        return null;
      }
      return new TernaryNullCheck(variable, nullBranch, notNullBranch);
    }
  }
}
