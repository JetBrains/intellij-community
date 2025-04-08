// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.model.Symbol;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.navigation.NavigationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.platform.backend.presentation.TargetPresentationBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.*;

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

  static @NotNull SymbolNavigationService getInstance() {
    return ApplicationManager.getApplication().getService(SymbolNavigationService.class);
  }

  @NotNull
  @Unmodifiable
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
