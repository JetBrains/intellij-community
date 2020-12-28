// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection.general;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Represents information about injected language. Usually is used as the result of the
 * {@link LanguageInjectionContributor#getInjection(PsiElement)} call and corresponds to the given {@code PsiElement}
 */
public interface Injection {

  @NotNull
  @NlsSafe
  String getInjectedLanguageId();

  /**
   * @return a the code of the injected language, which is "implied" be a prefix of the code in the host literal
   *
   * For instance for the code "{@code String div = "<div>some html</div>";}" the prefix could be "{@code <html><body>}"
   */
  @NotNull
  String getPrefix();

  /**
   * @return a the code of the injected language, which is "implied" be a suffix of the code in the host literal
   *
   * For instance for the code "{@code String div = "<div>some html</div>";}" the prefix could be "{@code </body></html>}"
   */
  @NotNull
  String getSuffix();

}

