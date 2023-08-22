// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public abstract class TextConsoleBuilderFactory {
  public abstract @NotNull TextConsoleBuilder createBuilder(@NotNull Project project);

  public abstract @NotNull TextConsoleBuilder createBuilder(@NotNull Project project, @NotNull GlobalSearchScope scope);

  public static TextConsoleBuilderFactory getInstance() {
    return ApplicationManager.getApplication().getService(TextConsoleBuilderFactory.class);
  }
}