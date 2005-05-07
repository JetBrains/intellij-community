/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public interface PsiTypeParameter extends PsiClass {
  PsiTypeParameter[] EMPTY_ARRAY = new PsiTypeParameter[0];

  @NotNull(documentation = "for this particular kind of classes it never returns null")
  PsiReferenceList getExtendsList();

  @NotNull
  PsiTypeParameterListOwner getOwner ();

  int getIndex();
}
