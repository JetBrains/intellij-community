/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jun 8, 2007
 * Time: 8:41:25 PM
 */
package com.intellij.lang.injection;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

public abstract class InjectedLanguageManager implements ApplicationComponent {
  public static InjectedLanguageManager getInstance() {
    return ApplicationManager.getApplication().getComponent(InjectedLanguageManager.class);
  }

  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element);

  public abstract TextRange injectedToHost(@NotNull PsiElement element, @NotNull TextRange textRange);

  public interface MultiPlaceInjector {
    void getLanguagesToInject(@NotNull PsiElement context, @NotNull MultiPlaceRegistrar injectionPlacesRegistrar);
  }

  public interface MultiPlaceRegistrar {
    @NotNull /*this*/ MultiPlaceRegistrar startInjecting(@NotNull Language language);
    @NotNull /*this*/ MultiPlaceRegistrar addPlace(@NonNls @Nullable String prefix, @NonNls @Nullable String suffix, @NotNull PsiLanguageInjectionHost host, @NotNull TextRange rangeInsideHost);
    void doneInjecting();
  }

  public abstract void registerMultiHostInjector(@NotNull Class<? extends PsiElement> place, @Nullable ElementFilter filter, @NotNull MultiPlaceInjector injector);
  public abstract boolean unregisterMultiPlaceInjector(@NotNull MultiPlaceInjector injector);
}