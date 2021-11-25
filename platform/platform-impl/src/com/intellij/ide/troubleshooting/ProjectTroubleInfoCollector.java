// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.troubleshooting;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import org.jetbrains.annotations.NotNull;

final class ProjectTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @Override
  public @NotNull String getTitle() {
    return "Project";
  }

  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    return "Project trusted: " + TrustedProjects.isTrusted(project);
  }
}
