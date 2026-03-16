// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import com.intellij.build.eventBuilders.FileDownloadedEventBuilder;
import com.intellij.build.events.BuildEventsNls.Message;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

public interface FileDownloadedEvent extends BuildEvent {

  long getDuration();

  @NotNull String getDownloadPath();

  @CheckReturnValue
  static @NotNull FileDownloadedEventBuilder builder(
    @NotNull Object startId,
    @NotNull @Message String message,
    long duration,
    @NotNull String downloadPath
  ) {
    return BuildEvents.getInstance().fileDownloaded(startId, message, duration, downloadPath);
  }
}
