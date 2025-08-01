// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.execution.process.ProcessOutputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OutputBuildEventImpl extends AbstractBuildEvent implements OutputBuildEvent {
  private final @NotNull ProcessOutputType myOutputType;

  public OutputBuildEventImpl(@Nullable Object parentId, @NotNull @BuildEventsNls.Message String message, @NotNull ProcessOutputType outputType) {
    this(new Object(), parentId, message, outputType);
  }

  public OutputBuildEventImpl(@NotNull Object eventId, @Nullable Object parentId, @NotNull @BuildEventsNls.Message String message, @NotNull ProcessOutputType outputType) {
    super(eventId, parentId, -1, message);
    myOutputType = outputType;
  }

  /**
   * @deprecated Use {@link #OutputBuildEventImpl(Object, String, ProcessOutputType)} instead
   */
  @Deprecated
  public OutputBuildEventImpl(@Nullable Object parentId, @NotNull @BuildEventsNls.Message  String message, boolean stdOut) {
    this(parentId, message, stdOut ? ProcessOutputType.STDOUT : ProcessOutputType.STDERR);
  }

  /**
   * @deprecated Use {@link #OutputBuildEventImpl(Object, Object, String, ProcessOutputType)} instead
   */
  @Deprecated
  public OutputBuildEventImpl(@NotNull Object eventId, @Nullable Object parentId, @NotNull @BuildEventsNls.Message String message, boolean stdOut) {
    this(eventId, parentId, message, stdOut ? ProcessOutputType.STDOUT : ProcessOutputType.STDERR);
  }

  @Override
  public @NotNull ProcessOutputType getOutputType() {
    return myOutputType;
  }
}