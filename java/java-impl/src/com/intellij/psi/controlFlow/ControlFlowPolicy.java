package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.Nullable;

public interface ControlFlowPolicy {
  @Nullable
  PsiVariable getUsedVariable(PsiReferenceExpression refExpr);

  boolean isParameterAccepted(PsiParameter psiParameter);
  boolean isLocalVariableAccepted(PsiLocalVariable psiVariable);
}
