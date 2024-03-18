// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.ContractReturnValue.ParameterReturnValue;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.jdk.AutoBoxingInspection;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ObviousNullCheckInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        // Avoid method resolve if no argument is a candidate for obvious non-null warning
        // (checking this is easier than resolving and calls without arguments are excluded at all)
        if (!ContainerUtil.exists(args, arg -> TrackingRunner.getObviouslyNonNullExplanation(PsiUtil.skipParenthesizedExprDown(arg)) != null)) return;
        NullCheckParameter nullCheckParameter = NullCheckParameter.fromCall(call);
        if (nullCheckParameter == null) return;
        if (!ExpressionUtils.isVoidContext(call) && !nullCheckParameter.myReturnsParameter) return;
        if (args.length <= nullCheckParameter.myIndex) return;
        PsiExpression nullArg = PsiUtil.skipParenthesizedExprDown(args[nullCheckParameter.myIndex]);
        String explanation = TrackingRunner.getObviouslyNonNullExplanation(nullArg);
        if (explanation == null) return;
        if(nullCheckParameter.myNull) {
          holder.registerProblem(nullArg, JavaBundle.message("inspection.redundant.null.check.always.fail.message", explanation));
        } else {
          PsiReferenceExpression comparedToNull = ExpressionUtils.getReferenceExpressionFromNullComparison(nullArg, false);
          LocalQuickFix fix = comparedToNull == null ? new RemoveNullCheckFix() : new RemoveExcessiveNullComparisonFix();
          holder.registerProblem(nullArg, JavaBundle.message("inspection.redundant.null.check.message", explanation), fix);
        }
      }
    };
  }

  static class NullCheckParameter {
    int myIndex;
    boolean myNull;
    boolean myReturnsParameter;

    NullCheckParameter(int index, boolean aNull, boolean returnsParameter) {
      myIndex = index;
      myNull = aNull;
      myReturnsParameter = returnsParameter;
    }

    @Nullable
    static NullCheckParameter fromCall(PsiMethodCallExpression call) {
      PsiMethod method = call.resolveMethod();
      if (method == null || method.isConstructor()) return null;
      if (!JavaMethodContractUtil.isPure(method)) return null;
      List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, call);
      if (contracts.isEmpty()) return null;
      MethodContract contract = contracts.get(0);
      if (contract == null) return null;
      ContractReturnValue firstReturn = contract.getReturnValue();
      ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
      if (condition == null) return null;
      if (firstReturn instanceof ParameterReturnValue) {
        // first contract is like "!null -> param1"; ignore other contracts
        int index = ((ParameterReturnValue)firstReturn).getParameterNumber();
        int nullIndex = condition.getNullCheckedArgument(false).orElse(-1);
        if (nullIndex != index) return null;
        return new NullCheckParameter(nullIndex, false, true);
      }

      if (contracts.size() > 2) return null;
      if (!firstReturn.isFail()) return null;
      boolean isNull = false;
      int nullIndex = condition.getNullCheckedArgument(true).orElse(-1);
      if (nullIndex == -1) {
        isNull = true;
        nullIndex = condition.getNullCheckedArgument(false).orElse(-1);
        if (nullIndex == -1) return null;
      }

      boolean returnsParameter = false;
      if (contracts.size() == 2) {
        ContractReturnValue returnValue = JavaMethodContractUtil.getNonFailingReturnValue(contracts);
        if (returnValue instanceof ParameterReturnValue && ((ParameterReturnValue)returnValue).getParameterNumber() == nullIndex) {
          returnsParameter = true;
        } else {
          return null;
        }
      }
      return new NullCheckParameter(nullIndex, isNull, returnsParameter);
    }
  }

  public static class RemoveExcessiveNullComparisonFix extends PsiUpdateModCommandQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.redundant.null.check.fix.notnull.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiExpression arg = ObjectUtils.tryCast(element, PsiExpression.class);
      if (arg == null) return;
      PsiReferenceExpression comparedToNull = ExpressionUtils.getReferenceExpressionFromNullComparison(arg, false);
      if (comparedToNull == null) return;
      new CommentTracker().replaceAndRestoreComments(arg, comparedToNull);
    }
  }

  public static class RemoveNullCheckFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.redundant.null.check.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(startElement, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiElement parent = call.getParent();
      CommentTracker ct = new CommentTracker();
      if (parent instanceof PsiExpressionStatement) {
        List<PsiExpression> expressions = StreamEx.of(call.getArgumentList().getExpressions())
          .flatCollection(SideEffectChecker::extractSideEffectExpressions)
          .peek(ct::markUnchanged)
          .toList();
        PsiStatement[] sideEffectStatements = StatementExtractor.generateStatements(expressions, call);
        if(sideEffectStatements.length > 0) {
          BlockUtils.addBefore((PsiStatement)parent, sideEffectStatements);
        }
        ct.deleteAndRestoreComments(parent);
      } else {
        boolean objectCall = call.getType() instanceof PsiClassType;
        PsiExpression result = (PsiExpression)ct.replaceAndRestoreComments(call, startElement);
        if (objectCall && result.getType() instanceof PsiPrimitiveType) {
          PsiElement resultParent = result.getParent();
          if (resultParent instanceof PsiReferenceExpression) {
            AutoBoxingInspection.replaceWithBoxing(result);
          }
        }
      }
    }
  }
}
