// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FinishBuildEvent;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@Internal
public final class FinishBuildEventImpl extends FinishEventImpl implements FinishBuildEvent {

  @Internal
  public FinishBuildEventImpl(
    @NotNull Object startBuildId,
    @Nullable Object parentId,
    @Nullable Long time,
    @NotNull @Message String message,
    @Nullable @Hint String hint,
    @Nullable @Description String description,
    @NotNull EventResult result
  ) {
    super(startBuildId, parentId, time, message, hint, description, result);
  }

  /**
   * @deprecated Use {@link FinishBuildEvent#builder} event builder instead.
   */
  @Deprecated
  public FinishBuildEventImpl(
    @NotNull Object eventId,
    @Nullable Object parentId,
    long eventTime,
    @NotNull @Message String message,
    @NotNull EventResult result
  ) {
    this(eventId, parentId, eventTime, message, null, null, result);
  }
}
