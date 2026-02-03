// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.patterns.ElementPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows registering reference providers for specific locations.
 * <p>
 * The locations are described by {@link ElementPattern}s. If a pattern matches some PSI element, then the corresponding
 * {@link PsiReferenceProvider#getReferencesByElement(PsiElement, com.intellij.util.ProcessingContext)} is executed, from
 * which one can return the references whose {@link PsiReference#getElement()} is the same as the first parameter of
 * {@link PsiReferenceProvider#getReferencesByElement(PsiElement, com.intellij.util.ProcessingContext)}.
 */
public abstract class PsiReferenceRegistrar implements UserDataHolder {

  public static final double DEFAULT_PRIORITY = 0.0;
  public static final double HIGHER_PRIORITY = 100.0;
  public static final double LOWER_PRIORITY = -100.0;

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

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

  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserDataHolder.putUserData(key, value);
  }
}
