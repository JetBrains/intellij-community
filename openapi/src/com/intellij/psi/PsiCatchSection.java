package com.intellij.psi;

/**
 * @author ven
 */
public interface PsiCatchSection extends PsiElement {
  PsiCatchSection[] EMPTY_ARRAY = new PsiCatchSection[0];

  PsiParameter getParameter();
  PsiCodeBlock getCatchBlock();
  PsiType getCatchType();
  PsiTryStatement getTryStatement();
}
