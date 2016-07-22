/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class SuspiciousCollectionsMethodCallsInspection extends BaseJavaBatchLocalInspectionTool {
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
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        super.visitMethodCallExpression(methodCall);
        final String message = getSuspiciousMethodCallMessage(methodCall, REPORT_CONVERTIBLE_METHOD_CALLS, patternMethods, indices
        );
        if (message != null) {
          holder.registerProblem(methodCall.getArgumentList().getExpressions()[0], message);
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
    if (args.length != 1) return null;

    PsiType argType = args[0].getType();
    final String plainMessage = SuspiciousMethodCallUtil
      .getSuspiciousMethodCallMessage(methodCall, args[0], argType, reportConvertibleMethodCalls, patternMethods, indices);
    if (plainMessage != null) {
      final PsiType dfaType = GuessManager.getInstance(methodCall.getProject()).getControlFlowExpressionType(args[0]);
      if (dfaType != null && SuspiciousMethodCallUtil
                               .getSuspiciousMethodCallMessage(methodCall, args[0], dfaType, reportConvertibleMethodCalls, patternMethods, indices) == null) {
        return null;
      }
    }

    return plainMessage;
  }
}
