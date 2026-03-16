// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.progress;

import com.intellij.build.BuildBundle;
import com.intellij.build.BuildProgressListener;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class BuildRootProgressImpl extends AbstractBuildProgress {

  private @Nullable BuildProgressDescriptor myDescriptor;

  public BuildRootProgressImpl(@NotNull BuildProgressListener buildProgressListener) {
    super(buildProgressListener);
  }

  @Override
  protected @NotNull BuildProgressDescriptor getDescriptor() {
    assert myDescriptor != null : "The task was not started yet.";
    return myDescriptor;
  }

  @Override
  public @NotNull Object getStartId() {
    return getBuildId();
  }

  @Override
  protected @Nullable Object getParentId() {
    return null;
  }

  @Override
  protected @NotNull String getStartMessage() {
    return BuildBundle.message("build.status.running");
  }

  @Override
  protected @NotNull String getFinishMessage() {
    return BuildBundle.message("build.status.finished");
  }

  @Override
  protected @NotNull String getFailMessage() {
    return BuildBundle.message("build.status.failed");
  }

  @Override
  protected @NotNull String getCancelMessage() {
    return BuildBundle.message("build.status.cancelled");
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> start(@NotNull String message, @NotNull BuildProgressDescriptor descriptor) {
    myDescriptor = descriptor;
    return event(
      StartBuildEvent.builder(message, descriptor.getBuildDescriptor())
        .withParentId(getParentId())
        .build()
    );
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp, @NotNull String message, @NotNull EventResult result) {
    return event(
      FinishBuildEvent.builder(getBuildId(), message, result)
        .withParentId(getParentId())
        .withTime(timeStamp)
        .build()
    );
  }
}
