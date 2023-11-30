// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileDownloadedEventImpl extends AbstractBuildEvent {

  private final long myDuration;

  public FileDownloadedEventImpl(@NotNull Object eventId,
                                 @Nullable Object parentId,
                                 long eventTime,
                                 @NotNull String message,
                                 long duration) {
    super(eventId, parentId, eventTime, message);
    myDuration = duration;
  }

  public long getDuration() {
    return myDuration;
  }
}
