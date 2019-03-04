// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.troubleshooting;

import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import org.jetbrains.annotations.NotNull;

public class SystemTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @NotNull
  @Override
  public String getTitle() {
    return "System";
  }

  @NotNull
  @Override
  public String collectInfo(@NotNull Project project) {
    String output = "";
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
