// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ExceptionFilters {
  private ExceptionFilters() {
  }

  @NotNull
  public static List<Filter> getFilters(@NotNull GlobalSearchScope searchScope) {
    List<ExceptionFilterFactory> extensions = ExceptionFilterFactory.EP_NAME.getExtensionList();
    List<Filter> filters = new ArrayList<>(extensions.size());
    for (ExceptionFilterFactory extension : extensions) {
      filters.add(extension.create(searchScope));
    }
    return filters;
  }
}
