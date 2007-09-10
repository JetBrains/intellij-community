/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 10, 2007
 * Time: 1:58:08 PM
 */
package com.intellij.lang.injection;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;

public interface ConcatenationAwareInjector {
  void getLanguagesToInject(@NotNull MultiHostRegistrar injectionPlacesRegistrar, @NotNull PsiElement... operands);
}