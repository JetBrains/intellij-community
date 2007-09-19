/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jun 8, 2007
 * Time: 8:41:25 PM
 */
package com.intellij.lang.injection;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

public abstract class InjectedLanguageManager implements ProjectComponent {
  public static final ExtensionPointName<ConcatenationAwareInjector> CONCATENATION_INJECTOR_EP_NAME = ExtensionPointName.create("com.intellij.concatenationAwareInjector");
  public static final ExtensionPointName<MultiHostInjector> MULTIHOST_INJECTOR_EP_NAME = ExtensionPointName.create("com.intellij.multiHostInjector");

  public static InjectedLanguageManager getInstance(Project project) {
    return project.getComponent(InjectedLanguageManager.class);
  }

  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element);

  public abstract TextRange injectedToHost(@NotNull PsiElement element, @NotNull TextRange textRange);

  public abstract void registerMultiHostInjector(@NotNull MultiHostInjector injector);
  public abstract boolean unregisterMultiPlaceInjector(@NotNull MultiHostInjector injector);

  public abstract void registerConcatenationInjector(@NotNull ConcatenationAwareInjector injector);
  public abstract boolean unregisterConcatenationInjector(@NotNull ConcatenationAwareInjector injector);

}