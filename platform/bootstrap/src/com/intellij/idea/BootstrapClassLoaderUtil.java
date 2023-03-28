// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.StringTokenizer;

@ApiStatus.Internal
public final class BootstrapClassLoaderUtil {
  static final @NonNls String MARKETPLACE_PLUGIN_DIR = "marketplace";

  private BootstrapClassLoaderUtil() { }

  // for CWM
  // Marketplace plugin, PROPERTY_IGNORE_CLASSPATH and PROPERTY_ADDITIONAL_CLASSPATH is not supported by intention
  public static @NotNull Collection<Path> getProductClassPath() throws IOException {
    Path distDir = Path.of(PathManager.getHomePath());
    Path libDir = distDir.resolve("lib");
    Collection<Path> classpath = new LinkedHashSet<>();

    parseClassPathString(System.getProperty("java.class.path"), classpath);

    Class<BootstrapClassLoaderUtil> aClass = BootstrapClassLoaderUtil.class;
    String selfRootPath = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    assert selfRootPath != null;
    Path selfRoot = Path.of(selfRootPath);
    classpath.add(selfRoot);
    addLibraries(classpath, libDir, selfRoot);
    addLibraries(classpath, libDir.resolve("ant/lib"), null);
    return classpath;
  }

  static @NotNull Path findMarketplaceBootDir(Path pluginDir) {
    return pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).resolve("lib/boot");
  }

  static boolean isMarketplacePluginCompatible(@NotNull Path homePath, @NotNull Path pluginDir, @NotNull Path mpBoot) {
    if (Files.notExists(mpBoot)) {
      return false;
    }

    try {
      SimpleVersion ideVersion = null;
      try (BufferedReader reader = Files.newBufferedReader(homePath.resolve("build.txt"))) {
        ideVersion = SimpleVersion.parse(reader.readLine());
      }
      catch (IOException ignored){
      }
      if (ideVersion == null && SystemInfoRt.isMac) {
        try (BufferedReader reader = Files.newBufferedReader(homePath.resolve("Resources/build.txt"))) {
          ideVersion = SimpleVersion.parse(reader.readLine());
        }
      }
      if (ideVersion != null) {
        SimpleVersion sinceVersion = null;
        SimpleVersion untilVersion = null;
        try (BufferedReader reader = Files.newBufferedReader(pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).resolve("platform-build.txt"))) {
          sinceVersion = SimpleVersion.parse(reader.readLine());
          untilVersion = SimpleVersion.parse(reader.readLine());
        }
        catch (IOException ignored) {
        }
        return ideVersion.isCompatible(sinceVersion, untilVersion);
      }
    }
    catch (Throwable ignored) {
    }
    return true;
  }

  private static void addLibraries(@NotNull Collection<Path> classPath, @NotNull Path fromDir, @Nullable Path selfRoot) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(fromDir)) {
      for (Path file : dirStream) {
        String path = file.toString();
        int lastDotIndex = path.length() - 4;
        if (lastDotIndex > 0 &&
            path.charAt(lastDotIndex) == '.' &&
            (path.regionMatches(true, lastDotIndex + 1, "jar", 0, 3) || path.regionMatches(true, lastDotIndex + 1, "zip", 0, 3))) {
          if (selfRoot == null || !selfRoot.equals(file)) {
            classPath.add(file);
          }
        }
      }
    }
    catch (NoSuchFileException ignore) {
    }
  }

  private static void parseClassPathString(@Nullable String pathString, @NotNull Collection<? super Path> classpath) {
    if (pathString == null || pathString.isEmpty()) {
      return;
    }

    StringTokenizer tokenizer = new StringTokenizer(pathString, File.pathSeparator + ',', false);
    while (tokenizer.hasMoreTokens()) {
      classpath.add(Path.of(tokenizer.nextToken()));
    }
  }

  private static final class SimpleVersion implements Comparable<SimpleVersion>{
    private final int myMajor;
    private final int myMinor;

    private SimpleVersion(int major, int minor) {
      myMajor = major;
      myMinor = minor;
    }

    private boolean isAtLeast(@NotNull Comparable<? super SimpleVersion> ver) {
      return ver.compareTo(this) <= 0;
    }

    private boolean isCompatible(@Nullable SimpleVersion since, @Nullable SimpleVersion until) {
      if (since != null && until != null) {
        return compareTo(since) >= 0 && compareTo(until) <= 0;
      }
      if (since != null) {
        return isAtLeast(since);
      }
      if (until != null) {
        return until.isAtLeast(this);
      }
      return true; // assume compatible of nothing is specified
    }

    @Override
    public int compareTo(@NotNull SimpleVersion ver) {
      return myMajor != ver.myMajor? Integer.compare(myMajor, ver.myMajor) : Integer.compare(myMinor, ver.myMinor);
    }

    private static @Nullable SimpleVersion parse(@Nullable String text) {
      if (text == null || text.isEmpty()) {
        return null;
      }

      try {
        text = text.trim();
        int dash = text.lastIndexOf('-');
        if (dash >= 0) {
          text = text.substring(dash + 1); // strip product code
        }
        int dot = text.indexOf('.');
        if (dot >= 0) {
          return new SimpleVersion(Integer.parseInt(text.substring(0, dot)), parseMinor(text.substring(dot + 1)));
        }
        return new SimpleVersion(Integer.parseInt(text), 0);
      }
      catch (NumberFormatException ignored) {
      }
      return null;
    }

    private static int parseMinor(String text) {
      try {
        if ("*".equals(text) || "SNAPSHOT".equals(text)) {
          return Integer.MAX_VALUE;
        }
        final int dot = text.indexOf('.');
        return Integer.parseInt(dot >= 0 ? text.substring(0, dot) : text);
      }
      catch (NumberFormatException ignored) {
      }
      return 0;
    }
  }
}
