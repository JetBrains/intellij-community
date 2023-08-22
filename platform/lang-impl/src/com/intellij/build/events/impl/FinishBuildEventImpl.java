// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FinishBuildEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public final class FinishBuildEventImpl extends FinishEventImpl implements FinishBuildEvent {

  public FinishBuildEventImpl(@NotNull Object eventId,
                              @Nullable Object parentId,
                              long eventTime,
                              @NotNull @BuildEventsNls.Message String message,
                              @NotNull EventResult result) {
    super(eventId, parentId, eventTime, message, result);
  }
}
