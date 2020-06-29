// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class CompilerProfilerUtil {
  public static final String YJP_JAR = "yjp-controller-api-redist.jar";

  private static String getYKAgentFullLibraryName() throws IOException {
    String agent = "yjpagent";
    if (SystemInfo.isMac) {
      agent = "lib" + agent + ".dylib";
    }
    else if (SystemInfo.isLinux) {
      agent = "lib" + agent;
      if (SystemInfo.is64Bit) {
        agent += "64";
      }
      agent += ".so";
    }
    else if (SystemInfo.isWindows) {
      if (SystemInfo.is64Bit) {
        agent += "64";
      }
      agent += ".dll";
    }
    else {
      throw new IOException("YK profiling can't be enabled. OS is not supported.");
    }
    return agent;
  }

  private static String getYKFilePath(String pathInsidePlugin) throws IOException {
    File bundledPlugin = Paths.get(PathManager.getPreInstalledPluginsPath(), "performanceTesting").toFile();
    File installedPlugin = Paths.get(PathManager.getPluginsPath(), "performanceTesting").toFile();
    String folderPath;
    if (installedPlugin.exists()) {
      folderPath = installedPlugin.getAbsolutePath();
    }
    else if (bundledPlugin.exists()) {
      folderPath = bundledPlugin.getAbsolutePath();
    }
    else {
      throw new IOException("There are no binaries in performance plugin");
    }
    return Paths.get(folderPath, pathInsidePlugin).toString();
  }

  public static void copyYKLibraries(Path workDirectory) throws IOException {
    Path pathToYKAgent = workDirectory.resolve(getYKAgentFullLibraryName());
    Files.copy(Paths.get(getYKFilePath("bin/" + getYKAgentFullLibraryName())), pathToYKAgent, StandardCopyOption.REPLACE_EXISTING);
    Files.copy(Paths.get(getYKFilePath("lib/" + YJP_JAR)), workDirectory.resolve(YJP_JAR), StandardCopyOption.REPLACE_EXISTING);
  }

  public static String getYJPAgentPath(Path workDirectory) throws IOException {
    return workDirectory.resolve(getYKAgentFullLibraryName()).toAbsolutePath().toString();
  }
}
