/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

/**
 * @author dsl
 */
public interface ReplaceConstructorWithFactoryRefactoring extends Refactoring {
  PsiClass getOriginalClass();
  PsiClass getTargetClass();

  /**
   * @return null if applied to implicit default constructor
   */
  PsiMethod getConstructor();

  String getFactoryName();
}
