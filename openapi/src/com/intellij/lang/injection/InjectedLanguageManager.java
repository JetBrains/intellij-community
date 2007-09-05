/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jun 8, 2007
 * Time: 8:41:25 PM
 */
package com.intellij.lang.injection;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class InjectedLanguageManager implements ProjectComponent {
  public static InjectedLanguageManager getInstance(Project project) {
    return project.getComponent(InjectedLanguageManager.class);
  }

  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element);

  public abstract TextRange injectedToHost(@NotNull PsiElement element, @NotNull TextRange textRange);

  public interface MultiHostInjector {
    void getLanguagesToInject(@NotNull PsiElement context, @NotNull MultiHostRegistrar injectionPlacesRegistrar);
  }

  public interface MultiHostRegistrar {
    @NotNull /*this*/ MultiHostRegistrar startInjecting(@NotNull Language language);
    @NotNull /*this*/ MultiHostRegistrar addPlace(@NonNls @Nullable String prefix, @NonNls @Nullable String suffix, @NotNull PsiLanguageInjectionHost host, @NotNull TextRange rangeInsideHost);
    void doneInjecting();
  }

  public abstract void registerMultiHostInjector(@NotNull Class<? extends PsiElement> place, @Nullable ElementFilter filter, @NotNull MultiHostInjector injector);
  public abstract boolean unregisterMultiPlaceInjector(@NotNull MultiHostInjector injector);

  public interface ConcatenationInjector {
    void getLanguagesToInject(@NotNull MultiHostRegistrar injectionPlacesRegistrar, @NotNull PsiElement... operands);
  }
  public abstract void registerConcatenationInjector(@NotNull ConcatenationInjector injector);
  public abstract boolean unregisterConcatenationInjector(@NotNull ConcatenationInjector injector);
}