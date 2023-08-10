// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ConsoleDependentInputFilterProvider implements ConsoleInputFilterProvider {
  private static final InputFilter[] EMPTY_ARRAY = new InputFilter[0];

  public abstract @NotNull List<InputFilter> getDefaultFilters(@NotNull ConsoleView consoleView,
                                                               @NotNull Project project,
                                                               @NotNull GlobalSearchScope scope);

  @Override
  public final InputFilter @NotNull [] getDefaultFilters(@NotNull Project project) {
    return EMPTY_ARRAY;
  }
}
