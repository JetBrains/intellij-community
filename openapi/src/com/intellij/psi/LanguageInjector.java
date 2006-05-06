package com.intellij.psi;

import com.intellij.lang.Language;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface LanguageInjector {
  @Nullable Language getLanguageToInject(PsiLanguageInjectionHost host);
}
