// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReassignedVariableInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new ReassignedVariableVisitor(holder);
  }

  private class ReassignedVariableVisitor extends JavaElementVisitor {
    private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myLocalVariableProblems = new ConcurrentHashMap<>();
    private final Map<PsiParameter, Boolean> myParameterIsReassigned = new ConcurrentHashMap<>();
    private final @NotNull ProblemsHolder myHolder;

    private ReassignedVariableVisitor(@NotNull ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      checkReassigned(variable);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      myParameterIsReassigned.put(parameter, checkReassigned(parameter));
    }

    private boolean checkReassigned(@NotNull PsiVariable variable) {
      PsiIdentifier nameIdentifier = variable.getNameIdentifier();
      if (nameIdentifier != null &&
          !variable.hasModifierProperty(PsiModifier.FINAL) &&
          ControlFlowUtil.isReassigned(variable, myLocalVariableProblems)) {
        myHolder.registerProblem(nameIdentifier, getReassignedMessage(variable));
        return true;
      }
      return false;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (!myHolder.isOnTheFly()) return;

      PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement != null) {
        PsiElement resolved = expression.resolve();
        if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) &&
            !((PsiVariable)resolved).hasModifierProperty(PsiModifier.FINAL) &&
            !SuppressionUtil.inspectionResultSuppressed(resolved, ReassignedVariableInspection.this)) {
          if (resolved instanceof PsiLocalVariable) {
            if (ControlFlowUtil.isReassigned((PsiVariable)resolved, myLocalVariableProblems)) {
              myHolder.registerProblem(referenceNameElement, getReassignedMessage((PsiVariable)resolved));
            }
          }
          else {
            Boolean isReassigned = myParameterIsReassigned.computeIfAbsent((PsiParameter)resolved, VariableAccessUtils::variableIsAssigned);
            if (isReassigned) {
              myHolder.registerProblem(referenceNameElement, getReassignedMessage((PsiVariable)resolved));
            }
          }
        }
      }
    }

    private static @NotNull String getReassignedMessage(PsiVariable variable) {
      return JavaBundle.message(
        variable instanceof PsiLocalVariable ? "tooltip.reassigned.local.variable" : "tooltip.reassigned.parameter");
    }
  }
}
