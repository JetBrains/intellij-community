/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface ExecutionListener extends EventListener {

  default void processStartScheduled(@NotNull String executorId, @NotNull ExecutionEnvironment env) {}
  
  default void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment env) {}

  default void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env) {}

  default void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {}

  default void processTerminating(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) { 
    processTerminating(env.getRunProfile(), handler);
  }
  
  default void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
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
