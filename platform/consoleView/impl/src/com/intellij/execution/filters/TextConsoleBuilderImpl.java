// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TextConsoleBuilderImpl extends TextConsoleBuilder {
  private final Project myProject;
  private final GlobalSearchScope myScope;
  private final List<Filter> myFilters = new SmartList<>();
  private boolean myViewer;
  private boolean myUsePredefinedMessageFilter = true;
  private boolean useOwnModalityStateForUpdates = false;
  private boolean myAttachToProcessOutput = true;
  private boolean myAttachToProcessInput = true;

  public TextConsoleBuilderImpl(@NotNull Project project) {
    this(project, GlobalSearchScope.allScope(project));
  }

  public TextConsoleBuilderImpl(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    myProject = project;
    myScope = scope;
  }

  @Override
  public @NotNull ConsoleView getConsole() {
    final ConsoleView consoleView = createConsole();
    for (final Filter filter : myFilters) {
      consoleView.addMessageFilter(filter);
    }
    return consoleView;
  }

  @ApiStatus.Internal
  @Override
  public void setUseOwnModalityStateForUpdates(boolean useOwnModalityStateForUpdates) {
    this.useOwnModalityStateForUpdates = useOwnModalityStateForUpdates;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull TextConsoleBuilder setAttachToProcessOutput(boolean attachToProcessOutput) {
    myAttachToProcessOutput = attachToProcessOutput;
    return this;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull TextConsoleBuilder setAttachToProcessInput(boolean attachToProcessInput) {
    myAttachToProcessInput = attachToProcessInput;
    return this;
  }

  protected @NotNull ConsoleView createConsole() {
    var result = new ConsoleViewImpl(myProject, myScope, myViewer, myUsePredefinedMessageFilter,
                                     myAttachToProcessOutput, myAttachToProcessInput);
    result.setUseOwnModalityForUpdates(useOwnModalityStateForUpdates);
    return result;
  }

  @Override
  public void addFilter(@NotNull Filter filter) {
    myFilters.add(filter);
  }

  @Override
  public @NotNull TextConsoleBuilder filters(@NotNull List<? extends Filter> filters) {
    myFilters.addAll(filters);
    return this;
  }

  @Override
  public void setViewer(boolean isViewer) {
    myViewer = isViewer;
  }

  protected @NotNull Project getProject() {
    return myProject;
  }

  protected GlobalSearchScope getScope() {
    return myScope;
  }

  protected @NotNull List<Filter> getFilters() {
    return myFilters;
  }

  protected boolean isViewer() {
    return myViewer;
  }

  public void setUsePredefinedMessageFilter(boolean usePredefinedMessageFilter) {
    myUsePredefinedMessageFilter = usePredefinedMessageFilter;
  }

  public boolean isUsePredefinedMessageFilter() {
    return myUsePredefinedMessageFilter;
  }
}