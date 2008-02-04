/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import com.intellij.patterns.ElementPattern;

/**
 * @author peter
 */
public interface PsiReferenceRegistrar {
  double DEFAULT_PRIORITY = 0.0;
  double HIGHER_PRIORITY = 100.0;
  double LOWER_PRIORITY = -100.0;

  void registerReferenceProvider(@NotNull ElementPattern<? extends PsiElement> pattern, @NotNull PsiReferenceProvider provider);

  <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern, @NotNull PsiReferenceProvider provider, double priority);
  
}
