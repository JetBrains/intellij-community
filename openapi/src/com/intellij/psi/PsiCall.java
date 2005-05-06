/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public interface PsiCall extends PsiElement {
  @Nullable PsiExpressionList getArgumentList();

  @Nullable PsiMethod resolveMethod();

  @NotNull(documentation = "Returns ResolveResult.EMPTY if unresolved")
  ResolveResult resolveMethodGenerics();
}
