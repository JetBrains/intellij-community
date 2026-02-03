// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.FileDownloadEvent;
import com.intellij.openapi.util.NlsContexts.ProgressText;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.notNull;

@Internal
public class FileDownloadEventImpl extends AbstractBuildEvent implements FileDownloadEvent {

  private final @NotNull Long myTotal;
  private final @NotNull Long myProgress;
  private final @NotNull String myUnit;

  private final boolean myFirstInGroup;
  private final @NotNull String myDownloadPath;

  @Internal
  public FileDownloadEventImpl(
    @NotNull Object startId,
    @Nullable Object parentId,
    @Nullable Long time,
    @NotNull @ProgressTitle @ProgressText @Message String message,
    @Nullable @Hint String hint,
    @Nullable @Description String description,
    @Nullable Long total,
    @Nullable Long progress,
    @Nullable String unit,
    boolean firstInGroup,
    @NotNull String downloadPath
  ) {
    super(startId, parentId, time, message, hint, description);
    myTotal = notNull(total, -1L);
    myProgress = notNull(progress, -1L);
    myUnit = notNull(unit, "");
    myFirstInGroup = firstInGroup;
    myDownloadPath = downloadPath;
  }

  /**
   * @deprecated Use {@link FileDownloadEvent#builder} instead
   */
  @Deprecated
  public FileDownloadEventImpl(
    @NotNull Object eventId,
    @Nullable Object parentId,
    long eventTime,
    @NotNull @ProgressTitle @ProgressText @Message String message,
    long total,
    long progress,
    @NotNull String unit,
    boolean firstInGroup,
    @NotNull String downloadPath
  ) {
    this(eventId, parentId, eventTime, message, null, null, total, progress, unit, firstInGroup, downloadPath);
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
  public @NotNull String getUnit() {
    return myUnit;
  }

  @Override
  public boolean isFirstInGroup() {
    return myFirstInGroup;
  }

  @Override
  public @NotNull String getDownloadPath() {
    return myDownloadPath;
  }
}
