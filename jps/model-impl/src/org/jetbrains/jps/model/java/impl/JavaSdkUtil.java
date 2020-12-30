// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class JavaSdkUtil {
  private static final Path[] EMPTY_PATH_ARRAY = new Path[0];

  /**
   * @deprecated use {@link #getJdkClassesRoots(Path, boolean)} instead
   */
  @Deprecated
  @NotNull
  public static List<File> getJdkClassesRoots(@NotNull File home, boolean isJre) {
    return ContainerUtil.map(getJdkClassesRoots(home.toPath(), isJre), Path::toFile);
  }

  @NotNull
  public static List<Path> getJdkClassesRoots(@NotNull Path home, boolean isJre) {
    Path[] jarDirs;
    if (SystemInfo.isMac && !home.getFileName().startsWith("mockJDK")) {
      Path openJdkRtJar = home.resolve("jre/lib/rt.jar");
      if (Files.isReadable(openJdkRtJar)) {
        Path libDir = home.resolve("lib");
        Path classesDir = openJdkRtJar.getParent();
        Path libExtDir = openJdkRtJar.resolveSibling("ext");
        Path libEndorsedDir = libDir.resolve("endorsed");
        jarDirs = new Path[]{libEndorsedDir, libDir, classesDir, libExtDir};
      }
      else {
        Path libDir = home.resolve("lib");
        Path classesDir = home.resolveSibling("Classes");
        Path libExtDir = libDir.resolve("ext");
        Path libEndorsedDir = libDir.resolve("endorsed");
        jarDirs = new Path[]{libEndorsedDir, libDir, classesDir, libExtDir};
      }
    }
    else if (Files.exists(home.resolve("lib/jrt-fs.jar"))) {
      jarDirs = EMPTY_PATH_ARRAY;
    }
    else {
      Path libDir = home.resolve(isJre ? "lib" : "jre/lib");
      Path libExtDir = libDir.resolve("ext");
      Path libEndorsedDir = libDir.resolve("endorsed");
      jarDirs = new Path[]{libEndorsedDir, libDir, libExtDir};
    }

    Predicate<Path> jarFileFilter = path -> FileUtilRt.extensionEquals(path.toString(), "jar");
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
        for (Path jarFile : listFiles(jarDir, jarFileFilter)) {
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
    }

    List<Path> ibmJdkLookupDirs = ContainerUtil.newArrayList(home.resolve(isJre ? "bin" : "jre/bin"));
    ContainerUtil.addAll(ibmJdkLookupDirs, listFiles(home.resolve(isJre ? "lib" : "jre/lib"), path -> Files.isDirectory(path)));
    for (Path candidate : ibmJdkLookupDirs) {
      Path[] vmJarDirs =
        listFiles(candidate.resolve("default"), f -> f.getFileName().toString().startsWith("jclSC") && Files.isDirectory(f));
      for (Path dir : vmJarDirs) {
        Path vmJar = dir.resolve("vm.jar");
        if (Files.isRegularFile(vmJar)) {
          rootFiles.add(vmJar);
        }
      }
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

  private static Path[] listFiles(Path dir, Predicate<Path> filter) {
    try {
      return Files.list(dir).filter(filter).toArray(Path[]::new);
    }
    catch (IOException e) {
      return EMPTY_PATH_ARRAY;
    }
  }

  @Nullable
  private static String getCanonicalPath(Path file) {
    try {
      return file.toRealPath().toString();
    }
    catch (IOException e) {
      return null;
    }
  }
}