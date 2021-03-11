// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExceptionFilters {
  private ExceptionFilters() {
  }

  public static @NotNull List<Filter> getFilters(@NotNull GlobalSearchScope searchScope) {
    if (searchScope.getProject() == null) {
      return Collections.emptyList();
    }
    return getFilters(searchScope.getProject(), searchScope);
  }

  public static @NotNull List<Filter> getFilters(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    List<ExceptionFilterFactory> extensions = ExceptionFilterFactory.EP_NAME.getExtensionList();
    List<Filter> filters = new ArrayList<>(extensions.size());
    for (ExceptionFilterFactory extension : extensions) {
      filters.add(extension.create(project, searchScope));
    }
    return filters;
  }
}
