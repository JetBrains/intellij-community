/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 * @author dsl
 */
public interface PsiEnumConstant extends PsiField, PsiConstructorCall {
  PsiExpressionList getArgumentList();
  PsiEnumConstantInitializer getInitializingClass();
}
