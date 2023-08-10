// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection.general;

import com.intellij.lang.Language;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents information about injected language. Usually is used as the result of the
 * {@link LanguageInjectionContributor#getInjection(PsiElement)} call and corresponds to the given {@code PsiElement}
 */
public interface Injection {
  @NotNull
  @NlsSafe
  String getInjectedLanguageId();

  @Nullable
  Language getInjectedLanguage();

  /**
   * @return a string (in the injected language), which is prepended to the code in the host literal to form a parseable code fragment.
   * For instance, having the code {@code String div = "<div>some html</div>";} we could inject HTML language there
   * with the prefix = {@code "<html><body>"} and the suffix = {@code "</body></html>"}
   * to form correct HTML fragment:  {@code <html><body><div>some html</div></body></html>}
   * @see #getSuffix()
   */
  @NotNull
  String getPrefix();

  /**
   * @return a string (in the injected language), which is appended to the code in the host literal to form a parseable code fragment.
   * @see #getPrefix()
   */
  @NotNull
  String getSuffix();

  /**
   * @return the ID of a tool which will provide UI utils to manage this injection.
   * <p>
   * If the {@code LanguageInjectionSupport} with such ID is registered it will be used
   * if {@link LanguageInjectionPerformer} will not force other implementation
   */
  @Nullable
  @NlsSafe String getSupportId();
}
