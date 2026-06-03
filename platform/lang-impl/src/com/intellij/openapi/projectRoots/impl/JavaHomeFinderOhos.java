// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public class JavaHomeFinderOhos extends JavaHomeFinderBasic {
  public static final String defaultJavaLocation = "/data/service/hnp";
  private static final int HNP_VENDOR_DIR_LEVELS = 3;

  public JavaHomeFinderOhos(@NotNull JavaHomeFinder.SystemInfoProvider systemInfoProvider) {
    super(systemInfoProvider);

    checkSpecifiedPaths(defaultJavaLocation);
  }

  @Override
  public @NotNull Set<String> findInJavaHome() {
    String javaHome = System.getProperty("java.home");
    if (javaHome != null) {
      return scanAll(Paths.get(javaHome), false);
    }
    return Collections.emptySet();
  }

  @Override
  protected @NotNull List<Path> listPossibleJdkHomesFromInstallRoot(@NotNull Path path) {
    Set<Path> result = new LinkedHashSet<>();
    collectPossibleJdkHomes(path, HNP_VENDOR_DIR_LEVELS - 1, result);
    return new ArrayList<>(result);
  }

  private static void collectPossibleJdkHomes(@NotNull Path path, int depth, @NotNull Set<? super Path> result) {
    result.add(path);
    if (depth <= 0) {
      return;
    }
    try (var children = Files.list(path)) {
      children
        .filter(Files::isDirectory)
        .forEach(child -> collectPossibleJdkHomes(child, depth - 1, result));
    }
    catch (IOException ignored) {
    }
  }
}
