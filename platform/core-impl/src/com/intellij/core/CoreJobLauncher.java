// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The naive implementation of {@link JobLauncher} which executes all tasks sequentially
 */
@ApiStatus.Internal
public class CoreJobLauncher extends JobLauncher {
  @Override
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                     @NotNull ProgressIndicator progress,
                                                     boolean runInReadAction,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull Processor<? super T> thingProcessor) {
    return ContainerUtil.process(things, thingProcessor);
  }
}
