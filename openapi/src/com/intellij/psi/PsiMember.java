/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.navigation.NavigationItem;

public interface PsiMember extends PsiModifierListOwner, NavigationItem {
  PsiMember[] EMPTY_ARRAY = new PsiMember[0];

  PsiClass getContainingClass();
}
