// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class JavaHomeFinderWsl extends JavaHomeFinderBasic {
  private static final Set<String> DEFAULT_PATHS = Set.of("/bin", "/usr/bin", "/usr/local/bin");

  private final WSLDistribution myDistro;

  JavaHomeFinderWsl(@NotNull WSLDistribution distro) {
    super(new WslSystemInfoProvider(distro));
    myDistro = distro;
    checkDefaultInstallDir(false);
    checkUsedInstallDirs(false);
    checkConfiguredJdks(false);
    checkSpecifiedPaths(lookupPaths(distro));
  }

  private static String[] lookupPaths(WSLDistribution distro) {
    List<String> list = new ArrayList<>();
    for (String defaultPath : JavaHomeFinder.DEFAULT_JAVA_LINUX_PATHS) {
      list.add(distro.getWindowsPath(defaultPath));
    }
    String home = distro.getUserHome();
    if (home != null) {
      list.add(distro.getWindowsPath(home + "/.jdks"));
    }
    return ArrayUtil.toStringArray(list);
  }

  @Override
  protected @Nullable Path getPathInUserHome(@NotNull String relativePath) {
    String wslPath = myDistro.getUserHome();
    if (wslPath != null) {
      return Path.of(myDistro.getWindowsPath(wslPath), relativePath);
    }
    return null;
  }

  private static class WslSystemInfoProvider extends JavaHomeFinder.SystemInfoProvider {
    private final @NotNull WSLDistribution myDistro;

    private WslSystemInfoProvider(@NotNull WSLDistribution distro) { myDistro = distro; }

    @Override
    public @Nullable String getEnvironmentVariable(@NotNull String name) {
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
          .collect(Collectors.joining(File.pathSeparator));
        return converted.isEmpty() ? null : converted;
      }
    }

    @Override
    public @Nullable Path getUserHome() {
      String wslPath = myDistro.getUserHome();
      return wslPath != null ? Path.of(myDistro.getWindowsPath(wslPath)) : null;
    }
  }
}
