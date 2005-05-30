package com.intellij.psi;

/**
 * @author ven
 */
public interface ResolveResult {
  ResolveResult[] EMPTY_ARRAY = new ResolveResult[0];
  
  /**
   * @return an element reference is resolved to.
   */
  PsiElement getElement();

  /**
   *
   * @return true if the resolve encountered no problems
   */
  boolean isValidResult();
}
