/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiClassInitializer extends PsiMember, PsiModifierListOwner {
  PsiClassInitializer[] EMPTY_ARRAY = new PsiClassInitializer[0];

  PsiCodeBlock getBody();
}
