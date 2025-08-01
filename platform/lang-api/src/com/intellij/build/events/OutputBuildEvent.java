// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import com.intellij.execution.process.ProcessOutputType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface OutputBuildEvent {
  @NotNull
  @BuildEventsNls.Message
  String getMessage();

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
}
