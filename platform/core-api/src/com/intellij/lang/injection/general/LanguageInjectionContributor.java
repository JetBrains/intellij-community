// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection.general;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point to provide the injection information for the given context in terms <i>what</i> to inject.
 * Could be implemented by Language Plugins or Framework/Libraries Plugins to provide some context-specific injections,
 * which cannot be done via <a href="https://www.jetbrains.com/help/idea/language-injections-settings.html">IntelliLang.xml</a>
 * (which is actually handled by {@link org.intellij.plugins.intelliLang.inject.DefaultLanguageInjector DefaultLanguageInjector}
 * implementation of this interface)
 *
 * @see com.intellij.lang.injection.MultiHostInjector
 * @see LanguageInjectionPerformer
 */
public interface LanguageInjectionContributor {

  /**
   * @return {@link Injection} which should present in the {@code context},
   * or {@code null} if no injection to the given {@code context} could be provided by the current contributor
   * @param context a {@link PsiElement}, which could contain the {@link Injection} of other language.
   *               Usually implementations should handle only {@link com.intellij.psi.PsiLanguageInjectionHost}-s,
   *               when more complex cases (like the concatenation injection) should be handled via the corresponding
   *               {@link LanguageInjectionPerformer}
   */
  @Nullable
  Injection getInjection(@NotNull PsiElement context);

  ExtensionPointName<KeyedLazyInstance<LanguageInjectionContributor>> EP_NAME =
    ExtensionPointName.create("com.intellij.languageInjectionContributor");

  LanguageExtension<LanguageInjectionContributor> INJECTOR_EXTENSION = new LanguageExtension<>(EP_NAME);

}
