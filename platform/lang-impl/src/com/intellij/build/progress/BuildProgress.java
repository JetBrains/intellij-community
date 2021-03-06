// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.progress;

import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.BuildIssueEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface BuildProgress<T extends BuildProgressDescriptor> {
  @NotNull Object getId();

  @NotNull BuildProgress<T> start(@NotNull T descriptor);

  @NotNull BuildProgress<T> progress(@NlsContexts.ProgressTitle @NotNull String title);

  @NotNull BuildProgress<T> progress(@NlsContexts.ProgressTitle @NotNull String title, long total, long progress, String unit);

  @NotNull BuildProgress<T> output(@BuildEventsNls.Message @NotNull String text, boolean stdOut);

  @NotNull BuildProgress<T> message(@BuildEventsNls.Title @NotNull String title,
                                    @BuildEventsNls.Message @NotNull String message,
                                    @NotNull MessageEvent.Kind kind,
                                    @Nullable Navigatable navigatable);

  @NotNull BuildProgress<T> fileMessage(@BuildEventsNls.Title @NotNull String title,
                                        @BuildEventsNls.Message @NotNull String message,
                                        @NotNull MessageEvent.Kind kind,
                                        @NotNull FilePosition filePosition);

  @NotNull BuildProgress<BuildProgressDescriptor> finish();

  @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp);

  @NotNull BuildProgress<BuildProgressDescriptor> finish(boolean isUpToDate);

  @NotNull BuildProgress<BuildProgressDescriptor> finish(long timeStamp, boolean isUpToDate, @BuildEventsNls.Message @NotNull String message);

  @NotNull BuildProgress<BuildProgressDescriptor> fail();

  @NotNull BuildProgress<BuildProgressDescriptor> fail(long timeStamp, @BuildEventsNls.Message @NotNull String message);

  @NotNull BuildProgress<BuildProgressDescriptor> cancel();

  @NotNull BuildProgress<BuildProgressDescriptor> cancel(long timeStamp, @BuildEventsNls.Message @NotNull String message);

  @NotNull BuildProgress<BuildProgressDescriptor> startChildProgress(@BuildEventsNls.Title @NotNull String title);

  @NotNull BuildProgress<BuildProgressDescriptor>  buildIssue(@NotNull BuildIssue issue, @NotNull MessageEvent.Kind kind);
}
