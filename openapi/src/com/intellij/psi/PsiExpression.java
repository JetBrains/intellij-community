/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiExpression extends PsiElement, PsiAnnotationMemberValue {
  PsiExpression[] EMPTY_ARRAY = new PsiExpression[0];
  PsiType getType();
}
