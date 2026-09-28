// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import com.intellij.build.BuildViewSettingsProvider;
import com.intellij.build.eventBuilders.OutputBuildEventBuilder;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.execution.process.ProcessOutputType;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/**
 * Produces new output in the build console.
 * <p>
 * The whole build output is printed as a single stream into the shared console when {@link StartBuildEvent#getBuildViewSettings()} have a
 * valid {@link BuildViewSettingsProvider} and {@link BuildViewSettingsProvider#isSingleBuildConsoleView} is true for the execution.
 * Therefore, the {@link #getParentId} only anchors the produced output to a build tree node,
 * instead of splitting the output between the per-node consoles.
 * <p>
 * Use the {@link OutputReferenceEvent} to attach the output of this event to another build tree node,
 * instead of producing the same output twice.
 */
public interface OutputBuildEvent extends BuildEvent {

  @Override
  @Message
  @NotNull String getMessage();

  /**
   * @return type of the output (stdout, stderr, or system)
   */
  @NotNull ProcessOutputType getOutputType();

  /**
   * @deprecated Use {@link #getOutputType()} instead
   */
  @Deprecated
  default boolean isStdOut() {
    return getOutputType().isStdout();
  }

  @CheckReturnValue
  static @NotNull OutputBuildEventBuilder builder(
    @NotNull @Message String message
  ) {
    return BuildEvents.getInstance().output(message);
  }
}
