package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * Storage for places where PSI language being injected to.
 * @see com.intellij.psi.LanguageInjector for usage examples.
 */
public interface InjectedLanguagePlaces {
  void addPlace(@NotNull Language language, @NotNull TextRange rangeInsideHost);
}
