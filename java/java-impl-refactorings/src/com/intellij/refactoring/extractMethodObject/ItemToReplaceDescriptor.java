// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface ItemToReplaceDescriptor {
  void replace(@NotNull PsiClass outerClass, @NotNull PsiElementFactory elementFactory, @NotNull PsiMethodCallExpression callExpression);
}
