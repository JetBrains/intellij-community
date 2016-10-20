/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

public abstract class ProcessHandlerFactory {

  public static ProcessHandlerFactory getInstance() {
    return ServiceManager.getService(ProcessHandlerFactory.class);
  }

  /**
   * Returns a new instance of the {@link OSProcessHandler}.
   */
  @NotNull
  public abstract OSProcessHandler createProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException;

  /**
   * Returns a new instance of the {@link OSProcessHandler} which is aware of ANSI coloring output.
   */
  @NotNull
  public abstract OSProcessHandler createColoredProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException;

}
