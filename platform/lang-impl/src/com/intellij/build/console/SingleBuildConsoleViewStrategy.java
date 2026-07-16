// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console;

import com.intellij.build.BuildTextConsoleView;
import com.intellij.build.ExecutionNode;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.Failure;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.build.events.OutputReferenceEvent;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

@ApiStatus.Internal
final class SingleBuildConsoleViewStrategy implements BuildConsoleViewStrategy {

  @Override
  public @NotNull ExecutionConsole createRootConsoleView(@NotNull Project project, @NotNull ExecutionConsole executionConsole) {
    if (executionConsole instanceof ConsoleViewImpl consoleView) {
      return new BuildConsoleViewImplV2(project, consoleView);
    }
    return executionConsole;
  }

  @Override
  @TestOnly
  public @NotNull ExecutionConsole resolveNodeConsole(@NotNull Project project,
                                                      @NotNull BuildConsoleViewHandler handler,
                                                      @NotNull ExecutionNode node) {
    if (handler.hasDeferredNodeView(node)) {
      handler.addNodeView(node, createExecutionConsole(project, handler, node));
    }
    var console = handler.getNodeView(node);
    if (console != null) {
      return console;
    }
    return Objects.requireNonNull(handler.getNodeView(handler.getRootExecutionNode()));
  }

  @Override
  public void showExecutionNode(@NotNull Project project,
                                @NotNull BuildConsoleViewHandler handler,
                                @NotNull ExecutionNode node) {
    if (handler.hasDeferredNodeView(node)) {
      handler.addNodeView(node, createExecutionConsole(project, handler, node));
    }
    var viewNode = handler.hasNodeView(node) ? node : handler.getRootExecutionNode();
    handler.showNodeView(viewNode);
    handler.withConsoleView(viewNode, consoleView -> {
      consoleView.scrollToNodeOutput(node.getId());
      consoleView.selectProgressOutput(node.getId());
    });
  }

  private static @NotNull ExecutionConsole createExecutionConsole(@NotNull Project project,
                                                                  @NotNull BuildConsoleViewHandler handler,
                                                                  @NotNull ExecutionNode node) {
    return new BuildConsoleViewImplV2(project, new BuildTextConsoleView(project, true, handler.getExecutionConsoleFilters()), node);
  }

  @Override
  public void dispatchMessageEvent(@NotNull BuildConsoleViewHandler handler,
                                   @Nullable ExecutionNode parentNode,
                                   @NotNull ExecutionNode node,
                                   @NotNull BuildEvent event) {
    handler.withConsoleView(handler.getRootExecutionNode(), consoleView -> consoleView.onEvent(event));
  }

  @Override
  public void dispatchNodeEvent(@NotNull BuildConsoleViewHandler handler, @NotNull ExecutionNode parentNode, @NotNull BuildEvent event) {
    handler.withConsoleView(handler.getRootExecutionNode(), consoleView -> consoleView.onEvent(event));
  }

  @Override
  public void dispatchOutputEvent(@NotNull BuildConsoleViewHandler handler,
                                  @NotNull ExecutionNode parentNode,
                                  @NotNull OutputBuildEvent event) {
    handler.withConsoleView(parentNode, consoleView -> consoleView.onEvent(event));
  }

  @Override
  public void dispatchOutputReferenceEvent(@NotNull BuildConsoleViewHandler handler, @NotNull OutputReferenceEvent event) {
    handler.withConsoleView(handler.getRootExecutionNode(), consoleView -> consoleView.onEvent(event));
  }

  @Override
  public void dispatchFailure(@NotNull BuildConsoleViewHandler handler,
                              @NotNull ExecutionNode failureNode,
                              @NotNull Object failureNodeId,
                              @NotNull Failure failure) {
    handler.withConsoleView(handler.getRootExecutionNode(), consoleView -> consoleView.onFailure(failureNodeId, failure));
  }
}
