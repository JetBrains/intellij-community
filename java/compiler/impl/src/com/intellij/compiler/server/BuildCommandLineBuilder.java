// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

import com.intellij.compiler.YourKitProfilerService;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.List;

interface BuildCommandLineBuilder {
  void addParameter(@NotNull String parameter);
  void addPathParameter(@NotNull String prefix, @NotNull String path);

  default void addPathParameter(@NotNull String prefix, @NotNull Path path) {
    addPathParameter(prefix, path.toString());
  }

  void addClasspathParameter(List<String> classpathInHost, List<String> classpathInTarget);

  @NotNull String getWorkingDirectory();

  @NotNull Path getHostWorkingDirectory();

  String getYjpAgentPath(YourKitProfilerService yourKitProfilerService);

  void setCharset(Charset charset);

  GeneralCommandLine buildCommandLine() throws ExecutionException;

  /**
   * Uses {@code nice} command to run the build process with a lower priority.
   * Doesn't work on Windows because {@code start /low} does not pass the exit code.
   *
   * @param priority Unix process priority ({@code [0..19]}) (see the <a href="https://en.wikipedia.org/wiki/Nice_(Unix)">Wikipedia article</a>)
   */
  void setUnixProcessPriority(int priority);

  /**
   * Starts the build process in a new session so that the scheduler assigns it to a new autogroup
   * (see the "The autogroup feature" section in <a href="https://man7.org/linux/man-pages/man7/sched.7.html">sched(7)</a>;
   * <a href="https://man7.org/linux/man-pages/man1/setsid.1.html">setsid(1)</a>).
   * Linux-only.
   */
  default void setStartNewSession() { }

  default void setupAdditionalVMOptions() { }

  /**
   * @param path a path to a project-agnostic file which is available locally to the IDE.
   * @return a path which points to a copy on a remote machine, and is available to the IDE (but maybe not to the OS of the IDE)
   * i.e., in case of WSL the original path could be {@code C:\Users\a.zip}, and the returned path would be {@code \\wsl.localhost\home\a.zip}.
   * The file will be copied to a project-independent location; if the file already exists on the target machine, it will not be copied,
   * and the path to the existing file will be returned.
   * This method could be used to copy all the project-independent files like non-snapshot libraries, java agents and other tooling.
   */
  default @NotNull Path copyProjectAgnosticPathToTargetIfRequired(@NotNull Path path) throws FileSystemException {
    return path;
  }

  /**
   * @param path a path to a project-specific which is available locally to the IDE that can be used only with specific {@link project}.
   * @return a path which points to a copy on a remote machine, and is available to the IDE (but maybe not to the OS of the IDE)
   * i.e., in case of WSL the original path could be {@code C:\Users\a.zip}, and the returned path would be {@code \\wsl.localhost\home\a.zip}.
   * The file will be copied to a project-dependent location; if the file already exists on the target machine, it will not be copied,
   * and the path to the existing file will be returned.
   * This method could be used to copy project-dependent files like metadata for a compiler.
   */
  @ApiStatus.Experimental
  default @NotNull Path copyProjectSpecificPathToTargetIfRequired(@NotNull Project project, @NotNull Path path) throws FileSystemException {
    return path;
  }
}
