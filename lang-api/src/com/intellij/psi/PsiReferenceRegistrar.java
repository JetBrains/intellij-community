/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface PsiReferenceRegistrar {
  double DEFAULT_PRIORITY = 0.0;
  double HIGHER_PRIORITY = 100.0;
  double LOWER_PRIORITY = -100.0;

  /**
   * Register reference provider with default priority ({@link #DEFAULT_PRIORITY})
   * @param pattern reference place description. See {@link StandardPatterns}, {@link PlatformPatterns} and their extenders
   * @param provider
   */
  void registerReferenceProvider(@NotNull ElementPattern<? extends PsiElement> pattern, @NotNull PsiReferenceProvider provider);

  /**
   * Register reference provider
   * @param pattern reference place description. See {@link StandardPatterns}, {@link PlatformPatterns} and their extenders
   * @param provider
   * @param priority @see DEFAULT_PRIORITY, HIGHER_PRIORITY, LOWER_PRIORITY
   */
  <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern, @NotNull PsiReferenceProvider provider, double priority);

  /**
   * Return the project to register providers for.
   * @return project
   */
  Project getProject();
}
