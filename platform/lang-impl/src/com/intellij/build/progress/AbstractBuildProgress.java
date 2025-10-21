// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.progress;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.FilePosition;
import com.intellij.build.events.*;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.SkippedResultImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public abstract class AbstractBuildProgress implements BuildProgress<BuildProgressDescriptor> {

  protected final @NotNull BuildEvents myBuildEvents;
  private final @NotNull BuildProgressListener myListener;

  AbstractBuildProgress(
    @NotNull BuildEvents buildEvents,
    @NotNull BuildProgressListener listener
  ) {
    myBuildEvents = buildEvents;
    myListener = listener;
  }

  protected abstract @NotNull BuildProgressDescriptor getDescriptor();

  protected final @NotNull Object getBuildId() {
    return getDescriptor().getBuildDescriptor().getId();
  }

  protected abstract @NotNull Object getStartId();

  protected abstract @Nullable Object getParentId();

  protected abstract @NotNull @Message String getStartMessage();

  protected abstract @NotNull @Message String getFinishMessage();

  protected abstract @NotNull @Message String getFailMessage();

  protected abstract @NotNull @Message String getCancelMessage();

  @Override
  public final @NotNull Object getId() {
    return getStartId();
  }

  protected @NotNull BuildProgress<BuildProgressDescriptor> event(@NotNull BuildEvent event) {
    myListener.onEvent(getBuildId(), event);
    return this;
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> start(@NotNull BuildProgressDescriptor descriptor) {
    return start(getStartMessage(), descriptor);
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish(@NotNull EventResult result) {
    return finish(getFinishMessage(), result);
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> finish(@NotNull String message, @NotNull EventResult result) {
    return finish(System.currentTimeMillis(), message, result);
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> finish() {
    return finish(false);
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> finish(boolean isUpToDate) {
    return finish(System.currentTimeMillis(), isUpToDate, getFinishMessage());
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp) {
    return finish(timeStamp, false, getFinishMessage());
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp, boolean isUpToDate, @NotNull String message) {
    return finish(timeStamp, message, new SuccessResultImpl(isUpToDate));
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> fail() {
    return fail(System.currentTimeMillis(), getFailMessage());
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> fail(long timeStamp, @NotNull String message) {
    return finish(timeStamp, message, new FailureResultImpl());
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> cancel() {
    return cancel(System.currentTimeMillis(), getCancelMessage());
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> cancel(long timeStamp, @NotNull String message) {
    return finish(timeStamp, message, new SkippedResultImpl());
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> startChildProgress(@NotNull String title) {
    var childBuildProgressDescriptor = new BuildProgressDescriptorImpl(title, getDescriptor().getBuildDescriptor());
    var childBuildProgress = new ChildBuildProgressImpl(myBuildEvents, myListener, childBuildProgressDescriptor, this);
    return childBuildProgress.start(childBuildProgressDescriptor);
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> progress(@NotNull String title) {
    return event(
      myBuildEvents.progress()
        .withStartId(getStartId())
        .withParentId(getParentId())
        .withMessage(title)
        .build()
    );
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> progress(@NotNull String title, long total, long progress, String unit) {
    return event(
      myBuildEvents.progress()
        .withStartId(getStartId())
        .withParentId(getParentId())
        .withMessage(title)
        .withTotal(total)
        .withProgress(progress)
        .withUnit(unit)
        .build()
    );
  }

  @Override
  public @NotNull BuildProgress<BuildProgressDescriptor> presentable(
    @NotNull String title,
    @NotNull BuildEventPresentationData presentationData
  ) {
    return event(
      myBuildEvents.presentable()
        .withParentId(getStartId())
        .withMessage(title)
        .withPresentationData(presentationData)
        .build()
    );
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> output(@NotNull String text, @NotNull ProcessOutputType processOutputType) {
    return event(
      myBuildEvents.output()
        .withParentId(getStartId())
        .withMessage(text)
        .withOutputType(processOutputType)
        .build()
    );
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> message(
    @NotNull String title,
    @NotNull String message,
    @NotNull MessageEvent.Kind kind,
    @Nullable Navigatable navigatable
  ) {
    return event(
      myBuildEvents.message()
        .withParentId(getStartId())
        .withMessage(title)
        .withDescription(message)
        .withKind(kind)
        .withNavigatable(navigatable)
        .build()
    );
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> fileMessage(
    @NotNull String title,
    @NotNull String message,
    @NotNull MessageEvent.Kind kind,
    @NotNull FilePosition filePosition
  ) {
    return event(
      myBuildEvents.fileMessage()
        .withParentId(getStartId())
        .withMessage(title)
        .withDescription(message)
        .withKind(kind)
        .withFilePosition(filePosition)
        .build()
    );
  }

  @Override
  public final @NotNull BuildProgress<BuildProgressDescriptor> buildIssue(
    @NotNull BuildIssue issue,
    @NotNull MessageEvent.Kind kind
  ) {
    return event(
      myBuildEvents.buildIssue()
        .withParentId(getStartId())
        .withIssue(issue)
        .withKind(kind)
        .build()
    );
  }
}
