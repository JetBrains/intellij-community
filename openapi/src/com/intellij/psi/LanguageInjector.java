package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * Describes logic for injecting language inside hosting PSI element.
 * E.g. "inject XPath language into all XML attributes named 'select' that sit inside XML tag prefixed with 'xsl:'"
 * @see com.intellij.psi.PsiLanguageInjectionHost
 * @see com.intellij.psi.PsiManager#registerLanguageInjector(LanguageInjector)
 * @see com.intellij.psi.PsiManager#unregisterLanguageInjector(LanguageInjector)
 */
public interface LanguageInjector {
  /**
   * @param host PSI element inside which your language will be injected
   * @return null if this place is not appropriate for injection, or pair of language and TextRange of injected PSI.<br>
   * For example, you might want to <code>return new Pair&lt;Language,TextRange&gt;(myLanguage, new TextRange(1,host.getTextLength()-1))</code>
   * to inject your language in string literal inside quotes.
   */
  @Nullable
  Pair<Language, TextRange> getLanguageToInject(PsiLanguageInjectionHost host);
}
