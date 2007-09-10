/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 10, 2007
 * Time: 1:58:29 PM
 */
package com.intellij.lang.injection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import com.intellij.lang.Language;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.openapi.util.TextRange;

public interface MultiHostRegistrar {
  @NotNull /*this*/ MultiHostRegistrar startInjecting(@NotNull Language language);
  @NotNull /*this*/ MultiHostRegistrar addPlace(@NonNls @Nullable String prefix, @NonNls @Nullable String suffix, @NotNull PsiLanguageInjectionHost host, @NotNull TextRange rangeInsideHost);
  void doneInjecting();
}