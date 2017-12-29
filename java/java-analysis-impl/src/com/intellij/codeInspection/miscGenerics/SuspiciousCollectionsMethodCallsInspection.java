// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class SuspiciousCollectionsMethodCallsInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean REPORT_CONVERTIBLE_METHOD_CALLS = true;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("report.suspicious.but.possibly.correct.method.calls"), this, "REPORT_CONVERTIBLE_METHOD_CALLS");
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final List<PsiMethod> patternMethods = new ArrayList<>();
    final IntArrayList indices = new IntArrayList();
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        final String message = getSuspiciousMethodCallMessage(methodCall, REPORT_CONVERTIBLE_METHOD_CALLS, patternMethods, indices);
        if (message != null) {
          holder.registerProblem(methodCall.getArgumentList().getExpressions()[0], message);
        }
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
        final PsiClassType.ClassResolveResult functionalInterfaceResolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
        final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        if (interfaceMethod != null && interfaceMethod.getParameterList().getParametersCount() == 1) {
          final PsiSubstitutor psiSubstitutor = LambdaUtil.getSubstitutor(interfaceMethod, functionalInterfaceResolveResult);
          final MethodSignature signature = interfaceMethod.getSignature(psiSubstitutor);
          String message = SuspiciousMethodCallUtil.getSuspiciousMethodCallMessage(expression, signature.getParameterTypes()[0], REPORT_CONVERTIBLE_METHOD_CALLS, patternMethods, indices);
          if (message != null) {
            holder.registerProblem(ObjectUtils.notNull(expression.getReferenceNameElement(), expression), message);
          }
        }
      }
    };
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.suspicious.collections.method.calls.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "SuspiciousMethodCalls";
  }

  @Nullable
  private static String getSuspiciousMethodCallMessage(final PsiMethodCallExpression methodCall,
                                                       final boolean reportConvertibleMethodCalls, final List<PsiMethod> patternMethods,
                                                       final IntArrayList indices) {
    final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
    if (args.length < 1) return null;

    PsiType argType = args[0].getType();
    boolean exactType = args[0] instanceof PsiNewExpression;
    final String plainMessage = SuspiciousMethodCallUtil
      .getSuspiciousMethodCallMessage(methodCall, args[0], argType, exactType || reportConvertibleMethodCalls, patternMethods, indices);
    if (plainMessage != null && !exactType) {
      TypeConstraint constraint = CommonDataflow.getExpressionFact(args[0], DfaFactType.TYPE_CONSTRAINT);
      if (constraint != null) {
        PsiType type = constraint.getPsiType();
        if (type != null && SuspiciousMethodCallUtil
              .getSuspiciousMethodCallMessage(methodCall, args[0], type, reportConvertibleMethodCalls, patternMethods, indices) == null) {
          return null;
        }
      }
    }

    return plainMessage;
  }
}
