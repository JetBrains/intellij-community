// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Set;


/**
 * @author konstantin.aleev
 */
public interface RunDashboardManager {

  static RunDashboardManager getInstance(@NotNull Project project) {
    return RunDashboardManagerProxy.getInstance(project);
  }

  void updateDashboard(boolean withStructure);

  // todo split: do not add this method to frontend implementation - in frontend code use FrontendRunDashboardManager.isShowInDashboard :/
  boolean isShowInDashboard(@NotNull RunConfiguration runConfiguration);

  @NotNull
  @Unmodifiable
  Set<String> getTypes();

  void setTypes(Set<String> types);

  @ApiStatus.Internal
  @NotNull
  Set<RunConfiguration> getHiddenConfigurations();

  @ApiStatus.Internal
  void hideConfigurations(@NotNull Collection<? extends RunConfiguration> configurations);

  @ApiStatus.Internal
  void restoreConfigurations(@NotNull Collection<? extends RunConfiguration> configurations);

  @ApiStatus.Internal
  boolean isNewExcluded(@NotNull String typeId);

  @ApiStatus.Internal
  void setNewExcluded(@NotNull String typeId, boolean newExcluded);

  @ApiStatus.Internal
  void clearConfigurationStatus(@NotNull RunConfiguration configuration);

  @ApiStatus.Internal
  boolean isOpenRunningConfigInNewTab();

  @ApiStatus.Internal
  void setOpenRunningConfigInNewTab(boolean value);

  @ApiStatus.Internal
  Set<String> getEnableByDefaultTypes();
}