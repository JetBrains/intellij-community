// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
}
