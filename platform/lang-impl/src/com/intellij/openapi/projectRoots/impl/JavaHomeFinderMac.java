// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApiStatus.Internal
public class JavaHomeFinderMac extends JavaHomeFinderBasic {
  public static final String JAVA_HOME_FIND_UTIL = "/usr/libexec/java_home";

  static String defaultJavaLocation = "/Library/Java/JavaVirtualMachines";

  public JavaHomeFinderMac(@NotNull JavaHomeFinder.SystemInfoProvider systemInfoProvider) {
    super(systemInfoProvider);

    registerFinder(() -> {
      Set<String> result = new TreeSet<>();
      Collection<@NotNull Path> roots = systemInfoProvider.getFsRoots();
      roots.forEach(root -> {
        result.addAll(scanAll(root.resolve(defaultJavaLocation), true));
      });
      roots.forEach(root -> {
        result.addAll(scanAll(root.resolve("System/Library/Java/JavaVirtualMachines"), true));
      });
      result.addAll(findJavaInstalledByBrew());
      return result;
    });

    registerFinder(() -> {
      Path jdk = getPathInUserHome("Library/Java/JavaVirtualMachines");
      return jdk != null ? scanAll(jdk, true) : Collections.emptySet();
    });
    registerFinder(() -> scanAll(getSystemDefaultJavaHome(), false));
  }

  protected @Nullable Path getSystemDefaultJavaHome() {
    String homePath = null;
    if (new File(JAVA_HOME_FIND_UTIL).canExecute()) {
      homePath = ExecUtil.execAndReadLine(new GeneralCommandLine(JAVA_HOME_FIND_UTIL));
    }
    if (homePath != null) {
      return Path.of(homePath);
    }
    return null;
  }

  @Override
  protected @NotNull List<Path> listPossibleJdkHomesFromInstallRoot(@NotNull Path path) {
    return Arrays.asList(path, path.resolve("Home"), path.resolve("Contents/Home"));
  }

  @Override
  protected @NotNull List<Path> listPossibleJdkInstallRootsFromHomes(@NotNull Path file) {
    List<Path> result = new ArrayList<>();
    result.add(file);

    Path home = file.getFileName();
    if (home != null && home.toString().equalsIgnoreCase("Home")) {
      Path parentFile = file.getParent();
      if (parentFile != null) {
        result.add(parentFile);

        Path contents = parentFile.getFileName();
        if (contents != null && contents.toString().equalsIgnoreCase("Contents")) {
          Path parentParentFile = parentFile.getParent();
          if (parentParentFile != null) {
            result.add(parentParentFile);
          }
        }
      }
    }

    return result;
  }

  private static void processSubfolders(@Nullable Path dir, Consumer<? super Path> processor) {
    if (dir == null || !Files.isDirectory(dir) || Files.isSymbolicLink(dir)) { return; }
    try (Stream<Path> files = Files.list(dir)) {
      files.forEach(candidate -> {
        if (Files.isDirectory(candidate)) {
          processor.accept(candidate);
        }
      });
    } catch (IOException ignore) {}
  }

  /**
   * Finds directories for JDKs installed by <a href="https://brew.sh/">homebrew</a>.
   */
  private @NotNull Set<String> findJavaInstalledByBrew() {
    var found = new HashSet<String>();
    var paths = List.of("/opt/homebrew/Cellar/", "/usr/homebrew/Cellar/");
    for (String path : paths) {
      Path parentFolder = getPathInUserHome(path);
      // JDKs installed by Homebrew can be hidden in `libexec`
      processSubfolders(parentFolder, formula -> {
        processSubfolders(formula, version -> {
          processSubfolders(version, subFolder -> {
            if (subFolder.getFileName().toString().equals("libexec")) {
              found.addAll(
                scanAll(subFolder, true).stream()
                  .filter(jdkPath -> !Files.isSymbolicLink(Path.of(jdkPath)))
                  .collect(Collectors.toSet())
              );
            }
          });
        });
      });
    }

    return found;
  }
}
