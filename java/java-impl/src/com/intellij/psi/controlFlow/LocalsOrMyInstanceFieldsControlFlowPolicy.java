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

public class LocalsOrMyInstanceFieldsControlFlowPolicy implements ControlFlowPolicy {
  private static final LocalsOrMyInstanceFieldsControlFlowPolicy INSTANCE = new LocalsOrMyInstanceFieldsControlFlowPolicy();

  private LocalsOrMyInstanceFieldsControlFlowPolicy() {
  }

  public PsiVariable getUsedVariable(PsiReferenceExpression refExpr) {
    PsiExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null || qualifier instanceof PsiThisExpression) {
      PsiElement resolved = refExpr.resolve();
      if (!(resolved instanceof PsiVariable)) return null;
      return (PsiVariable)resolved;
    }

    return null;
  }

  public boolean isParameterAccepted(PsiParameter psiParameter) {
    return true;
  }

  public boolean isLocalVariableAccepted(PsiLocalVariable psiVariable) {
    return true;
  }

  public static LocalsOrMyInstanceFieldsControlFlowPolicy getInstance() {
    return INSTANCE;
  }
}
