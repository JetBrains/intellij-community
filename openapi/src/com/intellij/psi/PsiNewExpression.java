/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public interface PsiNewExpression extends PsiCallExpression, PsiConstructorCall {
  @Nullable
  PsiExpression getQualifier();

  @NotNull
  PsiExpression[] getArrayDimensions();

  @Nullable
  PsiArrayInitializerExpression getArrayInitializer();

  /**
   * @return class reference
   */
  @Nullable
  PsiJavaCodeReferenceElement getClassReference();

  @Nullable
  PsiAnonymousClass getAnonymousClass();
}
