package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * Storage for places where PSI language being injected to.
 * @see com.intellij.psi.LanguageInjector for usage examples.
 */
public interface InjectedLanguagePlaces {
  /**
   * Informs IDEA of the language injected inside the host element.
   * @param language to inject inside the host element.
   * @param rangeInsideHost where to inject the language. Offsets are relative to the host element text range.
   * @param prefix Optional header to be handed on to the language parser before the host element text.
   *        Might be useful e.g. for making the text parsable or providing some context.
   * @param suffix Optional footer to be passed on to the language parser after the host element text.
   *        Might be useful e.g. for making the text parsable or providing some context.
   */
  void addPlace(@NotNull Language language, @NotNull TextRange rangeInsideHost, @NonNls @Nullable String prefix, @NonNls @Nullable String suffix);
}
