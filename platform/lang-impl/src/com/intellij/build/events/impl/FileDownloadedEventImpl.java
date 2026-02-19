// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.FileDownloadedEvent;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public class FileDownloadedEventImpl extends AbstractBuildEvent implements FileDownloadedEvent {

  private final @NotNull Long myDuration;
  private final @NotNull String myDownloadPath;

  @Internal
  public FileDownloadedEventImpl(
    @NotNull Object startId,
    @Nullable Object parentId,
    @Nullable Long time,
    @NotNull @Message String message,
    @Nullable @Hint String hint,
    @Nullable @Description String description,
    @NotNull Long duration,
    @NotNull String downloadPath
  ) {
    super(startId, parentId, time, message, hint, description);
    myDuration = duration;
    myDownloadPath = downloadPath;
  }

  /**
   * @deprecated Use {@link FileDownloadedEvent#builder} instead
   */
  @Deprecated
  public FileDownloadedEventImpl(
    @NotNull Object eventId,
    @Nullable Object parentId,
    long eventTime,
    @NotNull @Message String message,
    long duration,
    @NotNull String downloadPath
  ) {
    this(eventId, parentId, eventTime, message, null, null, duration, downloadPath);
  }

  @Override
  public long getDuration() {
    return myDuration;
  }

  @Override
  public @NotNull String getDownloadPath() {
    return myDownloadPath;
  }
}
