/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

public class LocalsControlFlowPolicy implements ControlFlowPolicy {
  private final PsiElement myCodeFragment;

  public LocalsControlFlowPolicy(PsiElement codeFragment) {
    myCodeFragment = codeFragment;
  }

  public PsiVariable getUsedVariable(PsiReferenceExpression refExpr) {
    if (refExpr.isQualified()) return null;

    PsiElement refElement = refExpr.resolve();
    if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter){
      return checkCodeFragment(refElement);
    }
    else{
      return null;
    }
  }

  @Nullable
  private PsiVariable checkCodeFragment(PsiElement refElement) {
    PsiElement codeFragement;
    if (refElement instanceof PsiParameter
      && ((PsiParameter)refElement).getDeclarationScope() instanceof PsiMethod){
      codeFragement = ((PsiMethod)((PsiParameter)refElement).getDeclarationScope()).getBody();
    }
    else{
      codeFragement = ControlFlowUtil.findCodeFragment(refElement);
    }
    if (codeFragement == null) return null;
    if (myCodeFragment.getContainingFile() == codeFragement.getContainingFile() && //In order for jsp includes to work
        !myCodeFragment.equals(codeFragement)) return null;
    return (PsiVariable)refElement;
  }

  public boolean isParameterAccepted(PsiParameter psiParameter) {
    return checkCodeFragment(psiParameter) != null;
  }

  public boolean isLocalVariableAccepted(PsiLocalVariable psiVariable) {
    return checkCodeFragment(psiVariable) != null;
  }
}
