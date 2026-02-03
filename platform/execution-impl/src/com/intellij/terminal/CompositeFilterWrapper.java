// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.filters.CompositeFilter;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class CompositeFilterWrapper {

  private final Project myProject;
  private final TerminalExecutionConsole myConsole;
  private final List<Filter> myCustomFilters = new CopyOnWriteArrayList<>();
  private volatile CompositeFilter myCompositeFilter;

  CompositeFilterWrapper(@NotNull Project project, @Nullable TerminalExecutionConsole console, @NotNull Disposable disposable) {
    myProject = project;
    myConsole = console;
    ConsoleFilterProvider.FILTER_PROVIDERS.addChangeListener(() -> {
      myCompositeFilter = null;
    }, disposable);
  }

  void addFilter(@NotNull Filter filter) {
    myCustomFilters.add(filter);
    myCompositeFilter = null;
  }

  @RequiresReadLock
  private @NotNull List<Filter> computeConsoleFilters() {
    if (myProject.isDefault()) {
      return Collections.emptyList();
    }
    if (myProject.isDisposed()) {
      return Collections.emptyList();
    }
    return ConsoleViewUtil.computeConsoleFilters(myProject, myConsole, GlobalSearchScope.allScope(myProject));
  }

  @RequiresReadLock
  @NotNull CompositeFilter getCompositeFilter() {
    CompositeFilter filter = myCompositeFilter;
    if (filter != null) {
      return filter;
    }
    List<Filter> predefinedFilters = computeConsoleFilters();
    filter = new CompositeFilter(myProject, ContainerUtil.concat(myCustomFilters, predefinedFilters));
    filter.setForceUseAllFilters(true);
    myCompositeFilter = filter;
    return filter;
  }
}
