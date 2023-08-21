// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.ProgressBuildEvent;
import com.intellij.openapi.util.NlsContext;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public final class ProgressBuildEventImpl extends AbstractBuildEvent implements ProgressBuildEvent {

  private final long myTotal;
  private final long myProgress;
  private final String myUnit;

  public ProgressBuildEventImpl(@NotNull Object eventId,
                                @Nullable Object parentId,
                                long eventTime,
                                @NlsContexts.ProgressTitle
                                @NotNull @NlsContexts.ProgressText String message,
                                long total,
                                long progress,
                                @NotNull String unit) {
    super(eventId, parentId, eventTime, message);
    myTotal = total;
    myProgress = progress;
    myUnit = unit;
  }

  @Override
  public long getTotal() {
    return myTotal;
  }

  @Override
  public long getProgress() {
    return myProgress;
  }

  @Override
  public String getUnit() {
    return myUnit;
  }
}
