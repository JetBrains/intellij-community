// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.RunContentDescriptorId;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Set;

public interface RunDashboardManager {

  @ApiStatus.Internal
  boolean isInitialized();

  @ApiStatus.Internal
  void updateServiceRunContentDescriptor(@NotNull Content contentWithNewDescriptor, @NotNull RunContentDescriptor oldDescriptor);

  static RunDashboardManager getInstance(@NotNull Project project) {
    return RunDashboardManagerProxy.getInstance(project);
  }

  // Sorry for that, but it's unbearable to move the api classes from the actual dashboard module to lang or execution
  // only to be able to add them into an interface which in turn cannot be moved to the dashboard module because of existing external dependencies
  // AND the fact that it solves cyclic dependencies issue between debugger, execution and lang modules
  @ApiStatus.Internal
  @Nullable default Object findService(@NotNull RunContentDescriptorId descriptorId) { return null; }

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

  @ApiStatus.Internal
  void navigateToServiceOnRun(@NotNull RunContentDescriptorId descriptorId, Boolean focus);
}