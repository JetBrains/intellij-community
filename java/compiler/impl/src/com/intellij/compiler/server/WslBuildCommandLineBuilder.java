// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.server;

import com.intellij.compiler.YourKitProfilerService;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.wsl.WSLCommandLineOptions;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

final class WslBuildCommandLineBuilder implements BuildCommandLineBuilder {
  private static final Logger LOG = Logger.getInstance(WslBuildCommandLineBuilder.class);
  private final Project myProject;
  private final @NotNull WSLDistribution myDistribution;
  private final @Nullable ProgressIndicator myProgressIndicator;
  private final GeneralCommandLine myCommandLine = new GeneralCommandLine();
  private final @NotNull String myWorkingDirectory;
  private final @NotNull String myHostWorkingDirectory;
  private final @Nullable String myClasspathDirectory;
  private final @Nullable Path myHostClasspathDirectory;

  private static boolean CURRENT_SNAPSHOT_COPIED = false;
  private boolean myReportedProgress;

  WslBuildCommandLineBuilder(@NotNull Project project, @NotNull WSLDistribution distribution, @NotNull String sdkPath,
                             @Nullable ProgressIndicator progressIndicator) {
    myProject = project;
    myDistribution = distribution;
    myProgressIndicator = progressIndicator;
    myCommandLine.setExePath(sdkPath);

    Path buildDirectory = getWslBuildSystemDirectory(distribution);
    if (buildDirectory == null) {
      LOG.warn("Cannot determine build directory for " + distribution + ", rolling back to LocalBuildSystemDirectory");
      myHostWorkingDirectory = LocalBuildCommandLineBuilder.getLocalBuildSystemDirectory().toString();
      myWorkingDirectory = myDistribution.getWslPath(myHostWorkingDirectory);
      LOG.warn("ClasspathDirectory and myHostClasspathDirectory set to null!");
      myClasspathDirectory = null;
      myHostClasspathDirectory = null;
    } else {
      myHostWorkingDirectory = buildDirectory.toString();
      myWorkingDirectory = myDistribution.getWslPath(myHostWorkingDirectory);
      myClasspathDirectory = myWorkingDirectory + "/jps-" + ApplicationInfo.getInstance().getBuild().asString();
      myHostClasspathDirectory = Paths.get(myDistribution.getWindowsPath(myClasspathDirectory));
      if (ApplicationInfo.getInstance().getBuild().isSnapshot() && !CURRENT_SNAPSHOT_COPIED) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        CURRENT_SNAPSHOT_COPIED = true;
        if (myProgressIndicator != null) {
          myProgressIndicator.setText(JavaCompilerBundle.message("progress.preparing.wsl.build.environment"));
          myReportedProgress = true;
        }
        try {
          FileUtil.delete(myHostClasspathDirectory);
        }
        catch (IOException e) {
          // ignore
        }
      }
    }
  }

  @Override
  public void addParameter(@NotNull String parameter) {
    myCommandLine.addParameter(parameter);
  }

  @Override
  public void addPathParameter(@NotNull String prefix, @NotNull String path) {
    myCommandLine.addParameter(prefix + myDistribution.getWslPath(path));
  }

  @Override
  public void addClasspathParameter(List<String> classpathInHost, List<String> classpathInTarget) {
    StringBuilder builder = new StringBuilder();
    myReportedProgress = false;
    for (String pathName : classpathInHost) {
      if (builder.length() > 0) {
        builder.append(":");
      }
      Path path = Paths.get(pathName);
      if (myClasspathDirectory != null && myHostClasspathDirectory != null) {
        Path targetPath = myHostClasspathDirectory.resolve(path.getFileName());
        try {
          if (!targetPath.toFile().exists()) {
            if (!myReportedProgress && myProgressIndicator != null) {
              myProgressIndicator.setText(JavaCompilerBundle.message("progress.preparing.wsl.build.environment"));
              myReportedProgress = true;
            }
            FileUtil.copyFileOrDir(path.toFile(), targetPath.toFile());
          }
          builder.append(myDistribution.getWslPath(targetPath.toString()));
          continue;
        }
        catch (IOException e) {
          // fallback to default case
        }
      }

      builder.append(myDistribution.getWslPath(pathName));
    }
    for (String s : classpathInTarget) {
      if (builder.length() > 0) {
        builder.append(":");
      }
      builder.append(myWorkingDirectory).append("/").append(s);
    }
    myCommandLine.addParameter(builder.toString());
  }

  @Override
  public void copyPathToTarget(Iterable<File> pathFiles) {
    if (myClasspathDirectory != null && myHostClasspathDirectory != null) {
      for (File file : pathFiles) {
        File targetFile = myHostClasspathDirectory.resolve(file.getName()).toFile();
        if (!targetFile.exists()) {
          try {
            FileUtil.copyFileOrDir(file, targetFile);
          }
          catch (IOException ignored) {
          }
        }
      }
    }
  }

  @Override
  public @NotNull Path getHostWorkingDirectory() {
    return Paths.get(myHostWorkingDirectory);
  }

  @Override
  public @NotNull String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  @Override
  public InetAddress getListenAddress() {
    return myDistribution.getHostIpAddress();
  }

  @Override
  public @NotNull String getHostIp() throws ExecutionException {
    String hostIp = myDistribution.getHostIp();
    if (hostIp == null) {
      throw new ExecutionException(JavaCompilerBundle.message("dialog.message.failed.to.determine.host.ip.for.wsl.jdk"));
    }
    return hostIp;
  }

  @Override
  public String getYjpAgentPath(YourKitProfilerService yourKitProfilerService) {
    return myWorkingDirectory + "/" + yourKitProfilerService.getYKAgentFullNameForWSL();
  }

  @Override
  public void setCharset(Charset charset) {
    myCommandLine.setCharset(charset);
  }

  @Override
  public void setupAdditionalVMOptions() {
    addParameter("-D" + GlobalOptions.JPS_IN_WSL_OPTION + "=true");
  }

  @Override
  public GeneralCommandLine buildCommandLine() throws ExecutionException {
    WSLCommandLineOptions options = new WSLCommandLineOptions();
    options.setRemoteWorkingDirectory(myWorkingDirectory);
    myDistribution.patchCommandLine(myCommandLine, myProject, options);

    return myCommandLine;
  }

  @Override
  public void setUnixProcessPriority(int priority) {
    LocalBuildCommandLineBuilder.setUnixProcessPriority(myCommandLine, priority);
  }

  @Nullable
  public static Path getWslBuildSystemDirectory(WSLDistribution distribution) {
    String pathsSelector = PathManager.getPathsSelector();
    String wslUserHome = distribution.getUserHome();
    if (wslUserHome == null) return null;
    String windowsUserHomePath = distribution.getWindowsPath(wslUserHome);
    if (pathsSelector == null) pathsSelector = "." + ApplicationNamesInfo.getInstance().getScriptName();
    if (windowsUserHomePath == null) return null;
    String workingDirectory = PathManager.getDefaultUnixSystemPath(windowsUserHomePath, pathsSelector) + "/" + BuildManager.SYSTEM_ROOT;
    return Paths.get(workingDirectory);
  }
}
