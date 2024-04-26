// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting;

import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import com.intellij.troubleshooting.TroubleInfoCollector;
import org.jetbrains.annotations.NotNull;

public final class CompositeGeneralTroubleInfoCollector implements TroubleInfoCollector {
  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    return collectInfo(project, GeneralTroubleInfoCollector.EP_SETTINGS.getExtensions());
  }

  static @NotNull String collectInfo(@NotNull Project project, @NotNull GeneralTroubleInfoCollector @NotNull... collectors) {
    StringBuilder builder = new StringBuilder();
    for (GeneralTroubleInfoCollector collector : collectors) {
      builder.append("=== ").append(collector.getTitle()).append(" ===\n");
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
