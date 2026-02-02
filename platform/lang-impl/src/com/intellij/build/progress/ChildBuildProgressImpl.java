// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.progress;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FinishEvent;
import com.intellij.build.events.StartEvent;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public class ChildBuildProgressImpl extends AbstractBuildProgress {

  private final @NotNull Object myId = new Object();
  private final @NotNull BuildProgressDescriptor myDescriptor;
  private final @NotNull BuildProgress<BuildProgressDescriptor> myParentProgress;

  ChildBuildProgressImpl(
    @NotNull BuildProgressListener listener,
    @NotNull BuildProgressDescriptor descriptor,
    @NotNull BuildProgress<BuildProgressDescriptor> parentProgress
  ) {
    super(listener);
    myDescriptor = descriptor;
    myParentProgress = parentProgress;
  }

  @Override
  protected @NotNull BuildProgressDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  public @NotNull Object getStartId() {
    return myId;
  }

  @Override
  protected @Nullable Object getParentId() {
    return myParentProgress.getId();
  }

  @Override
  protected @NotNull String getStartMessage() {
    return myDescriptor.getTitle();
  }

  @Override
  protected @NotNull String getFinishMessage() {
    return myDescriptor.getTitle();
  }

  @Override
  protected @NotNull String getFailMessage() {
    return myDescriptor.getTitle();
  }

  @Override
  protected @NotNull String getCancelMessage() {
    return myDescriptor.getTitle();
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> start(@NotNull String message, @NotNull BuildProgressDescriptor descriptor) {
    return event(
      StartEvent.builder(getStartId(), message)
        .withParentId(getParentId())
        .build()
    );
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp, @NotNull String message, @NotNull EventResult result) {
    event(
      FinishEvent.builder(getStartId(), message, result)
        .withParentId(getParentId())
        .withTime(timeStamp)
        .build()
    );
    return myParentProgress;
  }
}
