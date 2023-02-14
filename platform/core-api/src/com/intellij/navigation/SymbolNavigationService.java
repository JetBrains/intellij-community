// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.model.Symbol;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * This is an entry point to obtain {@link NavigationTarget}s of a {@link Symbol}.
 * <p/>
 * Implement {@link NavigatableSymbol} in the {@link Symbol}
 * or implement a {@link SymbolNavigationProvider} extension
 * to customize navigation targets of the {@link Symbol}.
 */
@ApiStatus.Experimental
public interface SymbolNavigationService {

  @NotNull
  static SymbolNavigationService getInstance() {
    return ApplicationManager.getApplication().getService(SymbolNavigationService.class);
  }

  @NotNull
  Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Project project, @NotNull Symbol symbol);

  @Contract("_ -> new")
  @NotNull NavigationTarget psiFileNavigationTarget(@NotNull PsiFile file);

  /**
   * This method exists for compatibility. Use with care.
   *
   * @return a target instance which delegates its implementation to older PSI-based APIs
   */
  @Contract("_ -> new")
  @NotNull NavigationTarget psiElementNavigationTarget(@NotNull PsiElement element);

  /**
   * Please use {@link TargetPresentation#builder(String)}
   */
  @ApiStatus.Internal
  @NotNull TargetPresentationBuilder presentationBuilder(@Nls @NotNull String presentableText);
}
