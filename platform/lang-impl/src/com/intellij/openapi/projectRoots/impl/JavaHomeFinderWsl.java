// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
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
    List<String> list = new ArrayList<>();
    for (String defaultPath : JavaHomeFinder.DEFAULT_JAVA_LINUX_PATHS) {
      String path = distro.getWindowsPath(defaultPath);
      if (path != null) {
        list.add(path);
      }
    }
    String home = distro.getUserHome();
    if (home != null) {
      list.add(distro.getWindowsPath(home + "/.jdks"));
    }
    return ArrayUtil.toStringArray(list);
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
      Map<String, String> mntRoots = myDistro.getDriveMountDirs();
      String converted = Stream.of(value.split(":"))
        .filter(p -> !DEFAULT_PATHS.contains(p) && !inMountedRoots(p, mntRoots))
        .map(myDistro::getWindowsPath)
        .filter(Objects::nonNull)
        .collect(Collectors.joining(File.pathSeparator));
      return converted.isEmpty() ? null : converted;
    }
  }

  private static boolean inMountedRoots(String p, Map<String, String> roots) {
    return roots.entrySet().stream().anyMatch(e -> p.startsWith(e.getKey()));
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
