// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.execution.target.TargetEnvironmentConfiguration
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

  fun getEnvironmentConfiguration(runConfiguration: ExternalSystemRunConfiguration,
                                  taskNotificationListener: ExternalSystemTaskNotificationListener,
                                  project: Project): TargetEnvironmentConfiguration? = null

  fun getEnvironmentConfiguration(externalProjectPath: String,
                                  isPreviewMode: Boolean,
                                  taskNotificationListener: ExternalSystemTaskNotificationListener,
                                  project: Project): TargetEnvironmentConfiguration? = null

  companion object {
    private val TARGET_ENVIRONMENT_CONFIGURATION: Key<TargetEnvironmentConfiguration> = Key.create("Target environment configuration")
    private val EP_COLLECTOR = KeyedExtensionCollector<ExternalSystemExecutionAware, ProjectSystemId>("com.intellij.externalExecutionAware")

    @JvmStatic
    fun getExtensions(systemId: ProjectSystemId): List<ExternalSystemExecutionAware> {
      return EP_COLLECTOR.forKey(systemId)
    }

    @JvmStatic
    fun ExternalSystemExecutionSettings.getEnvironmentConfiguration() = getUserData(TARGET_ENVIRONMENT_CONFIGURATION)

    @ApiStatus.Internal
    @JvmStatic
    fun ExternalSystemExecutionSettings.setEnvironmentConfiguration(configuration: TargetEnvironmentConfiguration?) =
      putUserData(TARGET_ENVIRONMENT_CONFIGURATION, configuration)
  }
}