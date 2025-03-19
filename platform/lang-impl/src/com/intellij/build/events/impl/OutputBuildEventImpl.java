// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.OutputBuildEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public final class OutputBuildEventImpl extends AbstractBuildEvent implements OutputBuildEvent {
  private final boolean myStdOut;

  public OutputBuildEventImpl(@Nullable Object parentId, @NotNull @BuildEventsNls.Message  String message, boolean stdOut) {
    this(new Object(), parentId, message, stdOut);
  }

  public OutputBuildEventImpl(@NotNull Object eventId, @Nullable Object parentId, @NotNull @BuildEventsNls.Message String message, boolean stdOut) {
    super(eventId, parentId, -1, message);
    myStdOut = stdOut;
  }

  @Override
  public boolean isStdOut() {
    return myStdOut;
  }
}
