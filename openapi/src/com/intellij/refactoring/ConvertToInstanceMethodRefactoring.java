/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;

/**
 * @author dsl
 */
public interface ConvertToInstanceMethodRefactoring extends Refactoring {
  PsiMethod getMethod();
  PsiParameter getTargetParameter();
  PsiClass getTargetClass();
}

