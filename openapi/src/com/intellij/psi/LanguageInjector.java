package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Describes logic for injecting language inside hosting PSI element.
 * E.g. "inject XPath language into all XML attributes named 'select' that sit inside XML tag prefixed with 'xsl:'".
 * @see com.intellij.psi.PsiLanguageInjectionHost
 * @see com.intellij.psi.PsiManager#registerLanguageInjector(LanguageInjector)
 * @see com.intellij.psi.PsiManager#unregisterLanguageInjector(LanguageInjector)
 */
public interface LanguageInjector {
  /**
   * @param host PSI element inside which your language will be injected.
   * @param placesToInject stores places where injection occurs. <br>
   *        Call its {@link com.intellij.psi.InjectedLanguagePlaces#addPlace(com.intellij.lang.Language, com.intellij.openapi.util.TextRange)}
   *        method to register particular injection place.
   *        For example, to inject your language in string literal inside quotes, you might want to <br>
   *        <code>placesToInject.addPlace(myLanguage, new TextRange(1,host.getTextLength()-1))</code>
   */
  void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces placesToInject);
}
