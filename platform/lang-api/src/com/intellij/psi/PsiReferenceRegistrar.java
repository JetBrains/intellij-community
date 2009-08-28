/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.pom.references.PomReferenceProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class PsiReferenceRegistrar {
  public static final double DEFAULT_PRIORITY = 0.0;
  public static final double HIGHER_PRIORITY = 100.0;
  public static final double LOWER_PRIORITY = -100.0;

  /**
   * Register reference provider with default priority ({@link #DEFAULT_PRIORITY})
   * @param pattern reference place description. See {@link StandardPatterns}, {@link PlatformPatterns} and their extenders
   * @param provider
   */
  public void registerReferenceProvider(@NotNull ElementPattern<? extends PsiElement> pattern, @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(pattern, provider, DEFAULT_PRIORITY);
  }


  /**
   * Register reference provider
   * @param pattern reference place description. See {@link StandardPatterns}, {@link PlatformPatterns} and their extenders
   * @param provider
   * @param priority @see DEFAULT_PRIORITY, HIGHER_PRIORITY, LOWER_PRIORITY
   */
  public abstract <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern, @NotNull PsiReferenceProvider provider, double priority);

  public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern, @NotNull PomReferenceProvider<T> provider) {
    registerReferenceProvider(pattern, provider, DEFAULT_PRIORITY);
  }

  public abstract <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern, @NotNull PomReferenceProvider<T> provider, double priority);

  /**
   * Return the project to register providers for.
   * @return project or null, if POM references are registered
   */
  @Deprecated
  public abstract Project getProject();
}
