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
  private static final Key<Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>>> LOCAL_KEY = Key.create("LOCAL_REASSIGNS");
  private static final Key<Map<PsiParameter, Boolean>> PARAMETER_KEY = Key.create("PARAMETER_REASSIGNS");

  @Override
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
    session.putUserData(LOCAL_KEY, null);
    session.putUserData(PARAMETER_KEY, null);
  }

  @Override
  public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {
    session.putUserData(LOCAL_KEY, new HashMap<>());
    session.putUserData(PARAMETER_KEY, new HashMap<>());
  }


  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        doCheck(variable);
      }

      @Override
      public void visitParameter(PsiParameter parameter) {
        session.getUserData(PARAMETER_KEY).put(parameter, doCheck(parameter));
      }

      private boolean doCheck(PsiVariable variable) {
        PsiIdentifier nameIdentifier = variable.getNameIdentifier();
        if (nameIdentifier != null && 
            !variable.hasModifierProperty(PsiModifier.FINAL) && 
            HighlightControlFlowUtil.isReassigned(variable, session.getUserData(LOCAL_KEY))) {
          holder.registerProblem(nameIdentifier, getReassignedMessage(variable));
          return true;
        }
        return false;
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (!holder.isOnTheFly()) return;

        PsiElement referenceNameElement = expression.getReferenceNameElement();
        if (referenceNameElement != null) {
          PsiElement resolve = expression.resolve();
          if (resolve instanceof PsiVariable && !((PsiVariable)resolve).hasModifierProperty(PsiModifier.FINAL)) {
            if (resolve instanceof PsiLocalVariable) {
              if (HighlightControlFlowUtil.isReassigned((PsiVariable)resolve, session.getUserData(LOCAL_KEY))) {
                holder.registerProblem(referenceNameElement, getReassignedMessage((PsiVariable)resolve));
              }
            }
            else if (resolve instanceof PsiParameter) {
              Map<PsiParameter, Boolean> assigned = session.getUserData(PARAMETER_KEY);
              Boolean isAssigned = assigned.get(resolve);
              if (isAssigned == null) {
                isAssigned = HighlightControlFlowUtil.isAssigned((PsiParameter)resolve);
                assigned.put((PsiParameter)resolve, isAssigned);
              }
              if (isAssigned) {
                holder.registerProblem(referenceNameElement, getReassignedMessage((PsiVariable)resolve));
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
    };
  }
}
