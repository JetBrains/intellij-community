// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.ScopeOptimizer;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JavaCompilerReferencesCodeUsageScopeOptimizer implements ScopeOptimizer {
  @Override
  public @Nullable SearchScope getRestrictedUseScope(@NotNull PsiElement element) {
    CompilerReferenceService service = CompilerReferenceService.getInstanceIfEnabled(element.getProject());
    return service == null ? null : service.getScopeWithCodeReferences(element);
  }
}
