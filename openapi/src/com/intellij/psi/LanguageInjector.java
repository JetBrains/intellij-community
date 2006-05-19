package com.intellij.psi;

import com.intellij.lang.Language;
import org.jetbrains.annotations.Nullable;

/**
 * Describes logic for injecting language inside hosting PSI element.
 * E.g. "inject XSLT language into all XML attributes named 'path' that sit inside XML tag prefixed with 'xsl:'"
 * @see com.intellij.psi.PsiLanguageInjectionHost
 * @see com.intellij.psi.PsiManager#registerLanguageInjector(LanguageInjector)
 * @see com.intellij.psi.PsiManager#unregisterLanguageInjector(LanguageInjector)
 */
public interface LanguageInjector {
  /**
   * @param host PSI element inside which your language will be injected
   * @return language that should be injected in this particular place, or null if there is no any.
   */
  @Nullable Language getLanguageToInject(PsiLanguageInjectionHost host);
}
