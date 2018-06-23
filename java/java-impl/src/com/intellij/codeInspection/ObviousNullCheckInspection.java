// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.ContractReturnValue.ParameterReturnValue;
import com.intellij.codeInspection.dataFlow.ContractValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ObviousNullCheckInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        NullCheckParameter nullCheckParameter = NullCheckParameter.fromCall(call);
        if (nullCheckParameter == null) return;
        if (!(call.getParent() instanceof PsiExpressionStatement || nullCheckParameter.myReturnsParameter)) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length <= nullCheckParameter.myIndex) return;
        PsiExpression nullArg = PsiUtil.skipParenthesizedExprDown(args[nullCheckParameter.myIndex]);
        String explanation = getObviouslyNonNullExplanation(nullArg);
        if (explanation == null) return;
        if(nullCheckParameter.myNull) {
          holder.registerProblem(nullArg, InspectionsBundle.message("inspection.redundant.null.check.always.fail.message", explanation));
        } else {
          holder.registerProblem(nullArg, InspectionsBundle.message("inspection.redundant.null.check.message", explanation),
                                 new RemoveNullCheckFix());
        }
      }
    };
  }

  @Nullable
  private static String getObviouslyNonNullExplanation(PsiExpression arg) {
    if (arg == null || ExpressionUtils.isNullLiteral(arg)) return null;
    if (arg instanceof PsiNewExpression) return "newly created object";
    if (arg instanceof PsiLiteralExpression) return "literal";
    if (arg.getType() instanceof PsiPrimitiveType) return "a value of primitive type";
    if (arg instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)arg).getOperationTokenType() == JavaTokenType.PLUS) {
      return "concatenation";
    }
    if (arg instanceof PsiThisExpression) return "this object";
    return null;
  }

  static class NullCheckParameter {
    int myIndex;
    boolean myNull;
    boolean myReturnsParameter;

    public NullCheckParameter(int index, boolean aNull, boolean returnsParameter) {
      myIndex = index;
      myNull = aNull;
      myReturnsParameter = returnsParameter;
    }

    @Nullable
    static NullCheckParameter fromCall(PsiMethodCallExpression call) {
      PsiMethod method = call.resolveMethod();
      if (method == null) return null;
      if (!JavaMethodContractUtil.isPure(method)) return null;
      List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, call);
      if (contracts.isEmpty() || contracts.size() > 2) return null;

      MethodContract contract = contracts.get(0);
      if (contract == null || !contract.getReturnValue().isFail()) return null;
      ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
      if (condition == null) return null;
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

  public static class RemoveNullCheckFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.null.check.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement startElement = descriptor.getStartElement();
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
        ct.replaceAndRestoreComments(call, ct.markUnchanged(startElement));
      }
    }
  }
}
