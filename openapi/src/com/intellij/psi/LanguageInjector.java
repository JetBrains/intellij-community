package com.intellij.psi;

import com.intellij.lang.Language;

/**
 * @author max
 */
public interface LanguageInjector {
  Language getLanguageToInject(PsiLanguageInjectionHost host);
}
