// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public final class TextConsoleBuilderFactoryImpl extends TextConsoleBuilderFactory {
  @Override
  public @NotNull TextConsoleBuilder createBuilder(final @NotNull Project project) {
    return new TextConsoleBuilderImpl(project);
  }

  @Override
  public @NotNull TextConsoleBuilder createBuilder(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    return new TextConsoleBuilderImpl(project, scope);
  }
}