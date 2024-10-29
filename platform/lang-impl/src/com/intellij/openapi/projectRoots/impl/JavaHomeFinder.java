// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public abstract class JavaHomeFinder {
  public static class SystemInfoProvider {
    public @Nullable String getEnvironmentVariable(@NotNull String name) {
      return EnvironmentUtil.getValue(name);
    }

    public @NotNull Path getPath(String path, String... more) {
      return Path.of(path, more);
    }

    public @Nullable Path getUserHome() {
      return Path.of(SystemProperties.getUserHome());
    }

    public @NotNull Collection<@NotNull Path> getFsRoots() {
      Iterable<Path> rootDirectories = FileSystems.getDefault().getRootDirectories();
      return rootDirectories != null ? ContainerUtil.newArrayList(rootDirectories) : Collections.emptyList();
    }

    public String getPathSeparator() {
      return File.pathSeparator;
    }

    public boolean isFileSystemCaseSensitive() {
      return SystemInfoRt.isFileSystemCaseSensitive;
    }
  }

  /**
   * Tries to find existing Java SDKs on this computer.
   * If no JDK found, returns possible directories to start file chooser.
   * @return suggested sdk home paths (sorted)
   */
  public static @NotNull List<String> suggestHomePaths() {
    return suggestHomePaths(false);
  }

  /**
   * Do the same as {@link #suggestHomePaths()} but always considers the embedded JRE,
   * for using in tests that are performed when the registry is not properly initialized
   * or that need the embedded JetBrains Runtime.
   */
  public static @NotNull List<String> suggestHomePaths(boolean forceEmbeddedJava) {
    JavaHomeFinderBasic javaFinder = getFinder(forceEmbeddedJava);
    if (javaFinder == null) return Collections.emptyList();

    ArrayList<String> paths = new ArrayList<>(javaFinder.findExistingJdks());
    paths.sort((o1, o2) -> Comparing.compare(JavaVersion.tryParse(o2), JavaVersion.tryParse(o1)));
    return paths;
  }

  private static boolean isDetectorEnabled(boolean forceEmbeddedJava) {
    return forceEmbeddedJava || Registry.is("java.detector.enabled", true);
  }

  private static JavaHomeFinderBasic getFinder(boolean forceEmbeddedJava) {
    if (!isDetectorEnabled(forceEmbeddedJava)) return null;

    return getFinder().checkEmbeddedJava(forceEmbeddedJava);
  }

  public static @NotNull JavaHomeFinderBasic getFinder() {
    SystemInfoProvider systemInfoProvider = new SystemInfoProvider();

    if (SystemInfo.isWindows) {
      return new JavaHomeFinderWindows(true, true, systemInfoProvider);
    }
    if (SystemInfo.isMac) {
      return new JavaHomeFinderMac(systemInfoProvider);
    }
    if (SystemInfo.isLinux) {
      return new JavaHomeFinderBasic(systemInfoProvider).checkSpecifiedPaths(DEFAULT_JAVA_LINUX_PATHS);
    }
    if (SystemInfo.isSolaris) {
      return new JavaHomeFinderBasic(systemInfoProvider).checkSpecifiedPaths("/usr/jdk");
    }

    return new JavaHomeFinderBasic(systemInfoProvider);
  }

  public static @Nullable String defaultJavaLocation() {
    if (SystemInfo.isWindows) {
      return JavaHomeFinderWindows.defaultJavaLocation;
    }
    if (SystemInfo.isMac) {
      return JavaHomeFinderMac.defaultJavaLocation;
    }
    if (SystemInfo.isLinux) {
      return "/opt/java";
    }
    if (SystemInfo.isSolaris) {
      return "/usr/jdk";
    }
    return null;
  }

  public static final String[] DEFAULT_JAVA_LINUX_PATHS = {"/usr/java", "/opt/java", "/usr/lib/jvm"};
}
