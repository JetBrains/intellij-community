/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public interface PsiImportStaticReferenceElement extends PsiJavaCodeReferenceElement {
  PsiJavaCodeReferenceElement getClassReference();
  PsiImportStaticStatement bindToTargetClass(PsiClass aClass) throws IncorrectOperationException;
}
