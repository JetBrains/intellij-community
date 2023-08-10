// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
      return Paths.get(homePath);
    }
    return null;
  }

  @NotNull
  @Override
  protected List<Path> listPossibleJdkHomesFromInstallRoot(@NotNull Path path) {
    return Arrays.asList(path, path.resolve("/Home"), path.resolve("Contents/Home"));
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
}
