// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

public final class JpsCachesLoaderUtil {
  private static final Logger LOG = Logger.getInstance(JpsCachesLoaderUtil.class);
  public static final String LOADER_TMP_FOLDER_NAME = "jps-cache-loader";
  public static final String INTELLIJ_REPO_NAME = "intellij.git";
  public static final ExecutorService EXECUTOR_SERVICE = AppExecutorUtil.createBoundedApplicationPoolExecutor("JpsCacheLoader Pool",
                                                                                                         INSTANCE, getThreadPoolSize());
  private JpsCachesLoaderUtil() {}

  private static int getThreadPoolSize() {
    int threadsCount = Runtime.getRuntime().availableProcessors() - 1;
    LOG.info("Executor service will be configured with " + threadsCount + " threads");
    return threadsCount;
  }

  public static void delete(@NotNull File dir, boolean asynchronously) {
    if (!asynchronously) {
      FileUtil.delete(dir);
      return;
    }

    LOG.info("Deleting asynchronously... " + dir.getPath());
    try {
      File temp = getEmptyTempDir();
      Path moved = Files.move(dir.toPath(), temp.toPath());
      EXECUTOR_SERVICE.execute(() -> delete(moved));
    }
    catch (IOException e) {
      LOG.warn("Unable to move directory: " + e.getMessage());
      FileUtil.delete(dir);
    }
  }

  public static void runCleanUpAsynchronously() {
    LOG.info("Running clean-up asynchronously...");
    Path pluginTemp = Path.of(PathManager.getPluginTempPath());
    try (Stream<Path> stream = Files.list(pluginTemp)) {
      List<Path> files = stream.filter(file -> file.getFileName().toString().startsWith(getPrefix())).collect(Collectors.toList());
      if (!files.isEmpty()) {
        EXECUTOR_SERVICE.execute(() -> files.forEach(JpsCachesLoaderUtil::delete));
      }
    }
    catch (IOException e) {
      LOG.warn("Unable to run clean-up task: " + e.getMessage());
    }
  }

  private static void delete(@NotNull Path dir) {
    if (SystemInfo.isUnix) {
      File empty = getEmptyTempDir();
      if (empty.mkdir()) {
        try {
          // https://unix.stackexchange.com/a/79656/47504
          List<String> command = new ArrayList<>();
          command.add("rsync");
          command.add("-a");
          command.add("--delete");
          command.add(empty.getPath() + "/");
          command.add(dir + "/");

          ProcessBuilder builder = new ProcessBuilder(command);
          Process process = builder.start();
          process.waitFor();
          int exitCode = process.exitValue();
          LOG.info("rsync exited with " + exitCode);
        }
        catch (IOException | InterruptedException e) {
          LOG.warn("rsync failed: " + e.getMessage());
        }
        finally {
          FileUtil.delete(empty);
        }
      }
    }
    FileUtil.delete(dir.toFile());
  }

  private static @NotNull File getEmptyTempDir() {
    File pluginTemp = new File(PathManager.getPluginTempPath());
    String prefix = getPrefix() + UUID.randomUUID();
    return FileUtil.findSequentNonexistentFile(pluginTemp, prefix, "");
  }

  private static @NotNull String getPrefix() {
    return LOADER_TMP_FOLDER_NAME + "-";
  }
}
