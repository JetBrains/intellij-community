// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

final class TypeConversionRuleUtil {
  static List<PsiVariable> getVariablesToMakeFinal(@NotNull PsiExpression expression) {
    final ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getControlFlow(expression, new MyControlFlowPolicy(expression), ControlFlowOptions.NO_CONST_EVALUATE);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }

    Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false);
    if (!writtenVariables.isEmpty()) return null;

    return ContainerUtil
      .filter(ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize()), v -> !v.hasModifierProperty(PsiModifier.FINAL));
  }

  private static class MyControlFlowPolicy implements ControlFlowPolicy {
    private final PsiElement myElement;

    MyControlFlowPolicy(PsiElement element) {myElement = element;}

    @Override
    public PsiVariable getUsedVariable(@NotNull PsiReferenceExpression refExpr) {
      if (refExpr.isQualified()) return null;

      PsiElement refElement = refExpr.resolve();
      if ((refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) &&
          !PsiTreeUtil.isAncestor(myElement, refElement, true)) {
        return (PsiVariable) refElement;
      }

      return null;
    }

    @Override
    public boolean isParameterAccepted(@NotNull PsiParameter psiParameter) {
      return true;
    }

    @Override
    public boolean isLocalVariableAccepted(@NotNull PsiLocalVariable psiVariable) {
      return true;
    }
  }
}
