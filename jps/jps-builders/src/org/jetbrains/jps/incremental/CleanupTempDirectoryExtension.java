// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.PreloadedDataExtension;
import org.jetbrains.jps.cmdline.PreloadedData;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.concurrent.Future;

public class CleanupTempDirectoryExtension implements PreloadedDataExtension {
  private Future<Void> myTask;
  
  @Override
  public void preloadData(@NotNull PreloadedData data) {
    final ProjectDescriptor pd = data.getProjectDescriptor();
    if (pd != null) {
      myTask = IncProjectBuilder.startTempDirectoryCleanupTask(pd);
    }
  }

  @Nullable
  public static CleanupTempDirectoryExtension getInstance() {
    for (PreloadedDataExtension extension : JpsServiceManager.getInstance().getExtensions(PreloadedDataExtension.class)) {
      if (extension instanceof CleanupTempDirectoryExtension) {
        return (CleanupTempDirectoryExtension)extension;
      }
    }
    return null;
  }
  
  @Nullable
  public Future<Void> getCleanupTask() {
    return myTask;
  }

  @Override
  public void buildSessionInitialized(@NotNull PreloadedData data) {
  }

  @Override
  public void discardPreloadedData(PreloadedData data) {
  }
}
