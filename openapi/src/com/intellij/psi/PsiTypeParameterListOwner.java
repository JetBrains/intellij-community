/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public interface PsiTypeParameterListOwner extends PsiMember {
  boolean hasTypeParameters();

  @Nullable
  PsiTypeParameterList getTypeParameterList();

  @NotNull
  PsiTypeParameter[] getTypeParameters();
}
