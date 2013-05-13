/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Simonchik
 */
public class PathEnvironmentVariableUtil {

  private static final Logger LOG = Logger.getInstance(PathEnvironmentVariableUtil.class);

  private PathEnvironmentVariableUtil() { }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in PATH environment variable.
   *
   * @param fileBaseName file base name
   * @return {@code File} instance or null if not found
   */
  @Nullable
  public static File findInPath(@NotNull String fileBaseName) {
    return findInPath(fileBaseName, false);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in PATH environment variable.
   *
   * @param fileBaseName file base name
   * @param  logFindDetails true if extra logging is needed
   * @return {@code File} instance or null if not found
   */
  @Nullable
  public static File findInPath(@NotNull String fileBaseName, boolean logFindDetails) {
    List<File> exeFiles = findExeFilesInPath(fileBaseName, true, logFindDetails);
    return exeFiles.size() > 0 ? exeFiles.get(0) : null;
  }

  /**
   * Finds all executable files with the specified base name, that are located in directories
   * from PATH environment variable.
   *
   * @param fileBaseName file base name
   * @return file list
   */
  @NotNull
  public static List<File> findAllExeFilesInPath(@NotNull String fileBaseName) {
    return findExeFilesInPath(fileBaseName, false, false);
  }

  @NotNull
  private static List<File> findExeFilesInPath(@NotNull String fileBaseName,
                                               boolean stopAfterFirstMatch,
                                               boolean logFindDetails) {
    String systemPath = EnvironmentUtil.getValue("PATH");
    if (logFindDetails) {
      LOG.info("Finding files in PATH (base name=" + fileBaseName + ", PATH=" + systemPath + ").");
    }
    if (systemPath == null) {
      return Collections.emptyList();
    }
    List<File> result = new SmartList<File>();
    List<String> paths = StringUtil.split(systemPath, File.pathSeparator, true, true);
    for (String path : paths) {
      File dir = new File(path);
      if (logFindDetails) {
        File file = new File(dir, fileBaseName);
        LOG.info("path:" + path + ", path.isAbsolute:" + dir.isAbsolute() + ", path.isDirectory:" + dir.isDirectory()
                 + ", file.isFile:" + file.isFile() + ", file.canExecute:" + file.canExecute());
      }
      if (dir.isAbsolute() && dir.isDirectory()) {
        File file = new File(dir, fileBaseName);
        if (file.isFile() && file.canExecute()) {
          result.add(file);
          if (stopAfterFirstMatch) {
            return result;
          }
        }
      }
    }
    return result;
  }
}
