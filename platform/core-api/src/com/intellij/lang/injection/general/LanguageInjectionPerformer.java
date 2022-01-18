// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection.general;

import com.intellij.lang.LanguageExtension;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point, which provides <i>how</i> injection should be done to the particular host language.
 * Should be implemented by language-plugins to support complex language-injections like injection into concatenation or interpolation.
 *
 * <p>If it is not implemented then the {@link org.intellij.plugins.intelliLang.inject.DefaultLanguageInjectionPerformer DefaultLanguageInjectionPerformer}
 * will be used</p>
 */
public interface LanguageInjectionPerformer {

  /**
   * Determinate if this is a default {@link LanguageInjectionPerformer} for current language, and it handles most of {@link Injection}-s.
   * If there were no <b>primary</b> {@link LanguageInjectionPerformer} found for the language then a fallback injection will be performed.
   *
   * @return <code>true</code> if it is an dedicated injector for current language,
   * and <code>false</code> if it handles only specific cases
   */
  boolean isPrimary();

  /**
   * Performs the injection into the {@code context} {@link PsiElement} and/or some elements around it if needed
   * in case if they are semantically connected (concatenation injection for instance).
   *
   * @param registrar a consumer of injection
   * @param injection <i>what</i> should be injected
   * @param context   <i>where</i> to inject. Implementations are free inject to nearby  {@link PsiElement}-s if needed
   * @return {@code true} if injection was succeeded, {@code false} if the current implementation isn't able to hande the injection
   * and other implementation could be tried.
   *
   * @see com.intellij.lang.injection.MultiHostInjector
   */
  boolean performInjection(@NotNull MultiHostRegistrar registrar, @NotNull Injection injection, @NotNull PsiElement context);

  ExtensionPointName<KeyedLazyInstance<LanguageInjectionPerformer>> EP_NAME =
    ExtensionPointName.create("com.intellij.languageInjectionPerformer");

  LanguageExtension<LanguageInjectionPerformer> INJECTOR_EXTENSION = new LanguageExtension<>(EP_NAME);
}
