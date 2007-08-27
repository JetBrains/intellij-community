/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jun 8, 2007
 * Time: 8:41:25 PM
 */
package com.intellij.lang.injection;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class InjectedLanguageManager implements ApplicationComponent {
  public static InjectedLanguageManager getInstance() {
    return ApplicationManager.getApplication().getComponent(InjectedLanguageManager.class);
  }

  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element);

  public abstract TextRange injectedToHost(@NotNull PsiElement element, @NotNull TextRange textRange);

  public abstract boolean unregisterMultiPlaceInjector(@NotNull MultiPlaceInjector injector);

  public interface MultiPlaceInjector {
    void getLanguagesToInject(@NotNull PsiElement context, @NotNull MultiPlaceRegistrar injectionPlacesRegistrar);
  }

  public interface MultiPlaceRegistrar {
    void addPlaces(@NotNull Language language, @NonNls @Nullable String prefix, @NonNls @Nullable String suffix, @NotNull List<Pair<PsiLanguageInjectionHost, TextRange>> hosts);
  }

  public abstract void registerMultiHostInjector(@NotNull Class<? extends PsiElement> place, @Nullable ElementFilter filter, @NotNull MultiPlaceInjector injector);
}