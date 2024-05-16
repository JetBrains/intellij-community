// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileDownloadedEventImpl extends AbstractBuildEvent {

  private final long myDuration;
  private final @NotNull String myDownloadPath;

  public FileDownloadedEventImpl(@NotNull Object eventId,
                                 @Nullable Object parentId,
                                 long eventTime,
                                 @NotNull String message,
                                 long duration,
                                 @NotNull String downloadPath) {
    super(eventId, parentId, eventTime, message);
    myDuration = duration;
    myDownloadPath = downloadPath;
  }

  public long getDuration() {
    return myDuration;
  }

  public @NotNull String getDownloadPath() {
    return myDownloadPath;
  }
}
