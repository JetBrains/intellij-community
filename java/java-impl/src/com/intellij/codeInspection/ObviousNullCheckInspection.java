// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Tagir Valeev
 */
public class ObviousNullCheckInspection extends AbstractBaseJavaLocalInspectionTool {
  // Methods which are known to return the null-checked argument,
  // so calling them is useless even if return value is used
  private static final CallMatcher REQUIRE_NON_NULL_METHOD = CallMatcher.anyOf(
    CallMatcher.staticCall("java.util.Objects", "requireNonNull"),
    CallMatcher.staticCall("com.google.common.base.Preconditions", "checkNotNull"));

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        NullCheckParameter nullCheckParameter = NullCheckParameter.fromCall(call);
        if (nullCheckParameter == null) return;
        if (!(call.getParent() instanceof PsiExpressionStatement) && !REQUIRE_NON_NULL_METHOD.test(call)) return;
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

    public NullCheckParameter(int index, boolean aNull) {
      myIndex = index;
      myNull = aNull;
    }

    @Nullable
    static NullCheckParameter fromCall(PsiMethodCallExpression call) {
      PsiMethod method = call.resolveMethod();
      if (method == null) return null;
      if (!ControlFlowAnalyzer.isPure(method)) return null;
      List<? extends MethodContract> contracts = ControlFlowAnalyzer.getMethodCallContracts(method, call);
      if (contracts.size() != 1) return null;
      StandardMethodContract contract = ObjectUtils.tryCast(contracts.get(0), StandardMethodContract.class);
      if (contract == null || contract.getReturnValue() != MethodContract.ValueConstraint.THROW_EXCEPTION) return null;
      MethodContract.ValueConstraint[] arguments = contract.arguments;
      Integer nullIndex = null;
      boolean isNull = false;
      for (int i = 0; i < arguments.length; i++) {
        MethodContract.ValueConstraint argument = arguments[i];
        if (argument == MethodContract.ValueConstraint.NULL_VALUE || argument == MethodContract.ValueConstraint.NOT_NULL_VALUE) {
          if (nullIndex != null) return null;
          nullIndex = i;
          isNull = argument == MethodContract.ValueConstraint.NOT_NULL_VALUE;
        }
        else if (argument != MethodContract.ValueConstraint.ANY_VALUE) {
          return null;
        }
      }
      return nullIndex == null ? null : new NullCheckParameter(nullIndex, isNull);
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
