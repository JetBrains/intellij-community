// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.execution.filters.ExceptionFilterFactory;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

final class VcsContentAnnotationExceptionFilterFactory implements ExceptionFilterFactory {
  @Override
  public @NotNull Filter create(@NotNull GlobalSearchScope searchScope) {
    return new VcsContentAnnotationExceptionFilter(Objects.requireNonNull(searchScope.getProject()), searchScope);
  }

  @Override
  public @NotNull Filter create(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    return new VcsContentAnnotationExceptionFilter(project, searchScope);
  }
}
