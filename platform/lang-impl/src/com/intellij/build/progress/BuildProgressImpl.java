// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.progress;

import com.intellij.build.BuildDescriptor;
import com.intellij.build.BuildProgressListener;
import com.intellij.build.FilePosition;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FinishEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.StartEvent;
import com.intellij.build.events.impl.*;
import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
class BuildProgressImpl implements BuildProgress<BuildProgressDescriptor> {
  private final Object myId = new Object();
  private final BuildProgressListener myListener;
  private final @Nullable BuildProgress<BuildProgressDescriptor> myParentProgress;
  private BuildProgressDescriptor myDescriptor;

  BuildProgressImpl(BuildProgressListener listener, @Nullable BuildProgress<BuildProgressDescriptor> parentProgress) {
    myListener = listener;
    myParentProgress = parentProgress;
  }

  protected Object getBuildId() {
    return myDescriptor.getBuildDescriptor().getId();
  }

  @Override
  public @NotNull Object getId() {
    return myId;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> start(@NotNull BuildProgressDescriptor descriptor) {
    myDescriptor = descriptor;
    StartEvent event = createStartEvent(descriptor);
    myListener.onEvent(getBuildId(), event);
    return this;
  }

  protected @NotNull StartEvent createStartEvent(BuildProgressDescriptor descriptor) {
    assert myParentProgress != null;
    return new StartEventImpl(getId(), myParentProgress.getId(), System.currentTimeMillis(), descriptor.getTitle());
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> startChildProgress(@NotNull String title) {
    BuildDescriptor buildDescriptor = myDescriptor.getBuildDescriptor();
    return new BuildProgressImpl(myListener, this).start(new BuildProgressDescriptor() {

      @Override
      public @NotNull String getTitle() {
        return title;
      }

      @Override
      public @NotNull BuildDescriptor getBuildDescriptor() {
        return buildDescriptor;
      }
    });
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> progress(@NotNull String title) {
    return progress(title, -1, -1 , "");
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> progress(@NotNull String title, long total, long progress, String unit) {
    Object parentId = myParentProgress != null ? myParentProgress.getId() : null;
    myListener.onEvent(getBuildId(), new ProgressBuildEventImpl(getId(), parentId, System.currentTimeMillis(), title, total, progress, unit));
    return this;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> output(@NotNull String text, boolean stdOut) {
    myListener.onEvent(getBuildId(), new OutputBuildEventImpl(getId(), text, stdOut));
    return this;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> fileMessage(@NotNull String title,
                                                                     @NotNull String message,
                                                                     @NotNull MessageEvent.Kind kind,
                                                                     @NotNull FilePosition filePosition) {
    StringBuilder fileLink = new StringBuilder(filePosition.getFile().getPath());
    if (filePosition.getStartLine() > 0) {
      fileLink.append(":").append(filePosition.getStartLine() + 1);
      if (filePosition.getStartColumn() > 0) {
        fileLink.append(":").append(filePosition.getStartColumn() + 1);
      }
    }
    @NlsSafe String detailedMessage = fileLink.toString() + '\n' + message;
    FileMessageEventImpl event = new FileMessageEventImpl(getId(), kind, null, title, detailedMessage, filePosition);
    myListener.onEvent(getBuildId(), event);
    return this;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> message(@NotNull String title,
                                                                 @NotNull String message,
                                                                 @NotNull MessageEvent.Kind kind,
                                                                 @Nullable Navigatable navigatable) {
    MessageEventImpl event = new MessageEventImpl(getId(), kind, null, title, message) {
      @Override
      public @Nullable Navigatable getNavigatable(@NotNull Project project) {
        return navigatable;
      }
    };
    myListener.onEvent(getBuildId(), event);
    return this;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish() {
    return finish(false);
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish(boolean isUpToDate) {
    return finish(System.currentTimeMillis(), isUpToDate, myDescriptor.getTitle());
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp) {
    return finish(timeStamp, false, myDescriptor.getTitle());
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp, boolean isUpToDate, @NotNull String message) {
    assertStarted();
    assert myParentProgress != null;
    EventResult result = new SuccessResultImpl(isUpToDate);
    FinishEvent event = new FinishEventImpl(getId(), myParentProgress.getId(), timeStamp, message, result);
    myListener.onEvent(getBuildId(), event);
    return myParentProgress;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> fail() {
    return fail(System.currentTimeMillis(), myDescriptor.getTitle());
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> fail(long timeStamp, @NotNull String message) {
    assertStarted();
    assert myParentProgress != null;
    FinishEvent event = new FinishEventImpl(getId(), myParentProgress.getId(), timeStamp, message, new FailureResultImpl());
    myListener.onEvent(getBuildId(), event);
    return myParentProgress;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> cancel() {
    return cancel(System.currentTimeMillis(), myDescriptor.getTitle());
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> cancel(long timeStamp, @NotNull String message) {
    assertStarted();
    assert myParentProgress != null;
    FinishEventImpl event = new FinishEventImpl(getId(), myParentProgress.getId(), timeStamp, message, new SkippedResultImpl());
    myListener.onEvent(getBuildId(), event);
    return myParentProgress;
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> buildIssue(@NotNull BuildIssue issue, @NotNull MessageEvent.Kind kind) {
    myListener.onEvent(getBuildId(), new BuildIssueEventImpl(getId(), issue, kind));
    return this;
  }

  protected void assertStarted() {
    if (myDescriptor == null) {
      throw new IllegalStateException("The start event was not triggered yet.");
    }
  }
}
