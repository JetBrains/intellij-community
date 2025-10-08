// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.dashboard.RunDashboardCustomizer;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.services.ServiceViewDnDDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl;
import com.intellij.execution.dashboard.RunDashboardService;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@ApiStatus.Internal
public final class BackendRunDashboardServiceUtils {
  public static void reorderConfigurations(@NotNull Project project,
                                           @NotNull RunDashboardService target,
                                           @NotNull RunDashboardService drop,
                                           @NotNull ServiceViewDnDDescriptor.Position position) {
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    runManager.fireBeginUpdate();
    try {
      drop.getConfigurationSettings().setFolderName(target.getConfigurationSettings().getFolderName());

      Map<RunnerAndConfigurationSettings, Integer> indices = new HashMap<>();
      int i = 0;
      for (RunnerAndConfigurationSettings each : runManager.getAllSettings()) {
        if (each.equals(drop.getConfigurationSettings())) continue;

        if (each.equals(target.getConfigurationSettings())) {
          if (position == ServiceViewDnDDescriptor.Position.ABOVE) {
            indices.put(drop.getConfigurationSettings(), i++);
            indices.put(target.getConfigurationSettings(), i++);
          }
          else if (position == ServiceViewDnDDescriptor.Position.BELOW) {
            indices.put(target.getConfigurationSettings(), i++);
            indices.put(drop.getConfigurationSettings(), i++);
          }
        }
        else {
          indices.put(each, i++);
        }
      }
      runManager.setOrder(Comparator.comparingInt(indices::get));
    }
    finally {
      runManager.fireEndUpdate();
    }
  }

  public static @Nullable PsiElement getServiceNavigationTarget(@NotNull RunDashboardService backendService) {
    PsiElement targetElement = null;
    List<RunDashboardCustomizer> customizers =
      RunDashboardManagerImpl.getCustomizers(backendService.getConfigurationSettings(), backendService.getDescriptor());
    for (RunDashboardCustomizer customizer : customizers) {
      PsiElement psiElement = customizer.getPsiElement(backendService.getConfigurationSettings().getConfiguration());
      if (psiElement != null) {
        targetElement = psiElement;
        break;
      }
    }
    return targetElement;
  }
}
