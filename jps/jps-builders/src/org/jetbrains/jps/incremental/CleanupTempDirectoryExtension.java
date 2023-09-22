// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.PreloadedDataExtension;
import org.jetbrains.jps.cmdline.PreloadedData;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.concurrent.Future;

public final class CleanupTempDirectoryExtension implements PreloadedDataExtension {
  private Future<Void> myTask;
  
  @Override
  public void preloadData(@NotNull PreloadedData data) {
    final ProjectDescriptor pd = data.getProjectDescriptor();
    if (pd != null) {
      myTask = IncProjectBuilder.startTempDirectoryCleanupTask(pd);
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
    return myTask;
  }

  @Override
  public void buildSessionInitialized(@NotNull PreloadedData data) {
  }

  @Override
  public void discardPreloadedData(PreloadedData data) {
  }
}
