// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.dataFlow.ContractReturnValue.ParameterReturnValue;
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Tagir Valeev
 */
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
          holder.registerProblem(nullArg, InspectionsBundle.message("inspection.useless.null.check.always.fail.message", explanation));
        } else {
          holder.registerProblem(nullArg, InspectionsBundle.message("inspection.useless.null.check.message", explanation),
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
      if (!ControlFlowAnalyzer.isPure(method)) return null;
      List<? extends MethodContract> contracts = ControlFlowAnalyzer.getMethodCallContracts(method, call);
      if (contracts.isEmpty() || contracts.size() > 2) return null;
      StandardMethodContract contract = ObjectUtils.tryCast(contracts.get(0), StandardMethodContract.class);
      if (contract == null || !contract.getReturnValue().isFail()) return null;
      Integer nullIndex = null;
      boolean isNull = false;
      for (int i = 0; i < contract.getParameterCount(); i++) {
        ValueConstraint argument = contract.getParameterConstraint(i);
        if (argument == ValueConstraint.NULL_VALUE || argument == ValueConstraint.NOT_NULL_VALUE) {
          if (nullIndex != null) return null;
          nullIndex = i;
          isNull = argument == ValueConstraint.NOT_NULL_VALUE;
        }
        else if (argument != ValueConstraint.ANY_VALUE) {
          return null;
        }
      }
      if (nullIndex == null) return null;
      boolean returnsParameter = false;
      if (contracts.size() == 2) {
        MethodContract secondContract = contracts.get(1);
        if (!secondContract.isTrivial()) return null;
        ParameterReturnValue value = ObjectUtils.tryCast(secondContract.getReturnValue(), ParameterReturnValue.class);
        if (value == null || value.getParameterNumber() != nullIndex) return null;
        returnsParameter = true;
      }
      return new NullCheckParameter(nullIndex, isNull, returnsParameter);
    }
  }

  public static class RemoveNullCheckFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.useless.null.check.fix.family.name");
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
