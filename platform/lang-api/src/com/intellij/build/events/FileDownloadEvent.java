// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import com.intellij.build.eventBuilders.FileDownloadEventBuilder;
import com.intellij.build.events.BuildEventsNls.Message;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

@NonExtendable
public interface FileDownloadEvent extends ProgressBuildEvent {

  boolean isFirstInGroup();

  @NotNull String getDownloadPath();

  @CheckReturnValue
  static @NotNull FileDownloadEventBuilder builder(
    @NotNull Object startId,
    @NotNull @Message String message,
    boolean isFirstInGroup,
    @NotNull String downloadPath
  ) {
    return BuildEvents.getInstance().fileDownload(startId, message, isFirstInGroup, downloadPath);
  }
}
