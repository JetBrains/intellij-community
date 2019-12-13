// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.ObjectLongHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Map;

@ApiStatus.Internal
public interface StartUpPerformanceService extends StartupActivity {
  void lastOptionTopHitProviderFinishedForProject(@NotNull Project project);

  @NotNull
  Map<String, ObjectLongHashMap<String>> getPluginCostMap();

  @Nullable
  ObjectIntHashMap<String> getMetrics();

  @Nullable
  ByteBuffer getLastReport();

  @NotNull
  static StartUpPerformanceService getInstance() {
    try {
      @SuppressWarnings("unchecked")
      Class<StartUpPerformanceService> aClass = (Class<StartUpPerformanceService>)StartUpPerformanceService.class.getClassLoader()
        .loadClass("com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter");
      return StartupActivity.POST_STARTUP_ACTIVITY.findExtensionOrFail(aClass);
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
