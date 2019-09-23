// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/** @deprecated use {@link OSProcessHandler} */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
@SuppressWarnings("unused")
public class DefaultJavaProcessHandler extends OSProcessHandler {
  public DefaultJavaProcessHandler(@NotNull JavaParameters javaParameters) throws ExecutionException {
    super(javaParameters.toCommandLine());
  }

  public DefaultJavaProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
  }

  public DefaultJavaProcessHandler(@NotNull Process process, @NotNull String commandLine, @NotNull Charset charset) {
    super(process, commandLine, charset);
  }
}