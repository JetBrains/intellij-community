// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.ProgressBuildEvent;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileDownloadEventImpl extends AbstractBuildEvent implements ProgressBuildEvent {

  private final long myTotal;
  private final long myProgress;
  private final @NotNull @NlsSafe String myUnit;
  private final boolean myFirstInGroup;

  public FileDownloadEventImpl(@NotNull Object eventId,
                               @Nullable Object parentId,
                               long eventTime,
                               @NotNull String message,
                               long total,
                               long progress,
                               @NotNull String unit,
                               boolean firstInGroup) {
    super(eventId, parentId, eventTime, message);
    myTotal = total;
    myProgress = progress;
    myUnit = unit;
    myFirstInGroup = firstInGroup;
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

  public boolean isFirstInGroup() {
    return myFirstInGroup;
  }
}
