// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.progress;

import com.intellij.build.BuildBundle;
import com.intellij.build.BuildProgressListener;
import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.FinishEvent;
import com.intellij.build.events.StartEvent;
import com.intellij.build.events.impl.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class BuildRootProgressImpl extends BuildProgressImpl {
  private final BuildProgressListener myListener;

  public BuildRootProgressImpl(BuildProgressListener buildProgressListener) {
    super(buildProgressListener, null);
    myListener = buildProgressListener;
  }

  @Override
  public @NotNull Object getId() {
    return getBuildId();
  }

  @Override
  protected @NotNull StartEvent createStartEvent(BuildProgressDescriptor descriptor) {
    return new StartBuildEventImpl(descriptor.getBuildDescriptor(), BuildBundle.message("build.status.running"));
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish() {
    return finish(System.currentTimeMillis(), false, BuildBundle.message("build.status.finished"));
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp, boolean isUpToDate, @NotNull @BuildEventsNls.Message String message) {
    assertStarted();
    FinishEvent event = new FinishBuildEventImpl(getId(), null, timeStamp, message, new SuccessResultImpl(isUpToDate));
    myListener.onEvent(getBuildId(), event);
    return this;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> fail() {
    return fail(System.currentTimeMillis(), BuildBundle.message("build.status.failed"));
  }

  @Override
  public @NotNull BuildRootProgressImpl fail(long timeStamp, @NotNull @BuildEventsNls.Message String message) {
    assertStarted();
    FinishBuildEvent event = new FinishBuildEventImpl(getId(), null, timeStamp, message, new FailureResultImpl());
    myListener.onEvent(getBuildId(), event);
    return this;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> cancel() {
    return cancel(System.currentTimeMillis(), BuildBundle.message("build.status.cancelled"));
  }

  @Override
  public @NotNull BuildRootProgressImpl cancel(long timeStamp, @NotNull String message) {
    assertStarted();
    FinishBuildEventImpl event = new FinishBuildEventImpl(getId(), null, timeStamp, message, new SkippedResultImpl());
    myListener.onEvent(getBuildId(), event);
    return this;
  }
}
