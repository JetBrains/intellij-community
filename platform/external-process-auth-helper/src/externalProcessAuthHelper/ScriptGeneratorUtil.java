// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public final class ScriptGeneratorUtil {

  private ScriptGeneratorUtil() { }

  private static @NotNull File createBatchScript(@NotNull @NonNls String fileName, @NotNull @NonNls String commandLine) throws IOException {
    String batchScriptText = "@echo off" + "\r\n" + commandLine + " %*\r\n";
    return createTempExecutable(fileName + ".bat", batchScriptText);
  }

  private static @NotNull File createShellScript(@NotNull @NonNls String fileName, @NotNull @NonNls String commandLine) throws IOException {
    String shellText = "#!/bin/sh\n" + commandLine + " \"$@\"\n";
    return createTempExecutable(fileName + ".sh", shellText);
  }

  private static @NotNull File createTempExecutable(@NotNull @NonNls String fileName, @NotNull @NonNls String content) throws IOException {
    File file = new File(PathManager.getTempPath(), fileName);
    FileUtil.writeToFile(file, content);
    FileUtil.setExecutable(file);
    return file;
  }

  public static @NotNull File createTempScript(@NotNull String commandLine, @NotNull String fileNamePrefix, boolean useBatchFile)
    throws IOException {
    return useBatchFile ? createBatchScript(fileNamePrefix, commandLine)
                        : createShellScript(fileNamePrefix, commandLine);
  }

  /**
   * @return jar or directory that contains the class for the classpath
   */
  public static @NotNull File getJarFileFor(@NotNull Class<?> clazz) {
    return new File(PathUtil.getJarPathForClass(clazz));
  }
}
