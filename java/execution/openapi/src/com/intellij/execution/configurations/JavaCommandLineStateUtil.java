/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class JavaCommandLineStateUtil {
  private JavaCommandLineStateUtil() { }

  @NotNull
  public static OSProcessHandler startProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return startProcess(commandLine, false);
  }
  
  @NotNull
  public static OSProcessHandler startProcess(@NotNull GeneralCommandLine commandLine, boolean ansiColoring) throws ExecutionException {
    OSProcessHandler processHandler = ansiColoring ? new ColoredProcessHandler(commandLine) : new OSProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }
}
