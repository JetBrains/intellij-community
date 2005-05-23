/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public interface PsiAnnotationMethod extends PsiMethod {
  PsiAnnotationMethod[] EMPTY_ARRAY = new PsiAnnotationMethod[0];

  @Nullable
  PsiAnnotationMemberValue getDefaultValue();
}
