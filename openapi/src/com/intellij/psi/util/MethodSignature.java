/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;

/** 
 * @author cdr
 */

public interface MethodSignature {
  MethodSignature[] EMPTY_ARRAY = new MethodSignature[0];
  PsiSubstitutor getSubstitutor();
  String getName();
  /**
   * already substituted
   */
  PsiType[] getParameterTypes();
  PsiTypeParameter[] getTypeParameters();
  boolean isRaw();
}
