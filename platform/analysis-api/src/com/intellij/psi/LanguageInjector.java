// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * Describes logic for injecting language inside a hosting PSI element.
 * E.g., inject XPath language into all XML attributes named 'select' that sit inside XML tag prefixed with 'xsl:'.
 *
 * @see PsiLanguageInjectionHost
 * @see com.intellij.lang.injection.MultiHostInjector
 */
@FunctionalInterface
public interface LanguageInjector extends PossiblyDumbAware {
  ExtensionPointName<LanguageInjector> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.languageInjector");

  /**
   * @param host                     PSI element inside which your language will be injected.
   * @param injectionPlacesRegistrar stores places where injection occurs. <br>
   *                                 Call its {@link InjectedLanguagePlaces#addPlace(com.intellij.lang.Language, com.intellij.openapi.util.TextRange, String, String)}
   *                                 method to register particular injection place.
   *                                 For example, to inject your language in string literal inside quotes, you might want to <br>
   *                                 {@code injectionPlacesRegistrar.addPlace(myLanguage, new TextRange(1,host.getTextLength()-1))}
   */
  void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar);
}
