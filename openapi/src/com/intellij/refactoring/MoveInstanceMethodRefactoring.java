/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;

/**
 * @author ven
 */
public interface MoveInstanceMethodRefactoring extends Refactoring {
  PsiMethod getMethod();
  PsiVariable getTargetVariable();
  PsiClass getTargetClass();
}

