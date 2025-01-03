// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.PreloadedDataExtension;
import org.jetbrains.jps.cmdline.PreloadedData;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

public final class CleanupTempDirectoryExtension implements PreloadedDataExtension {
  private Future<Void> task;
  
  @Override
  public void preloadData(@NotNull PreloadedData data) {
    ProjectDescriptor projectDescriptor = data.getProjectDescriptor();
    if (projectDescriptor != null) {
      task = startTempDirectoryCleanupTask(projectDescriptor);
    }
  }

  public static @Nullable CleanupTempDirectoryExtension getInstance() {
    for (PreloadedDataExtension extension : JpsServiceManager.getInstance().getExtensions(PreloadedDataExtension.class)) {
      if (extension instanceof CleanupTempDirectoryExtension) {
        return (CleanupTempDirectoryExtension)extension;
      }
    }
    return null;
  }
  
  public @Nullable Future<Void> getCleanupTask() {
    return task;
  }

  @Override
  public void buildSessionInitialized(@NotNull PreloadedData data) {
  }

  @Override
  public void discardPreloadedData(PreloadedData data) {
  }

  static @Nullable Future<Void> startTempDirectoryCleanupTask(@NotNull ProjectDescriptor projectDescriptor) {
    String tempPath = System.getProperty("java.io.tmpdir", null);
    if (Strings.isEmptyOrSpaces(tempPath)) {
      return null;
    }

    File tempDir = new File(tempPath);
    File dataRoot = projectDescriptor.dataManager.getDataPaths().getDataStorageRoot();
    if (!FileUtil.isAncestor(dataRoot.getPath(), tempDir.getPath(), true)) {
      // cleanup only 'local' temp
      return null;
    }

    File[] files = tempDir.listFiles();
    if (files == null) {
      // ensure the directory exists
      tempDir.mkdirs();
    }
    else if (files.length > 0) {
      RunnableFuture<Void> task = new FutureTask<>(() -> {
        for (File tempFile : files) {
          FileUtilRt.delete(tempFile);
        }
      }, null);
      Thread thread = new Thread(task, "Temp directory cleanup");
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.setDaemon(true);
      thread.start();
      return task;
    }
    return null;
  }
}
