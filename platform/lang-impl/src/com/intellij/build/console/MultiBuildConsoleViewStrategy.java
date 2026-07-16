// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console;

import com.intellij.build.BuildTextConsoleView;
import com.intellij.build.ExecutionNode;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.Failure;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.build.events.OutputReferenceEvent;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
final class MultiBuildConsoleViewStrategy implements BuildConsoleViewStrategy {

  @Override
  public @NotNull ExecutionConsole createRootConsoleView(@NotNull Project project, @NotNull ExecutionConsole executionConsole) {
    if (executionConsole instanceof ConsoleView consoleView) {
      return new BuildConsoleViewImpl(project, consoleView);
    }
    return executionConsole;
  }

  @Override
  @TestOnly
  public @NotNull ExecutionConsole resolveNodeConsole(@NotNull Project project,
                                                      @NotNull BuildConsoleViewHandler handler,
                                                      @NotNull ExecutionNode node) {
    return handler.getOrAddNodeView(node, () -> createExecutionConsole(project, handler));
  }

  @Override
  public void showExecutionNode(@NotNull Project project,
                                @NotNull BuildConsoleViewHandler handler,
                                @NotNull ExecutionNode node) {
    handler.getOrAddNodeView(node, () -> createExecutionConsole(project, handler));
    handler.showNodeView(node);
  }

  private static @NotNull ExecutionConsole createExecutionConsole(@NotNull Project project, @NotNull BuildConsoleViewHandler handler) {
    return new BuildConsoleViewImpl(project, new BuildTextConsoleView(project, true, handler.getExecutionConsoleFilters()));
  }

  @Override
  public void dispatchMessageEvent(@NotNull BuildConsoleViewHandler handler,
                                   @Nullable ExecutionNode parentNode,
                                   @NotNull ExecutionNode node,
                                   @NotNull BuildEvent event) {
    if (parentNode != null && parentNode != handler.getRootExecutionNode()) {
      handler.withConsoleView(parentNode, consoleView -> consoleView.onEvent(event));
    }
    handler.withConsoleView(node, consoleView -> consoleView.onEvent(event));
  }

  @Override
  public void dispatchNodeEvent(@NotNull BuildConsoleViewHandler handler, @NotNull ExecutionNode parentNode, @NotNull BuildEvent event) {
    handler.withConsoleView(parentNode, consoleView -> consoleView.onEvent(event));
  }

  @Override
  public void dispatchOutputEvent(@NotNull BuildConsoleViewHandler handler,
                                  @NotNull ExecutionNode parentNode,
                                  @NotNull OutputBuildEvent event) {
    handler.withConsoleView(parentNode, consoleView -> consoleView.onEvent(event));
  }

  @Override
  public void dispatchOutputReferenceEvent(@NotNull BuildConsoleViewHandler handler, @NotNull OutputReferenceEvent event) {
    throw new UnsupportedOperationException("Output reference events are not supported in multi console mode.");
  }

  @Override
  public void dispatchFailure(@NotNull BuildConsoleViewHandler handler,
                              @NotNull ExecutionNode failureNode,
                              @NotNull Object failureNodeId,
                              @NotNull Failure failure) {
    handler.withConsoleView(failureNode, consoleView -> consoleView.onFailure(failureNodeId, failure));
  }
}
