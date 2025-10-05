// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

import com.intellij.compiler.YourKitProfilerService;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

final class LocalBuildCommandLineBuilder implements BuildCommandLineBuilder {
  private final GeneralCommandLine myCommandLine = new GeneralCommandLine();

  LocalBuildCommandLineBuilder(String vmExecutablePath) {
    myCommandLine.withExePath(vmExecutablePath);
  }

  @Override
  public void addParameter(@NotNull String parameter) {
    myCommandLine.addParameter(parameter);
  }

  @Override
  public void addPathParameter(@NotNull String prefix, @NotNull String path) {
    myCommandLine.addParameter(prefix + path);
  }

  @Override
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public void addClasspathParameter(List<String> classpathInHost, List<String> classpathInTarget) {
    StringBuilder builder = new StringBuilder();
    for (String file : classpathInHost) {
      if (!builder.isEmpty()) {
        builder.append(java.io.File.pathSeparator);
      }
      builder.append(FileUtil.toCanonicalPath(file));
    }
    for (String s : classpathInTarget) {
      if (!builder.isEmpty()) {
        builder.append(java.io.File.pathSeparator);
      }
      builder.append(getHostWorkingDirectory().resolve(s));
    }
    myCommandLine.addParameter(builder.toString());
  }

  @Override
  public @NotNull String getWorkingDirectory() {
    return FileUtil.toSystemIndependentName(getHostWorkingDirectory().toString());
  }

  @Override
  public @NotNull Path getHostWorkingDirectory() {
    return getLocalBuildSystemDirectory();
  }

  @Override
  public String getYjpAgentPath(YourKitProfilerService yourKitProfilerService) {
    return getLocalBuildSystemDirectory()
      .resolve(yourKitProfilerService.getYKAgentFullName())
      .toAbsolutePath().toString();
  }

  @Override
  public void setCharset(Charset charset) {
    myCommandLine.withCharset(charset);
  }

  @Override
  public GeneralCommandLine buildCommandLine() {
    return myCommandLine.withWorkingDirectory(getHostWorkingDirectory());
  }

  @Override
  public void setUnixProcessPriority(int priority) {
    if (!SystemInfo.isUnix) {
      throw new IllegalArgumentException("'setUnixProcessPriority' must be used only on Unix operating systems");
    }

    setUnixProcessPriority(myCommandLine, priority);
  }

  @Override
  public void setStartNewSession() {
    if (!SystemInfo.isLinux) {
      throw new IllegalArgumentException("'setNewProcessGroup' must be used only on Linux");
    }

    myCommandLine.withWrappingCommand("setsid", "-w");
  }

  static @NotNull Path getLocalBuildSystemDirectory() {
    return PathManager.getSystemDir().resolve(BuildManager.SYSTEM_ROOT);
  }

  static void setUnixProcessPriority(GeneralCommandLine commandLine, int priority) {
    if (priority < 0 || priority > 19) {
      throw new IllegalArgumentException("priority must be in the [0..19] range: " + priority);
    }

    commandLine.withWrappingCommand("nice", "-n", Integer.toString(priority));
  }
}
