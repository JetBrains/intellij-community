/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.psi.PsiElement;

public interface PsiMatcher {
  PsiMatcher parent(PsiMatcherExpression e);
  PsiMatcher firstChild(PsiMatcherExpression e);
  PsiMatcher ancestor(PsiMatcherExpression e);
  PsiMatcher descendant(PsiMatcherExpression e);
  PsiMatcher dot(PsiMatcherExpression e);

  PsiElement getElement();
}
