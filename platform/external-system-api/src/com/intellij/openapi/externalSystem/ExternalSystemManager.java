// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.service.ParametersEnhancer;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * IntelliJ external systems integration is built using GoF Bridge pattern, i.e. 'external-system' module defines
 * external system-specific extension (current interface) and an api which is used by all extensions. Most of the codebase
 * is built on top of that api and provides generic actions like 'sync ide project with external project'; 'import library
 * dependencies which are configured at external system but not at the ide' etc.
 * <p/>
 * That makes it relatively easy to add a new external system integration.
 */
public interface ExternalSystemManager<
  ProjectSettings extends ExternalProjectSettings,
  SettingsListener extends ExternalSystemSettingsListener<ProjectSettings>,
  Settings extends AbstractExternalSystemSettings<Settings, ProjectSettings, SettingsListener>,
  LocalSettings extends AbstractExternalSystemLocalSettings,
  ExecutionSettings extends ExternalSystemExecutionSettings>
  extends ParametersEnhancer
{

  ExtensionPointName<ExternalSystemManager<?, ?, ?, ?, ?>> EP_NAME = new ExtensionPointName<>("com.intellij.externalSystemManager");

  /**
   * @return    id of the external system represented by the current manager
   */
  @NotNull
  ProjectSystemId getSystemId();

  /**
   * @return    a strategy which can be queried for external system settings to use with the given project
   */
  @NotNull
  Function<Project, Settings> getSettingsProvider();

  /**
   * @return    a strategy which can be queried for external system local settings to use with the given project
   */
  @NotNull
  Function<Project, LocalSettings> getLocalSettingsProvider();

  /**
   * @return    a strategy which can be queried for external system execution settings to use with the given project
   */
  @NotNull
  Function<Pair<Project, String/*linked project path*/>, ExecutionSettings> getExecutionSettingsProvider();

  /**
   * Allows to retrieve information about {@link ExternalSystemProjectResolver project resolver} to use for the target external
   * system.
   * <p/>
   * <b>Note:</b> we return a class instance instead of resolver object here because there is a possible case that the resolver
   * is used at external (non-ide) process, so, it needs information which is enough for instantiating it there. That implies
   * the requirement that target resolver class is expected to have a no-args constructor
   *
   * @return  class of the project resolver to use for the target external system
   */
  @NotNull
  Class<? extends ExternalSystemProjectResolver<ExecutionSettings>> getProjectResolverClass();

  /**
   * @return    class of the build manager to use for the target external system
   * @see #getProjectResolverClass()
   */
  default @NotNull Class<? extends ExternalSystemTaskManager<ExecutionSettings>> getTaskManagerClass() {
    //noinspection unchecked
    return (Class)ExternalSystemTaskManager.NoOp.class;
  }

  /**
   * @return    file chooser descriptor to use when adding new external project
   */
  @NotNull
  FileChooserDescriptor getExternalProjectDescriptor();

  /**
   * @return scope where to search sources for external system tasks execution
   */
  @Nullable
  default GlobalSearchScope getSearchScope(@NotNull Project project, @NotNull ExternalSystemTaskExecutionSettings taskExecutionSettings) {
    return null;
  }

  /**
   * @return SMTRunnerConsoleProperties to integrate external system test runner with the 'Import Tests Results' action
   * @deprecated to be removed in IDEA 2020, implement {@link com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider}
   * for your {@link com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration} instead
   */
  @Nullable
  @Deprecated(forRemoval = true)
  default Object createTestConsoleProperties(@NotNull Project project,
                                             @NotNull Executor executor,
                                             @NotNull RunConfiguration runConfiguration) {
    return null;
  }

  /**
   * @return list of extension points used for populating external project data graph.
   * Plugins containing extensions will be used to look for classes on deserialization of external project data graph.
   */
  @ApiStatus.Experimental
  default @NotNull List<ExtensionPointName<?>> getExtensionPointsForResolver() {
    return Collections.emptyList();
  }
}
