package com.intellij.psi.controlFlow;

import com.intellij.psi.*;

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

  private PsiVariable checkCodeFragment(PsiElement refElement) {
    PsiElement codeFragement;
    if (refElement instanceof PsiParameter
      && ((PsiParameter)refElement).getDeclarationScope() instanceof PsiMethod){
      codeFragement = ((PsiMethod)((PsiParameter)refElement).getDeclarationScope()).getBody();
    }
    else{
      codeFragement = ControlFlowUtil.findCodeFragment(refElement);
    }
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
