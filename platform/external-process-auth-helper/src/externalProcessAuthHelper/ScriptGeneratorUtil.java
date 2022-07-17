// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class ScriptGeneratorUtil {
  @NotNull
  private static File createBatchScript(@NotNull @NonNls String fileName, @NotNull @NonNls String commandLine) throws IOException {
    @NonNls StringBuilder sb = new StringBuilder();
    sb.append("@echo off").append("\n");
    sb.append(commandLine).append(" %*").append("\n");
    return createTempExecutable(fileName + ".bat", sb.toString());
  }

  @NotNull
  private static File createShellScript(@NotNull @NonNls String fileName, @NotNull @NonNls String commandLine) throws IOException {
    @NonNls StringBuilder sb = new StringBuilder();
    sb.append("#!/bin/sh").append("\n");
    sb.append(commandLine).append(" \"$@\"").append("\n");
    return createTempExecutable(fileName + ".sh", sb.toString());
  }

  @NotNull
  private static File createTempExecutable(@NotNull @NonNls String fileName, @NotNull @NonNls String content) throws IOException {
    File file = new File(PathManager.getTempPath(), fileName);
    FileUtil.writeToFile(file, content);
    FileUtil.setExecutable(file);
    return file;
  }

  @NotNull
  public static File createTempScript(@NotNull String commandLine, @NotNull String fileNamePrefix, boolean useBatchFile)
    throws IOException {
    return useBatchFile ? createBatchScript(fileNamePrefix, commandLine)
                        : createShellScript(fileNamePrefix, commandLine);
  }

  /**
   * @return jar or directory that contains the class for the classpath
   */
  @NotNull
  public static File getJarFileFor(@NotNull Class<?> clazz) {
    return new File(PathUtil.getJarPathForClass(clazz));
  }
}
