// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import com.intellij.build.eventBuilders.OutputBuildEventBuilder;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.execution.process.ProcessOutputType;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

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
    return getOutputType() == ProcessOutputType.STDOUT;
  }

  @CheckReturnValue
  static @NotNull OutputBuildEventBuilder builder(
    @NotNull @Message String message
  ) {
    return BuildEvents.getInstance().output(message);
  }
}
