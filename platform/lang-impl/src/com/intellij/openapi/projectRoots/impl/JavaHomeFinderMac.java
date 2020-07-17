// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class JavaHomeFinderMac extends JavaHomeFinderBasic {
  public static final String JAVA_HOME_FIND_UTIL = "/usr/libexec/java_home";

  static String defaultJavaLocation = "/Library/Java/JavaVirtualMachines";

  JavaHomeFinderMac(boolean forceEmbeddedJava) {
    super(forceEmbeddedJava,
          defaultJavaLocation,
          "/System/Library/Java/JavaVirtualMachines",
          FileUtil.expandUserHome("~/Library/Java/JavaVirtualMachines")
    );

    registerFinder(() -> scanAll(getSystemDefaultJavaHome(), false));
  }

  private static @Nullable Path getSystemDefaultJavaHome() {
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
  protected List<File> listPossibleJdkHomesFromInstallRoot(@NotNull File file) {
    return Arrays.asList(file, new File(file, "/Home"), new File(file, "Contents/Home"));
  }

  @Override
  protected @NotNull List<Path> listPossibleJdkInstallRootsFromHomes(@NotNull Path file) {
    List<Path> result = new ArrayList<>();
    result.add(file);

    if (file.getFileName().toString().equalsIgnoreCase("Home")) {
      Path parentFile = file.getParent();
      if (parentFile != null) {
        result.add(parentFile);

        if (parentFile.getFileName().toString().equalsIgnoreCase("Contents")) {
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
