/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 10, 2007
 * Time: 1:58:39 PM
 */
package com.intellij.lang.injection;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;

public interface MultiHostInjector {
  void getLanguagesToInject(@NotNull PsiElement context, @NotNull MultiHostRegistrar injectionPlacesRegistrar);
}