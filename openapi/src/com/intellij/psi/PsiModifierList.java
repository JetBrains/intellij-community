/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiModifierList extends PsiElement {
  boolean hasModifierProperty(@NotNull String name);
  boolean hasExplicitModifier(@NotNull String name);
  void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException;
  void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException;

  @NotNull PsiAnnotation[] getAnnotations();
  @Nullable PsiAnnotation findAnnotation(@NotNull String qualifiedName);
}
