// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.ex.PairedUnfairLocalInspectionTool;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class ReassignedVariableInspection extends AbstractBaseJavaLocalInspectionTool implements PairedUnfairLocalInspectionTool {
  public static final String SHORT_NAME = getShortName(ReassignedVariableInspection.class.getSimpleName());
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (isOnTheFly) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        doCheck(variable);
      }

      @Override
      public void visitParameter(PsiParameter parameter) {
        doCheck(parameter);
      }

      private void doCheck(PsiVariable variable) {
        if (!variable.hasModifierProperty(PsiModifier.FINAL) && HighlightControlFlowUtil.isReassigned(variable, new HashMap<>())) {
          PsiIdentifier nameIdentifier = variable.getNameIdentifier();
          if (nameIdentifier != null) {
            String message = JavaBundle.message(
              variable instanceof PsiLocalVariable ? "tooltip.reassigned.local.variable" : "tooltip.reassigned.parameter");
            holder.registerProblem(nameIdentifier, message);
          }
        }
      }
    };
  }

  @Override
  public @NotNull String getInspectionForBatchShortName() {
    return SHORT_NAME;
  }
}
