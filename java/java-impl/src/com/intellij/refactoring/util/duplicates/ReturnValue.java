/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author dsl
 */
public interface ReturnValue {
  boolean isEquivalent(ReturnValue other);

  @Nullable
  PsiStatement createReplacement(final PsiMethod extractedMethod, PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException;
}
