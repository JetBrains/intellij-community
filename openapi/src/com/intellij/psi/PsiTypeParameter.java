/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 *  @author dsl
 */
public interface PsiTypeParameter extends PsiClass {
  PsiTypeParameter[] EMPTY_ARRAY = new PsiTypeParameter[0];

  PsiTypeParameterListOwner getOwner ();
  int getIndex();
}
