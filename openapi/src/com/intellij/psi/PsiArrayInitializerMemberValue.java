/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 * @author ven
 */
public interface PsiArrayInitializerMemberValue extends PsiAnnotationMemberValue {
  PsiAnnotationMemberValue[] getInitializers();
}
