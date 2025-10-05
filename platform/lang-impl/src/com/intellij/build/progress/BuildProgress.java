// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.progress;

import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEventPresentationData;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.BuildEventsNls.Title;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
@NonExtendable
public interface BuildProgress<T extends BuildProgressDescriptor> {

  @NotNull Object getId();

  @NotNull BuildProgress<T> start(@NotNull T descriptor);

  @NotNull BuildProgress<BuildProgressDescriptor> start(@NotNull @Message String message, @NotNull T descriptor);

  @NotNull BuildProgress<BuildProgressDescriptor> finish(@NotNull EventResult result);

  @NotNull BuildProgress<BuildProgressDescriptor> finish(@NotNull @Message String message, @NotNull EventResult result);

  @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp, @NotNull @Message String title, @NotNull EventResult result);

  @NotNull BuildProgress<BuildProgressDescriptor> finish();

  @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp);

  @NotNull BuildProgress<BuildProgressDescriptor> finish(boolean isUpToDate);

  @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp, boolean isUpToDate, @Message @NotNull String message);

  @NotNull BuildProgress<BuildProgressDescriptor> fail();

  @NotNull BuildProgress<BuildProgressDescriptor> fail(long timeStamp, @Message @NotNull String message);

  @NotNull BuildProgress<BuildProgressDescriptor> cancel();

  @NotNull BuildProgress<BuildProgressDescriptor> cancel(long timeStamp, @Message @NotNull String message);

  @NotNull BuildProgress<BuildProgressDescriptor> startChildProgress(@Title @NotNull String title);

  @NotNull BuildProgress<T> progress(@ProgressTitle @NotNull String title);

  @NotNull BuildProgress<T> progress(@ProgressTitle @NotNull String title, long total, long progress, String unit);

  @NotNull BuildProgress<T> presentable(@Message @NotNull String title, @NotNull BuildEventPresentationData presentationData);

  @NotNull BuildProgress<T> output(@Message @NotNull String text, @NotNull ProcessOutputType processOutputType);

  /**
   * @deprecated Use {@link output} event with {@link ProcessOutputType} instead.
   */
  @Deprecated
  default @NotNull BuildProgress<T> output(@Message @NotNull String text, boolean stdOut) {
    return output(text, stdOut ? ProcessOutputType.STDOUT : ProcessOutputType.STDERR);
  }

  @NotNull BuildProgress<T> message(
    @Title @NotNull String title,
    @Message @NotNull String message,
    @NotNull MessageEvent.Kind kind,
    @Nullable Navigatable navigatable
  );

  @NotNull BuildProgress<T> fileMessage(
    @Title @NotNull String title,
    @Message @NotNull String message,
    @NotNull MessageEvent.Kind kind,
    @NotNull FilePosition filePosition
  );

  @NotNull BuildProgress<BuildProgressDescriptor> buildIssue(
    @NotNull BuildIssue issue,
    @NotNull MessageEvent.Kind kind
  );
}
