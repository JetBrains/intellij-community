/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public interface PsiCall extends PsiElement {
  PsiExpressionList getArgumentList();
  @Nullable PsiMethod resolveMethod();
  ResolveResult resolveMethodGenerics();
}
