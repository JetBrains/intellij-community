/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 6, 2002
 * Time: 6:16:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.controlFlow;

import com.intellij.psi.*;

public class AllVariablesControlFlowPolicy implements ControlFlowPolicy {
  private static final AllVariablesControlFlowPolicy INSTANCE = new AllVariablesControlFlowPolicy();

  public PsiVariable getUsedVariable(PsiReferenceExpression refExpr) {
      PsiElement resolved = refExpr.resolve();
      return resolved instanceof PsiVariable ? (PsiVariable)resolved : null;
  }

  public boolean isParameterAccepted(PsiParameter psiParameter) {
    return true;
  }

  public boolean isLocalVariableAccepted(PsiLocalVariable psiVariable) {
    return true;
  }

  public static AllVariablesControlFlowPolicy getInstance() {
    return INSTANCE;
  }

}
