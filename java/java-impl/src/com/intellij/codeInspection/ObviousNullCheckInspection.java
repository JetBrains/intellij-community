/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.siyeh.ig.psiutils.BlockUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Tagir Valeev
 */
public class ObviousNullCheckInspection extends BaseJavaBatchLocalInspectionTool {
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
        Integer nullIndex = getNullParameterIndex(call);
        if (nullIndex == null) return;
        if (!(call.getParent() instanceof PsiExpressionStatement) && !REQUIRE_NON_NULL_METHOD.test(call)) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length <= nullIndex) return;
        PsiExpression nullArg = PsiUtil.skipParenthesizedExprDown(args[nullIndex]);
        String explanation = getObviouslyNonNullExplanation(nullArg);
        if (explanation == null) return;
        holder.registerProblem(nullArg, InspectionsBundle.message("inspection.useless.null.check.message", explanation),
                               new RemoveNullCheckFix());
      }
    };
  }

  @Nullable
  private static String getObviouslyNonNullExplanation(PsiExpression arg) {
    if (arg == null) return null;
    if (arg instanceof PsiNewExpression) return "newly created object";
    if (arg instanceof PsiLiteralExpression && !ExpressionUtils.isNullLiteral(arg)) return "literal";
    if (arg.getType() instanceof PsiPrimitiveType) return "a value of primitive type";
    if (arg instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)arg).getOperationTokenType() == JavaTokenType.PLUS) {
      return "concatenation";
    }
    return null;
  }

  @Nullable
  private static Integer getNullParameterIndex(PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    if (!ControlFlowAnalyzer.isPure(method)) return null;
    List<? extends MethodContract> contracts = ControlFlowAnalyzer.getMethodCallContracts(method, call);
    if (contracts.size() != 1) return null;
    StandardMethodContract contract = ObjectUtils.tryCast(contracts.get(0), StandardMethodContract.class);
    if (contract == null || contract.getReturnValue() != MethodContract.ValueConstraint.THROW_EXCEPTION) return null;
    MethodContract.ValueConstraint[] arguments = contract.arguments;
    Integer nullIndex = null;
    for (int i = 0; i < arguments.length; i++) {
      MethodContract.ValueConstraint argument = arguments[i];
      if (argument == MethodContract.ValueConstraint.NULL_VALUE) {
        if (nullIndex != null) return null;
        nullIndex = i;
      }
      else if (argument != MethodContract.ValueConstraint.ANY_VALUE) {
        return null;
      }
    }
    return nullIndex;
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
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiStatement[] sideEffectStatements = StreamEx.of(call.getArgumentList().getExpressions())
          .flatCollection(SideEffectChecker::extractSideEffectExpressions)
          .map(expr -> factory.createStatementFromText(ct.text(expr) + ";", call))
          .toArray(PsiStatement[]::new);
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
