// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.KeyedExtensionCollector
import org.jetbrains.annotations.ApiStatus

/**
 * Describes additional steps to configure or cleanup execution context
 * @see com.intellij.openapi.externalSystem.model.task.ExternalSystemTask for details
 */
@ApiStatus.Experimental
interface ExternalSystemExecutionAware {

  /**
   * Prepares execution context to execution
   * This method called after execution start but before
   * [com.intellij.openapi.externalSystem.model.task.ExternalSystemTask.execute]
   */
  fun prepareExecution(
    task: ExternalSystemTask,
    externalProjectPath: String,
    isPreviewMode: Boolean,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    project: Project
  )

  fun getEnvironmentConfigurationProvider(runConfiguration: ExternalSystemRunConfiguration,
                                          project: Project): TargetEnvironmentConfigurationProvider? = null

  fun getEnvironmentConfigurationProvider(projectPath: String,
                                          isPreviewMode: Boolean,
                                          project: Project): TargetEnvironmentConfigurationProvider? = null

  fun isRemoteRun(runConfiguration: ExternalSystemRunConfiguration, project: Project) = false

  companion object {
    private val TARGET_ENVIRONMENT_CONFIGURATION_PROVIDER: Key<TargetEnvironmentConfigurationProvider> =
      Key.create("Target environment configuration provider")
    private val EP_COLLECTOR = KeyedExtensionCollector<ExternalSystemExecutionAware, ProjectSystemId>("com.intellij.externalExecutionAware")

    @JvmStatic
    fun getExtensions(systemId: ProjectSystemId): List<ExternalSystemExecutionAware> = EP_COLLECTOR.forKey(systemId)

    fun ExternalSystemExecutionSettings.getEnvironmentConfigurationProvider() = getUserData(TARGET_ENVIRONMENT_CONFIGURATION_PROVIDER)

    @ApiStatus.Internal
    fun ExternalSystemExecutionSettings.setEnvironmentConfigurationProvider(configuration: TargetEnvironmentConfigurationProvider?) {
      putUserData(TARGET_ENVIRONMENT_CONFIGURATION_PROVIDER, configuration)
    }
  }
}