// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting;

import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SystemTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @Override
  public @NotNull @NonNls String getTitle() {
    return "System";
  }

  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    @NonNls String output = "";
    long mb = 1024L * 1024L;
    Runtime runtime = Runtime.getRuntime();
    output += "Number of CPU: " + runtime.availableProcessors() + '\n';
    output += "Used memory: " + (runtime.totalMemory() - runtime.freeMemory()) / mb + "Mb \n";
    output += "Free memory: " + runtime.freeMemory() / mb + "Mb \n";
    output += "Total memory: " + runtime.totalMemory() / mb + "Mb \n";
    output += "Maximum available memory: " + runtime.maxMemory() / mb + "Mb \n";
    return output;
  }
}
