// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ExecutionListener extends EventListener {
  default void processStartScheduled(@NotNull String executorId, @NotNull ExecutionEnvironment env) {}

  default void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment env) {}

  default void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env) {}

  /**
   * Called before {@link ProcessHandler#startNotify()}.
   */
  default void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {}

  /**
   * Called after {@link ProcessHandler#startNotify()}.
   */
  default void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {}

  default void processTerminating(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
    processTerminating(env.getRunProfile(), handler);
  }

  default void processTerminated(@NotNull String executorId,
                                 @NotNull ExecutionEnvironment env,
                                 @NotNull ProcessHandler handler,
                                 int exitCode) {
    processTerminated(env.getRunProfile(), handler);
  }

  /**
   * @deprecated use {@link #processTerminating(String, ExecutionEnvironment, ProcessHandler)}
   */
  @Deprecated
  default void processTerminating(@NotNull RunProfile runProfile, @NotNull ProcessHandler handler) {}

  /**
   * @deprecated use {@link #processTerminated(String, ExecutionEnvironment, ProcessHandler, int)}
   */
  @Deprecated
  default void processTerminated(@NotNull RunProfile runProfile, @NotNull ProcessHandler handler) {}
}
