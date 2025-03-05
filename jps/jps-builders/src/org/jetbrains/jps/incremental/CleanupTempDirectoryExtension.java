// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.PreloadedDataExtension;
import org.jetbrains.jps.cmdline.PreloadedData;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

public final class CleanupTempDirectoryExtension implements PreloadedDataExtension {
  private Future<?> myTask;
  
  @Override
  public void preloadData(@NotNull PreloadedData data) {
    ProjectDescriptor projectDescriptor = data.getProjectDescriptor();
    if (projectDescriptor != null) {
      myTask = startTempDirectoryCleanupTask(projectDescriptor);
    }
  }

  @Override
  public void buildSessionInitialized(@NotNull PreloadedData data) {
  }

  @Override
  public void discardPreloadedData(PreloadedData data) {
  }

  public static @Nullable CleanupTempDirectoryExtension getInstance() {
    for (PreloadedDataExtension extension : JpsServiceManager.getInstance().getExtensions(PreloadedDataExtension.class)) {
      if (extension instanceof CleanupTempDirectoryExtension) {
        return (CleanupTempDirectoryExtension)extension;
      }
    }
    return null;
  }

  public Future<?> getCleanupTask() {
    return myTask;
  }

  static @NotNull Collection<Future<?>> getRunningCleanupTasks() {
    List<Future<?>> tasks = new ArrayList<>();
    for (PreloadedDataExtension extension : JpsServiceManager.getInstance().getExtensions(PreloadedDataExtension.class)) {
      Future<?> task = extension instanceof CleanupTempDirectoryExtension? ((CleanupTempDirectoryExtension)extension).getCleanupTask() : null;
      if (task != null) {
        tasks.add(task);
      }
    }
    return tasks;
  }

  static @Nullable Future<?> startTempDirectoryCleanupTask(@NotNull ProjectDescriptor pd) {
    String tempPath = System.getProperty("java.io.tmpdir", null);
    if (tempPath == null || tempPath.isBlank()) {
      return null;
    }

    Path tempDir = Path.of(tempPath).toAbsolutePath().normalize();
    Path dataRoot = pd.dataManager.getDataPaths().getDataStorageDir();
    if (!tempDir.startsWith(dataRoot) || tempDir.equals(dataRoot)) {
      // cleanup only 'local' temp
      return null;
    }

    //noinspection DuplicatedCode
    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
      for (Path path : stream) {
        files.add(path);
      }
    }
    catch (IOException ex) {
      try {
        // ensure temp dir exists
        Files.createDirectories(tempDir);
      }
      catch (IOException ignored) {
      }
      return null;
    }

    if (files.isEmpty()) {
      return null;
    }

    RunnableFuture<Void> task = new FutureTask<>(() -> {
      for (Path tempFile : files) {
        try {
          FileUtilRt.deleteRecursively(tempFile);
        }
        catch (IOException ignored) {
        }
      }
    }, null);
    Thread thread = new Thread(task, "Temp directory cleanup");
    thread.setPriority(Thread.MIN_PRIORITY);
    thread.setDaemon(true);
    thread.start();
    return task;
  }
}
