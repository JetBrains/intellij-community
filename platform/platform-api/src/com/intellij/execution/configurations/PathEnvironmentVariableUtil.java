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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.List;

/**
 * A collection of utility methods for working with PATH environment variable.
 */
public class PathEnvironmentVariableUtil {

  private static final Logger LOG = Logger.getInstance(PathEnvironmentVariableUtil.class);
  private static final String PATH_ENV_VAR_NAME = "PATH";

  private PathEnvironmentVariableUtil() { }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in PATH environment variable.
   *
   * @param fileBaseName file base name
   * @return {@link File} instance or null if not found
   */
  @Nullable
  public static File findInPath(@NotNull String fileBaseName) {
    return findInPath(fileBaseName, false);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in PATH environment variable and is accepted by filter.
   *
   * @param fileBaseName file base name
   * @param filter exe file filter
   * @return {@link File} instance or null if not found
   */
  @Nullable
  public static File findInPath(@NotNull String fileBaseName, @Nullable FileFilter filter) {
    return findInPath(fileBaseName, false, filter);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in PATH environment variable.
   *
   * @param fileBaseName file base name
   * @param  logDetails true if extra logging is needed
   * @return {@link File} instance or null if not found
   */
  @Nullable
  public static File findInPath(@NotNull String fileBaseName, boolean logDetails) {
    return findInPath(fileBaseName, logDetails, null);
  }

  @Nullable
  private static File findInPath(@NotNull String fileBaseName, boolean logDetails, @Nullable FileFilter filter) {
    List<File> exeFiles = findExeFilesInPath(fileBaseName, true, logDetails, filter);
    return ContainerUtil.getFirstItem(exeFiles);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in an original PATH environment variable.
   * Original PATH environment variable value is a value returned by <code>System.getenv("PATH")</code>.
   *
   * @param fileBaseName file base name
   * @return {@link File} instance or null if not found
   */
  private static File findInOriginalPath(@NotNull String fileBaseName) {
    String originalPath = System.getenv(PATH_ENV_VAR_NAME);
    List<File> exeFiles = doFindExeFilesInPath(originalPath, fileBaseName, true, false, null);
    return ContainerUtil.getFirstItem(exeFiles);
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
    return findAllExeFilesInPath(fileBaseName, null);
  }

  @NotNull
  public static List<File> findAllExeFilesInPath(@NotNull String fileBaseName, @Nullable FileFilter filter) {
    return findExeFilesInPath(fileBaseName, false, false, filter);
  }

  @NotNull
  private static List<File> findExeFilesInPath(@NotNull String fileBaseName,
                                               boolean stopAfterFirstMatch,
                                               boolean logDetails,
                                               @Nullable FileFilter filter) {
    String systemPath = EnvironmentUtil.getValue(PATH_ENV_VAR_NAME);
    return doFindExeFilesInPath(systemPath, fileBaseName, stopAfterFirstMatch, logDetails, filter);
  }

  @NotNull
  private static List<File> doFindExeFilesInPath(@Nullable String pathEnvVarValue,
                                                 @NotNull String fileBaseName,
                                                 boolean stopAfterFirstMatch,
                                                 boolean logDetails,
                                                 @Nullable FileFilter filter) {
    if (logDetails) {
      LOG.info("Finding files in PATH (base name=" + fileBaseName + ", PATH=" + StringUtil.notNullize(pathEnvVarValue) + ").");
    }
    if (pathEnvVarValue == null) {
      return Collections.emptyList();
    }
    List<File> result = new SmartList<>();
    List<String> paths = StringUtil.split(pathEnvVarValue, File.pathSeparator, true, true);
    for (String path : paths) {
      File dir = new File(path);
      if (logDetails) {
        File file = new File(dir, fileBaseName);
        LOG.info("path:" + path + ", path.isAbsolute:" + dir.isAbsolute() + ", path.isDirectory:" + dir.isDirectory()
                 + ", file.isFile:" + file.isFile() + ", file.canExecute:" + file.canExecute());
      }
      if (dir.isAbsolute() && dir.isDirectory()) {
        File exeFile = new File(dir, fileBaseName);
        if (exeFile.isFile() && exeFile.canExecute()) {
          if (filter == null || filter.accept(exeFile)) {
            result.add(exeFile);
            if (stopAfterFirstMatch) {
              return result;
            }
          }
        }
      }
    }
    return result;
  }

  /**
   * Alters the passed in exe path to increase probability of exe file success finding when
   * spawning an external process. Modifications are performed iff the passed in exe path is
   * a basename (i.e. it doesn't contain slashes). E.g. "java", "git" or "node".
   * <p>
   * The motivation behind this modification is as follows. When exe path is a basename,
   * {@link ProcessBuilder#start} searches for the executable file in the original PATH
   * environment variable (i.e. {@code System.getenv("PATH")}).
   * The problem is that on MacOSX original PATH value can be different than the PATH
   * value in Terminal (see {@link EnvironmentUtil#getEnvironmentMap()}.
   *
   * @param exePath String path to exe file (basename, relative path or absolute path)
   * @return if an exe file can be found in {@code EnvironmentUtil.getValue("PATH")} and
   *            nothing found in original PATH (i.e. {@code System.getenv("PATH")}),
   *         return the found exe file absolute path.
   *         Otherwise, return the passed in exe path.
   */
  @NotNull
  public static String toLocatableExePath(@NotNull String exePath) {
    //noinspection deprecation
    return findAbsolutePathOnMac(exePath);
  }

  /**
   * @deprecated use {@link #toLocatableExePath(String)} instead
   */
  public static String findAbsolutePathOnMac(@NotNull String exePath) {
    if (SystemInfo.isMac) {
      if (!StringUtil.containsChar(exePath, '/') && !StringUtil.containsChar(exePath, '\\')) {
        File originalResolvedExeFile = findInOriginalPath(exePath);
        // don't modify exePath if the absolute path can be found in the original PATH
        if (originalResolvedExeFile == null) {
          File resolvedExeFile = findInPath(exePath);
          if (resolvedExeFile != null) {
            exePath = resolvedExeFile.getAbsolutePath();
          }
        }
      }
    }
    return exePath;
  }
}
