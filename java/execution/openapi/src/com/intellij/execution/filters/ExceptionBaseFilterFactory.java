// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class ExceptionBaseFilterFactory implements ExceptionFilterFactory {
  @Override
  public @NotNull Filter create(@NotNull GlobalSearchScope searchScope) {
    return new AdvancedExceptionFilter(Objects.requireNonNull(searchScope.getProject()), searchScope);
  }

  @Override
  public Filter create(@NotNull Project project,
                       @NotNull GlobalSearchScope searchScope) {
    return new AdvancedExceptionFilter(project, searchScope);
  }
}
