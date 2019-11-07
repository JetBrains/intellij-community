// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.troubleshooting;

import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import com.intellij.troubleshooting.TroubleInfoCollector;
import org.jetbrains.annotations.NotNull;

public class CompositeGeneralTroubleInfoCollector implements TroubleInfoCollector {

  @NotNull
  @Override
  public String collectInfo(@NotNull Project project) {
    StringBuilder builder = new StringBuilder();
    for (GeneralTroubleInfoCollector collector : GeneralTroubleInfoCollector.EP_SETTINGS.getExtensions()) {
      builder.append("=== " + collector.getTitle() + " ===\n");
      builder.append(collector.collectInfo(project).trim());
      builder.append("\n\n");
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    return "General";
  }
}
