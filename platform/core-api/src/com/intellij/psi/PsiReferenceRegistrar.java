// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.patterns.ElementPattern;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to register reference providers for specific locations.
 * <p>
 * The locations are described by {@link ElementPattern}s. If a pattern matches some PSI element, then the corresponding
 * {@link PsiReferenceProvider#getReferencesByElement(PsiElement, com.intellij.util.ProcessingContext)} is executed, from
 * which one can return the references whose {@link PsiReference#getElement()} is the same as the first parameter of
 * {@link PsiReferenceProvider#getReferencesByElement(PsiElement, com.intellij.util.ProcessingContext)}.
 *
 * @author peter
 */
public abstract class PsiReferenceRegistrar {

  public static final double DEFAULT_PRIORITY = 0.0;
  public static final double HIGHER_PRIORITY = 100.0;
  public static final double LOWER_PRIORITY = -100.0;

  /**
   * Register reference provider with default priority ({@link #DEFAULT_PRIORITY}).
   *
   * @param pattern  reference place description. See {@link com.intellij.patterns.StandardPatterns}, {@link com.intellij.patterns.PlatformPatterns} and their extenders.
   * @param provider provider to be registered
   */
  public void registerReferenceProvider(@NotNull ElementPattern<? extends PsiElement> pattern, @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(pattern, provider, DEFAULT_PRIORITY);
  }

  /**
   * Register reference provider with custom priority.
   *
   * @param pattern  reference place description. See {@link com.intellij.patterns.StandardPatterns}, {@link com.intellij.patterns.PlatformPatterns} and their extenders.
   * @param provider provider to be registered
   * @param priority see {@link #DEFAULT_PRIORITY), {@link #HIGHER_PRIORITY}, {@link #LOWER_PRIORITY}
   */
  public abstract <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern,
                                                                        @NotNull PsiReferenceProvider provider,
                                                                        double priority);
}
