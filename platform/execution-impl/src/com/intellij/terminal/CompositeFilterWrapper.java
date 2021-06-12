// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.filters.CompositeFilter;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

class CompositeFilterWrapper {

  private final Project myProject;
  private final TerminalExecutionConsole myConsole;
  private final List<Filter> myFilters = new CopyOnWriteArrayList<>();
  private volatile CompositeFilter myCompositeFilter;
  private final AtomicBoolean myConsoleFilterProvidersAdded = new AtomicBoolean(false);

  CompositeFilterWrapper(@NotNull Project project, @Nullable TerminalExecutionConsole console, @NotNull Disposable disposable) {
    myProject = project;
    myConsole = console;
    ConsoleFilterProvider.FILTER_PROVIDERS.addChangeListener(() -> {
      myCompositeFilter = null;
    }, disposable);
  }

  void addFilter(@NotNull Filter filter) {
    myFilters.add(filter);
    myCompositeFilter = null;
  }

  @NotNull
  private List<Filter> createCompositeFilters() {
    if (myProject.isDefault()) {
      return Collections.emptyList();
    }
    return ReadAction.compute(() -> {
      if (myProject.isDisposed()) {
        return Collections.emptyList();
      }
      return ConsoleViewUtil.computeConsoleFilters(myProject, myConsole, GlobalSearchScope.allScope(myProject));
    });
  }

  @NotNull
  CompositeFilter getCompositeFilter() {
    CompositeFilter filter = myCompositeFilter;
    if (filter != null) {
      return filter;
    }
    if (myConsoleFilterProvidersAdded.compareAndSet(false, true)) {
      myFilters.addAll(createCompositeFilters());
    }
    filter = new CompositeFilter(myProject, myFilters);
    filter.setForceUseAllFilters(true);
    myCompositeFilter = filter;
    return filter;
  }
}
