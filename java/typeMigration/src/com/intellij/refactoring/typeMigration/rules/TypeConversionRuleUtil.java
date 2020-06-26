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
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

class TypeConversionRuleUtil {
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
