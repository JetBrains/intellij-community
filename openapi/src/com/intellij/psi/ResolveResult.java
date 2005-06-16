/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

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
