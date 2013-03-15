package com.intellij.execution.configurations;

import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Sergey Simonchik
 */
public class PathEnvironmentVariableUtil {

  public static final String PATH_ENV_VAR_NAME = "PATH";
  private static final Logger LOG = Logger.getInstance(PathEnvironmentVariableUtil.class);
  private static final int MAX_BLOCKING_TIMEOUT_MILLIS = 1000;
  private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
  private static volatile String FIXED_MAC_PATH_VALUE;

  private PathEnvironmentVariableUtil() {
  }

  /**
   * Tries to return a real value for PATH environment variable on OSX.
   * Workaround for http://youtrack.jetbrains.com/issue/IDEA-99154
   *
   * @return <code>null</code>, if the platform isn't OSX, any troubles were encountered or time limit exceeded,
   *         otherwise returns <code>String</code> instance - PATH env var value
   */
  @Nullable
  public static String getFixedPathEnvVarValueOnMac() {
    if (INITIALIZED.compareAndSet(false, true)) {
      final Semaphore semaphore = new Semaphore(0, true);
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
        @Override
        public void run() {
          try {
            FIXED_MAC_PATH_VALUE = calcFixedMacPathEnvVarValue();
          }
          catch (Throwable t) {
            LOG.error("Can't calculate proper value for PATH environment variable", t);
          }
          finally {
            semaphore.release();
          }
        }
      });
      try {
        semaphore.tryAcquire(MAX_BLOCKING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException e) {
        LOG.info("Thread interrupted", e);
      }
    }
    return FIXED_MAC_PATH_VALUE;
  }

  @Nullable
  private static String calcFixedMacPathEnvVarValue() {
    final String originalPath = getOriginalPathEnvVarValue();
    Map<? extends String, ? extends String> envVars = UnixProcessManager.getOrLoadConsoleEnvironment();
    String consolePath = envVars.get(PATH_ENV_VAR_NAME);
    return mergePaths(ContainerUtil.newArrayList(originalPath, consolePath));
  }

  @Nullable
  private static String mergePaths(@NotNull List<String> pathStrings) {
    LinkedHashSet<String> paths = ContainerUtil.newLinkedHashSet();
    for (String pathString : pathStrings) {
      if (pathString != null) {
        List<String> locals = StringUtil.split(pathString, File.pathSeparator, true, true);
        for (String local : locals) {
          paths.add(local);
        }
      }
    }
    if (paths.isEmpty()) {
      return null;
    }
    return StringUtil.join(paths, File.pathSeparator);
  }

  @Nullable
  private static String getOriginalPathEnvVarValue() {
    return EnvironmentUtil.getValue(PATH_ENV_VAR_NAME);
  }

  @NotNull
  private static String getReliablePath() {
    final String value;
    if (SystemInfo.isMac) {
      value = getFixedPathEnvVarValueOnMac();
    }
    else {
      value = getOriginalPathEnvVarValue();
    }
    return StringUtil.notNullize(value);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in PATH environment variable.
   *
   * @param fileBaseName file base name
   * @return {@code File} instance or null if not found
   */
  @Nullable
  public static File findInPath(@NotNull String fileBaseName) {
    List<File> exeFiles = findExeFilesInPath(fileBaseName, true);
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
    return findExeFilesInPath(fileBaseName, false);
  }

  @NotNull
  private static List<File> findExeFilesInPath(@NotNull String fileBaseName, boolean stopAfterFirstMatch) {
    List<String> paths = StringUtil.split(getReliablePath(), File.pathSeparator, true, true);
    List<File> exeFiles = Collections.emptyList();
    for (String path : paths) {
      File dir = new File(path);
      if (dir.isAbsolute() && dir.isDirectory()) {
        File file = new File(dir, fileBaseName);
        if (file.isFile() && file.canExecute()) {
          if (stopAfterFirstMatch) {
            return Collections.singletonList(file);
          }
          if (exeFiles.isEmpty()) {
            exeFiles = ContainerUtil.newArrayListWithExpectedSize(paths.size());
          }
          exeFiles.add(file);
        }
      }
    }
    return exeFiles;
  }

}
