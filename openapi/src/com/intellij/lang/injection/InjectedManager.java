/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jun 8, 2007
 * Time: 8:41:25 PM
 */
package com.intellij.lang.injection;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiElement;

public abstract class InjectedManager {
  public static InjectedManager getInstance() {
    return ApplicationManager.getApplication().getComponent(InjectedManager.class);
  }

  public abstract PsiLanguageInjectionHost getInjectionHost(PsiElement element);

  public abstract TextRange injectedToHost(PsiElement element, TextRange textRange);
}