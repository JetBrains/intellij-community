// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.server;

import com.intellij.compiler.YourKitProfilerService;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

class LocalBuildCommandLineBuilder implements BuildCommandLineBuilder {
  private final GeneralCommandLine myCommandLine = new GeneralCommandLine();
  private final Project myProject;

  LocalBuildCommandLineBuilder(@NotNull Project project, String vmExecutablePath) {
    myProject = project;
    myCommandLine.setExePath(vmExecutablePath);
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
  public void addClasspathParameter(List<String> classpathInHost, List<String> classpathInTarget) {
    StringBuilder builder = new StringBuilder();
    for (String file : classpathInHost) {
      if (builder.length() > 0) {
        builder.append(File.pathSeparator);
      }
      builder.append(FileUtil.toCanonicalPath(file));
    }
    for (String s : classpathInTarget) {
      if (builder.length() > 0) {
        builder.append(File.pathSeparator);
      }
      builder.append(getHostWorkingDirectory().resolve(s).toString());
    }
    myCommandLine.addParameter(builder.toString());
  }

  @Override
  public @NotNull String getWorkingDirectory() {
    return PathKt.getSystemIndependentPath(getHostWorkingDirectory());
  }

  @Override
  @NotNull
  public Path getHostWorkingDirectory() {
    return BuildManager.getInstance().getBuildSystemDirectory(myProject);
  }

  @Override
  public InetAddress getListenAddress() {
    return InetAddress.getLoopbackAddress();
  }

  @Override
  public @NotNull String getHostIp() {
    return Boolean.parseBoolean(System.getProperty("java.net.preferIPv6Addresses", "false")) ? "::1" : "127.0.0.1";
  }

  @Override
  public String getYjpAgentPath(YourKitProfilerService yourKitProfilerService) {
    return BuildManager.getInstance().getBuildSystemDirectory(myProject)
      .resolve(yourKitProfilerService.getYKAgentFullName())
      .toAbsolutePath().toString();
  }

  @Override
  public void setCharset(Charset charset) {
    myCommandLine.setCharset(charset);
  }

  @Override
  public GeneralCommandLine buildCommandLine() {
    myCommandLine.setWorkDirectory(getHostWorkingDirectory().toFile());
    return myCommandLine;
  }
}
