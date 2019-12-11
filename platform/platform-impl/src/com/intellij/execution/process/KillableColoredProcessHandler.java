// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * This process handler supports both ANSI coloring (see {@link ColoredProcessHandler})
 * and "soft-kill" feature (see {@link KillableProcessHandler}).
 */
public class KillableColoredProcessHandler extends ColoredProcessHandler implements KillableProcess {
  public KillableColoredProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    this(commandLine, false);
  }

  /**
   * Starts a process with a {@link RunnerMediator mediator} when {@code withMediator} is set to {@code true} and the platform is Windows.
   */
  public KillableColoredProcessHandler(@NotNull GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
    super(mediate(commandLine, withMediator, false));
    setShouldKillProcessSoftly(true);
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public KillableColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine) {
    super(process, commandLine);
    setShouldKillProcessSoftly(true);
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public KillableColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @NotNull Charset charset) {
    super(process, commandLine, charset);
    setShouldKillProcessSoftly(true);
  }

  /** @deprecated use {@link #KillableColoredProcessHandler(GeneralCommandLine, boolean)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static KillableColoredProcessHandler create(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new KillableColoredProcessHandler(commandLine, true);
  }

  public static class Silent extends KillableColoredProcessHandler {
    public Silent(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
      super(commandLine);
    }

    @NotNull
    @Override
    protected BaseOutputReader.Options readerOptions() {
      return BaseOutputReader.Options.forMostlySilentProcess();
    }
  }
}