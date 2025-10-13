// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.execution.process.ProcessOutputType;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.notNull;

@Internal
public final class OutputBuildEventImpl extends AbstractBuildEvent implements OutputBuildEvent {
  private final @NotNull ProcessOutputType myOutputType;

  @Internal
  public OutputBuildEventImpl(
    @Nullable Object id,
    @Nullable Object parentId,
    @Nullable Long time,
    @NotNull @Message String message,
    @Nullable @Hint String hint,
    @Nullable @Description String description,
    @Nullable ProcessOutputType outputType
  ) {
    super(id, parentId, time, message, hint, description);
    myOutputType = notNull(outputType, () -> ProcessOutputType.STDOUT);
  }

  /**
   * @deprecated Use the {@link OutputBuildEvent#builder} event builder function instead.
   */
  @Deprecated
  public OutputBuildEventImpl(
    @Nullable Object parentId,
    @NotNull @Message String message,
    @NotNull ProcessOutputType outputType
  ) {
    this(null, parentId, null, message, null, null, outputType);
  }

  /**
   * @deprecated Use the {@link OutputBuildEvent#builder} event builder function instead.
   */
  @Deprecated
  public OutputBuildEventImpl(
    @NotNull Object eventId,
    @Nullable Object parentId,
    @NotNull @Message String message,
    @NotNull ProcessOutputType outputType
  ) {
    this(eventId, parentId, null, message, null, null, outputType);
  }

  /**
   * @deprecated Use the {@link OutputBuildEvent#builder} event builder function instead.
   */
  @Deprecated
  public OutputBuildEventImpl(
    @Nullable Object parentId,
    @NotNull @Message String message,
    boolean stdOut
  ) {
    this(parentId, message, stdOut ? ProcessOutputType.STDOUT : ProcessOutputType.STDERR);
  }

  /**
   * @deprecated Use the {@link OutputBuildEvent#builder} event builder function instead.
   */
  @Deprecated
  public OutputBuildEventImpl(
    @NotNull Object eventId,
    @Nullable Object parentId,
    @NotNull @Message String message,
    boolean stdOut
  ) {
    this(eventId, parentId, message, stdOut ? ProcessOutputType.STDOUT : ProcessOutputType.STDERR);
  }

  @Override
  public @NotNull ProcessOutputType getOutputType() {
    return myOutputType;
  }
}