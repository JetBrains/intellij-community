/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiNewExpression extends PsiCallExpression, PsiConstructorCall {
  PsiExpression getQualifier();

  PsiExpression[] getArrayDimensions();

  PsiArrayInitializerExpression getArrayInitializer();

  /**
   * @return class reference, or null, if it is a 'new' of an anonymous class
   */
  PsiJavaCodeReferenceElement getClassReference();

  PsiAnonymousClass getAnonymousClass();
}
