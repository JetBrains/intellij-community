// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThreeState;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

@ApiStatus.Internal
public interface StartUpPerformanceService {
  void lastOptionTopHitProviderFinishedForProject(@NotNull Project project);

  @NotNull
  Map<String, Object2LongMap<String>> getPluginCostMap();

  @Nullable
  Object2IntMap<String> getMetrics();

  @Nullable
  ByteBuffer getLastReport();

  @NotNull
  static StartUpPerformanceService getInstance() {
    try {
      @SuppressWarnings("unchecked")
      Class<StartUpPerformanceService> aClass = (Class<StartUpPerformanceService>)StartUpPerformanceService.class.getClassLoader()
        .loadClass("com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter");
      ExtensionPointImpl<Object> ep = (ExtensionPointImpl<Object>)ApplicationManager.getApplication().getExtensionArea().getExtensionPoint("com.intellij.startupActivity");
      return Objects.requireNonNull(ep.findExtension(aClass, true, ThreeState.YES));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
