/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.pom.references.PomReferenceProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to register reference providers for specific locations. The locations are described by
 * {@link com.intellij.patterns.ElementPattern}s. If a pattern matches some PSI element, then the corresponding
 * {@link com.intellij.psi.PsiReferenceProvider#getReferencesByElement(PsiElement, com.intellij.util.ProcessingContext)} is executed, from
 * which one can return the references whose {@link PsiReference#getElement()} is the same as the first parameter of
 * {@link com.intellij.psi.PsiReferenceProvider#getReferencesByElement(PsiElement, com.intellij.util.ProcessingContext)}.
 *
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
