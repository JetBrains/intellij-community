// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public final class SuspiciousCollectionsMethodCallsInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean REPORT_CONVERTIBLE_METHOD_CALLS = true;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("REPORT_CONVERTIBLE_METHOD_CALLS", JavaAnalysisBundle.message("report.suspicious.but.possibly.correct.method.calls")));
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final List<SuspiciousMethodCallUtil.PatternMethod> patternMethods = new ArrayList<>();
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression methodCall) {
        final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length < 1) return;
        for (int idx = 0; idx < Math.min(2, args.length); idx ++) {
          String message = getSuspiciousMethodCallMessage(methodCall, REPORT_CONVERTIBLE_METHOD_CALLS, patternMethods, args[idx], idx);
          if (message != null) {
            holder.registerProblem(methodCall.getArgumentList().getExpressions()[idx], message);
          }
        }
      }

      @Override
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
        final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
        final PsiClassType.ClassResolveResult functionalInterfaceResolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
        final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        if (interfaceMethod != null && interfaceMethod.getParameterList().getParametersCount() == 1) {
          final PsiSubstitutor psiSubstitutor = LambdaUtil.getSubstitutor(interfaceMethod, functionalInterfaceResolveResult);
          final MethodSignature signature = interfaceMethod.getSignature(psiSubstitutor);
          String message = SuspiciousMethodCallUtil.getSuspiciousMethodCallMessage(expression, signature.getParameterTypes()[0], REPORT_CONVERTIBLE_METHOD_CALLS, patternMethods, 0);
          if (message != null) {
            holder.registerProblem(ObjectUtils.notNull(expression.getReferenceNameElement(), expression), message);
          }
        }
      }
    };
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "SuspiciousMethodCalls";
  }

  private static @InspectionMessage String getSuspiciousMethodCallMessage(PsiMethodCallExpression methodCall,
                                                                          boolean reportConvertibleMethodCalls,
                                                                          List<SuspiciousMethodCallUtil.PatternMethod> patternMethods,
                                                                          PsiExpression arg,
                                                                          int i) {
    PsiType argType = arg.getType();
    boolean exactType = arg instanceof PsiNewExpression;
    final String plainMessage = SuspiciousMethodCallUtil
      .getSuspiciousMethodCallMessage(methodCall, arg, argType, exactType || reportConvertibleMethodCalls, patternMethods, i);
    if (plainMessage != null && !exactType) {
      String methodName = methodCall.getMethodExpression().getReferenceName();
      if (SuspiciousMethodCallUtil.isCollectionAcceptingMethod(methodName)) {
        // DFA works on raw types, so anyway we cannot narrow the argument type
        return plainMessage;
      }
      TypeConstraint constraint = TypeConstraint.fromDfType(CommonDataflow.getDfType(arg));
      PsiType type = constraint.getPsiType(methodCall.getProject());
      if (type != null && SuspiciousMethodCallUtil.getSuspiciousMethodCallMessage(methodCall, arg, type, reportConvertibleMethodCalls, patternMethods, i) == null) {
        return null;
      }
    }

    return plainMessage;
  }
}
