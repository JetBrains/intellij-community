/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.controlFlow;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalsControlFlowPolicy implements ControlFlowPolicy {
  private final PsiElement myCodeFragment;

  public LocalsControlFlowPolicy(@NotNull PsiElement codeFragment) {
    myCodeFragment = codeFragment;
  }

  @Override
  public PsiVariable getUsedVariable(@NotNull PsiReferenceExpression refExpr) {
    if (refExpr.isQualified()) return null;

    PsiElement refElement = refExpr.resolve();
    return refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter ? checkCodeFragment(refElement) : null;
  }

  @Nullable
  private PsiVariable checkCodeFragment(@NotNull PsiElement refElement) {
    PsiElement codeFragment;
    if (refElement instanceof PsiParameter) {
      final PsiElement declarationScope = ((PsiParameter)refElement).getDeclarationScope();
      if (declarationScope instanceof PsiMethod) {
        codeFragment = ((PsiMethod)declarationScope).getBody();
      }
      else if (declarationScope instanceof PsiLambdaExpression) {
        codeFragment = ((PsiLambdaExpression)declarationScope).getBody();
      }
      else {
        codeFragment = ControlFlowUtil.findCodeFragment(refElement);
      }
    }
    else {
      codeFragment = ControlFlowUtil.findCodeFragment(refElement);
    }
    if (codeFragment == null) return null;
    if (myCodeFragment.getContainingFile() == codeFragment.getContainingFile() &&  // in order for jsp includes to work
        !myCodeFragment.equals(codeFragment) &&
        !(myCodeFragment.getParent() instanceof PsiLambdaExpression && 
          PsiTreeUtil.isAncestor(PsiTreeUtil.getParentOfType(myCodeFragment, PsiClass.class), codeFragment, false))) {
      return null;
    }
    return (PsiVariable)refElement;
  }

  @Override
  public boolean isParameterAccepted(@NotNull PsiParameter psiParameter) {
    return checkCodeFragment(psiParameter) != null;
  }

  @Override
  public boolean isLocalVariableAccepted(@NotNull PsiLocalVariable psiVariable) {
    return checkCodeFragment(psiVariable) != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocalsControlFlowPolicy policy = (LocalsControlFlowPolicy)o;

    return myCodeFragment.equals(policy.myCodeFragment);
  }

  @Override
  public int hashCode() {
    return myCodeFragment.hashCode();
  }
}
