// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class JavaSdkUtil {
  /** @deprecated use {@link #getJdkClassesRoots(Path, boolean)} instead */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static @NotNull List<File> getJdkClassesRoots(@NotNull File home, boolean isJre) {
    return ContainerUtil.map(getJdkClassesRoots(home.toPath(), isJre), Path::toFile);
  }

  public static @NotNull List<Path> getJdkClassesRoots(@NotNull Path home, boolean isJre) {
    Path[] jarDirs;
    if ("Home".equals(home.getFileName().toString()) && Files.exists(home.resolve("../Classes/classes.jar"))) {
      Path libDir = home.resolve("lib");
      Path classesDir = home.resolveSibling("Classes");
      Path libExtDir = libDir.resolve("ext");
      Path libEndorsedDir = libDir.resolve("endorsed");
      jarDirs = new Path[]{libEndorsedDir, libDir, classesDir, libExtDir};
    }
    else if (Files.exists(home.resolve("lib/jrt-fs.jar"))) {
      jarDirs = new Path[0];
    }
    else {
      Path libDir = home.resolve(isJre ? "lib" : "jre/lib");
      Path libExtDir = libDir.resolve("ext");
      Path libEndorsedDir = libDir.resolve("endorsed");
      jarDirs = new Path[]{libEndorsedDir, libDir, libExtDir};
    }

    Set<String> pathFilter = CollectionFactory.createFilePathSet();
    List<Path> rootFiles = new ArrayList<>();
    if (Registry.is("project.structure.add.tools.jar.to.new.jdk", false)) {
      Path toolsJar = home.resolve("lib/tools.jar");
      if (Files.isRegularFile(toolsJar)) {
        rootFiles.add(toolsJar);
      }
    }
    for (Path jarDir : jarDirs) {
      if (jarDir != null && Files.isDirectory(jarDir)) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jarDir, "*.jar")) {
          for (Path jarFile : stream) {
            String jarFileName = jarFile.getFileName().toString();
            if (jarFileName.equals("alt-rt.jar") || jarFileName.equals("alt-string.jar")) {
              continue;  // filter out alternative implementations
            }
            String canonicalPath = getCanonicalPath(jarFile);
            if (canonicalPath == null || !pathFilter.add(canonicalPath)) {
              continue;  // filter out duplicate (symbolically linked) .jar files commonly found in OS X JDK distributions
            }
            rootFiles.add(jarFile);
          }
        }
        catch (IOException ignored) { }
      }
    }

    List<Path> ibmJdkLookupDirs = new ArrayList<>();
    ibmJdkLookupDirs.add(home.resolve(isJre ? "bin" : "jre/bin"));
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(home.resolve(isJre ? "lib" : "jre/lib"), Files::isDirectory)) {
      for (Path path : stream) ibmJdkLookupDirs.add(path);
    }
    catch (IOException ignored) { }
    for (Path candidate : ibmJdkLookupDirs) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(candidate, p -> p.getFileName().toString().startsWith("jclSC") && Files.isDirectory(p))) {
        for (Path dir : stream) {
          Path vmJar = dir.resolve("vm.jar");
          if (Files.isRegularFile(vmJar)) {
            rootFiles.add(vmJar);
          }
        }
      }
      catch (IOException ignored) { }
    }

    Path classesZip = home.resolve("lib/classes.zip");
    if (Files.isRegularFile(classesZip)) {
      rootFiles.add(classesZip);
    }

    if (rootFiles.isEmpty()) {
      Path classesDir = home.resolve("classes");
      if (Files.isDirectory(classesDir)) {
        rootFiles.add(classesDir);
      }
    }

    return rootFiles;
  }

  private static @Nullable String getCanonicalPath(Path file) {
    try {
      return file.toRealPath().toString();
    }
    catch (IOException e) {
      return null;
    }
  }
}
