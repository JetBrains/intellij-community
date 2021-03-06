// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.wsl.WSLDistribution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class JavaHomeFinderWsl extends JavaHomeFinderBasic {
  private static final Set<String> DEFAULT_PATHS = Set.of("/bin", "/usr/bin", "/usr/local/bin");

  private final WSLDistribution myDistro;

  JavaHomeFinderWsl(@NotNull WSLDistribution distro) {
    super(false, false, lookupPaths(distro));
    myDistro = distro;
  }

  private static String[] lookupPaths(WSLDistribution distro) {
    return Stream.of(JavaHomeFinder.DEFAULT_JAVA_LINUX_PATHS).map(distro::getWindowsPath).filter(Objects::nonNull).toArray(String[]::new);
  }

  @Override
  protected @Nullable String getEnvironmentVariable(@NotNull String name) {
    String value = myDistro.getEnvironmentVariable(name);
    if (value == null) {
      return null;
    }
    else if (value.indexOf(':') < 0) {
      return myDistro.getWindowsPath(value);
    }
    else {
      String mntRoot = myDistro.getMntRoot();
      String converted = Stream.of(value.split(":"))
        .filter(p -> !DEFAULT_PATHS.contains(p) && !p.startsWith(mntRoot))
        .map(myDistro::getWindowsPath)
        .filter(Objects::nonNull)
        .collect(Collectors.joining(File.pathSeparator));
      return converted.isEmpty() ? null : converted;
    }
  }

  @Override
  protected @Nullable Path getPathInUserHome(@NotNull String relativePath) {
    String wslPath = myDistro.getUserHome();
    if (wslPath != null) {
      String winPath = myDistro.getWindowsPath(wslPath);
      if (winPath != null) {
        return Path.of(winPath, relativePath);
      }
    }
    return null;
  }
}
