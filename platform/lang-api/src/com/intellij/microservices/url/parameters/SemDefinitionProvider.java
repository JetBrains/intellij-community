// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.parameters;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface SemDefinitionProvider {
  /**
   * Implements the PathVariable find-usages among non-literal elements (usually PsiParameters).
   *
   * @return {@link PsiElement}s for which the {@link PathVariableSem} that corresponds to the {@code pomTarget}
   * is defined via the {@link com.intellij.semantic.SemService}
   */
  @NotNull Iterable<PsiElement> findSemDefiningElements(@NotNull PathVariablePomTarget pomTarget);
}