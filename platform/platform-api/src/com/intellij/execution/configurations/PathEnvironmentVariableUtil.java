// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A collection of utility methods for working with PATH environment variable.
 */
public class PathEnvironmentVariableUtil {

  private static final String PATH = "PATH";

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
    return findInPath(fileBaseName, null);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in PATH environment variable and is accepted by filter.
   *
   * @param fileBaseName file base name
   * @param filter       exe file filter
   * @return {@link File} instance or null if not found
   */
  @Nullable
  public static File findInPath(@NotNull String fileBaseName, @Nullable FileFilter filter) {
    return findInPath(fileBaseName, EnvironmentUtil.getValue(PATH), filter);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in the passed PATH environment variable value and is accepted by filter.
   *
   * @param fileBaseName      file base name
   * @param pathVariableValue value of PATH environment variable
   * @param filter            exe file filter
   * @return {@link File} instance or null if not found
   */
  @Nullable
  public static File findInPath(@NotNull String fileBaseName, @Nullable String pathVariableValue, @Nullable FileFilter filter) {
    List<File> exeFiles = findExeFilesInPath(true, filter, pathVariableValue, fileBaseName);
    return ContainerUtil.getFirstItem(exeFiles);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in an original PATH environment variable.
   * Original PATH environment variable value is a value returned by {@code System.getenv("PATH")}.
   *
   * @param fileBaseName file base name
   * @return {@link File} instance or null if not found
   */
  private static File findInOriginalPath(@NotNull String fileBaseName) {
    List<File> exeFiles = findExeFilesInPath(true, null, System.getenv(PATH), fileBaseName);
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
    return findExeFilesInPath(false, filter, EnvironmentUtil.getValue(PATH), fileBaseName);
  }

  @NotNull
  private static List<File> findExeFilesInPath(boolean stopAfterFirstMatch,
                                               @Nullable FileFilter filter,
                                               @Nullable String pathEnvVarValue,
                                               @NotNull String... fileBaseNames) {
    if (pathEnvVarValue == null) {
      return Collections.emptyList();
    }
    List<File> result = new SmartList<>();
    List<String> dirPaths = getPathDirs(pathEnvVarValue);
    for (String dirPath : dirPaths) {
      File dir = new File(dirPath);
      if (dir.isAbsolute() && dir.isDirectory()) {
        for (String fileBaseName : fileBaseNames) {
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
    }
    return result;
  }

  @NotNull
  public static List<String> getPathDirs(@NotNull String pathEnvVarValue) {
    return StringUtil.split(pathEnvVarValue, File.pathSeparator, true, true);
  }

  /** @deprecated obsolete; the behavior is incorporated in {@link GeneralCommandLine#createProcess()} (to be removed in IDEA 2019) */
  @NotNull
  public static String toLocatableExePath(@NotNull String exePath) {
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

  @NotNull
  public static List<String> getWindowsExecutableFileExtensions() {
    if (SystemInfo.isWindows) {
      String allExtensions = System.getenv("PATHEXT");
      if (allExtensions != null) {
        Collection<String> extensions = StringUtil.split(allExtensions, ";", true, true);
        extensions = ContainerUtil.filter(extensions, s -> !StringUtil.isEmpty(s) && s.startsWith("."));
        return ContainerUtil.map2List(extensions, s -> StringUtil.toLowerCase(s));
      }
    }
    return Collections.emptyList();
  }

  public static String findExecutableInWindowsPath(@NotNull String exePath) {
    if (SystemInfo.isWindows) {
      if (!StringUtil.containsChar(exePath, '/') && !StringUtil.containsChar(exePath, '\\')) {
        List<String> executableFileExtensions = getWindowsExecutableFileExtensions();

        String[] baseNames = ContainerUtil.map2Array(executableFileExtensions, String.class, s -> exePath+s);
        List<File> exeFiles = findExeFilesInPath(true, null, EnvironmentUtil.getValue(PATH), baseNames);
        File foundFile = ContainerUtil.getFirstItem(exeFiles);
        if(foundFile != null){
          return foundFile.getAbsolutePath();
        }
      }
    }
    return exePath;
  }
}
