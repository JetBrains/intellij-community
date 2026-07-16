// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console;

import com.intellij.build.ExecutionNode;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.Failure;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.build.events.OutputReferenceEvent;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Encapsulates how {@link BuildConsoleViewHandler} builds its root console view and shows the console for a selected node.
 */
@ApiStatus.Internal
public interface BuildConsoleViewStrategy {

  /**
   * Wraps the raw {@code executionConsole} into the root console view that is added to the handler's composite view,
   * or returns it unchanged when it is not of the expected type.
   */
  @NotNull ExecutionConsole createRootConsoleView(@NotNull Project project, @NotNull ExecutionConsole executionConsole);

  @TestOnly
  @NotNull ExecutionConsole resolveNodeConsole(@NotNull Project project,
                                               @NotNull BuildConsoleViewHandler handler,
                                               @NotNull ExecutionNode node);

  /**
   * Shows (and lazily creates, if needed) the console view for the selected {@code node}, operating on the handler's
   * composite view through its package-private accessors.
   */
  void showExecutionNode(@NotNull Project project,
                         @NotNull BuildConsoleViewHandler handler,
                         @NotNull ExecutionNode node);

  /**
   * Routes a message-like event to the console(s) responsible for {@code node} (in multi-console mode, also to its
   * {@code parentNode}); in single-console mode it goes to the shared root console.
   */
  void dispatchMessageEvent(@NotNull BuildConsoleViewHandler handler,
                            @Nullable ExecutionNode parentNode,
                            @NotNull ExecutionNode node,
                            @NotNull BuildEvent event);

  /**
   * Routes an output/build event to {@code parentNode}'s console (multi-console mode) or the shared root console
   * (single-console mode).
   */
  void dispatchNodeEvent(@NotNull BuildConsoleViewHandler handler, @NotNull ExecutionNode parentNode, @NotNull BuildEvent event);

  /**
   * Routes a raw-output event (process stdout/stderr) to target console.
   */
  void dispatchOutputEvent(@NotNull BuildConsoleViewHandler handler, @NotNull ExecutionNode parentNode, @NotNull OutputBuildEvent event);

  /**
   * Routes an output-reference event to the shared root console; supported only in single-console mode.
   */
  void dispatchOutputReferenceEvent(@NotNull BuildConsoleViewHandler handler, @NotNull OutputReferenceEvent event);

  /**
   * Routes a failure to {@code failureNode}'s console (multi-console mode) or the shared root console (single-console mode).
   */
  void dispatchFailure(@NotNull BuildConsoleViewHandler handler,
                       @NotNull ExecutionNode failureNode,
                       @NotNull Object failureNodeId,
                       @NotNull Failure failure);

  static @NotNull BuildConsoleViewStrategy create(boolean singleConsole) {
    return singleConsole ? new SingleBuildConsoleViewStrategy() : new MultiBuildConsoleViewStrategy();
  }
}
