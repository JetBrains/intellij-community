/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 * @author ven
 */
public interface PsiCall extends PsiElement {
  PsiExpressionList getArgumentList();
  PsiMethod resolveMethod();
  ResolveResult resolveMethodGenerics();
}
