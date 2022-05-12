// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ReassignedVariableInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Key<ReassignedVariableVisitor> KEY = Key.create("REASSIGNED_VARIABLE_VISITOR");
  @Override
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
    ReassignedVariableVisitor visitor = session.getUserData(KEY);
    if (visitor != null) {
      visitor.clear();
      session.putUserData(KEY, null);
    }
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    ReassignedVariableVisitor visitor = new ReassignedVariableVisitor(holder);
    session.putUserData(KEY, visitor);
    return visitor;
  }

  private class ReassignedVariableVisitor extends JavaElementVisitor {
    private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myLocals = new HashMap<>();
    private final Map<PsiParameter, Boolean> myParameters = new HashMap<>();
    private final @NotNull ProblemsHolder myHolder;

    private ReassignedVariableVisitor(@NotNull ProblemsHolder holder) {
      myHolder = holder;
    }

    void clear() {
      myLocals.clear();
      myParameters.clear();
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      doCheck(variable);
    }

    @Override
    public void visitParameter(PsiParameter parameter) {
      myParameters.put(parameter, doCheck(parameter));
    }

    private boolean doCheck(PsiVariable variable) {
      PsiIdentifier nameIdentifier = variable.getNameIdentifier();
      if (nameIdentifier != null &&
          !variable.hasModifierProperty(PsiModifier.FINAL) &&
          HighlightControlFlowUtil.isReassigned(variable, myLocals)) {
        myHolder.registerProblem(nameIdentifier, getReassignedMessage(variable));
        return true;
      }
      return false;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (!myHolder.isOnTheFly()) return;

      PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement != null) {
        PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiVariable && 
            !((PsiVariable)resolve).hasModifierProperty(PsiModifier.FINAL) &&
            !SuppressionUtil.inspectionResultSuppressed(resolve, ReassignedVariableInspection.this)) {
          if (resolve instanceof PsiLocalVariable) {
            if (HighlightControlFlowUtil.isReassigned((PsiVariable)resolve, myLocals)) {
              myHolder.registerProblem(referenceNameElement, getReassignedMessage((PsiVariable)resolve));
            }
          }
          else if (resolve instanceof PsiParameter) {
            Boolean isAssigned = myParameters.get(resolve);
            if (isAssigned == null) {
              isAssigned = HighlightControlFlowUtil.isAssigned((PsiParameter)resolve);
              myParameters.put((PsiParameter)resolve, isAssigned);
            }
            if (isAssigned) {
              myHolder.registerProblem(referenceNameElement, getReassignedMessage((PsiVariable)resolve));
            }
          }
        }
      }
    }

    @NotNull
    private String getReassignedMessage(PsiVariable variable) {
      return JavaBundle.message(
        variable instanceof PsiLocalVariable ? "tooltip.reassigned.local.variable" : "tooltip.reassigned.parameter");
    }
  }
}
