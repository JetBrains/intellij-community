// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.server;

import com.intellij.compiler.YourKitProfilerService;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

interface BuildCommandLineBuilder {
  void addParameter(@NotNull String parameter);
  void addPathParameter(@NotNull String prefix, @NotNull String path);

  default void addPathParameter(@NotNull String prefix, @NotNull Path path) {
    addPathParameter(prefix, path.toString());
  }

  void addClasspathParameter(List<String> classpathInHost, List<String> classpathInTarget);

  @NotNull
  String getWorkingDirectory();

  @NotNull
  Path getHostWorkingDirectory();

  InetAddress getListenAddress();

  @NotNull
  String getHostIp() throws ExecutionException;

  String getYjpAgentPath(YourKitProfilerService yourKitProfilerService);

  void setCharset(Charset charset);

  GeneralCommandLine buildCommandLine() throws ExecutionException;

  /**
   * Uses `nice` command to run process with a lower priority.
   *
   * Does not work on Windows since start /low does not pass exit code on Windows, see ExecUtil#setupLowPriorityExecution documentation
   *
   * @param priority Unix process priority (-20 <= priority <= 19), see https://en.wikipedia.org/wiki/Nice_(Unix)
   */
  void setUnixProcessPriority(int priority);

  default void setupAdditionalVMOptions() {
  }

  default @NotNull Path copyPathToTargetIfRequired(@NotNull Path path) {
    return path;
  }
}
