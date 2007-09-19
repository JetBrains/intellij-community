/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 10, 2007
 * Time: 1:58:39 PM
 */
package com.intellij.lang.injection;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MultiHostInjector {
  void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context);
  @NotNull
  List<? extends Class<? extends PsiElement>> elementsToInjectIn();
}