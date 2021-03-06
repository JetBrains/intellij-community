// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ScopeOptimizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JavaCompilerReferencesCodeUsageScopeOptimizer implements ScopeOptimizer {
  @Nullable
  @Override
  public GlobalSearchScope getScopeToExclude(@NotNull PsiElement element) {
    CompilerReferenceService service = CompilerReferenceService.getInstanceIfEnabled(element.getProject());
    return service == null ? null : service.getScopeWithoutCodeReferences(element);
  }
}
