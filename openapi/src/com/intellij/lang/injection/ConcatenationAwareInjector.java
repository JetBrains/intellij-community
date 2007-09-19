/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 10, 2007
 * Time: 1:58:08 PM
 */
package com.intellij.lang.injection;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface ConcatenationAwareInjector {
  void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement... operands);
}