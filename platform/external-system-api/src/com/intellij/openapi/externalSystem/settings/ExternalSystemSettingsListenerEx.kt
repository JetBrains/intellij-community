// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.settings

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.project.Project

/**
 * Allows registering [ExternalSystemSettingsListener] by extension point
 */
interface ExternalSystemSettingsListenerEx {
  /**
   * @see ExternalSystemSettingsListener.onProjectsLoaded
   */
  @JvmDefault
  fun onProjectsLoaded(project: Project, manager: ExternalSystemManager<*, *, *, *, *>, settings: Collection<ExternalProjectSettings>) {}

  /**
   * @see ExternalSystemSettingsListener.onProjectsLinked
   */
  @JvmDefault
  fun onProjectsLinked(project: Project, manager: ExternalSystemManager<*, *, *, *, *>, settings: Collection<ExternalProjectSettings>) {}

  /**
   * @see ExternalSystemSettingsListener.onProjectsUnlinked
   */
  @JvmDefault
  fun onProjectsUnlinked(project: Project, manager: ExternalSystemManager<*, *, *, *, *>, linkedProjectPaths: Set<String>) {}

  companion object {
    private val EP_NAME = ExtensionPointName<ExternalSystemSettingsListenerEx>("com.intellij.externalSystemSettingsListener")

    fun onProjectsLoaded(project: Project, manager: ExternalSystemManager<*, *, *, *, *>, settings: Collection<ExternalProjectSettings>) {
      EP_NAME.forEachExtensionSafe { it.onProjectsLoaded(project, manager, settings) }
    }

    fun onProjectsLinked(project: Project, manager: ExternalSystemManager<*, *, *, *, *>, settings: Collection<ExternalProjectSettings>) {
      EP_NAME.forEachExtensionSafe { it.onProjectsLinked(project, manager, settings) }
    }

    fun onProjectsUnlinked(project: Project, manager: ExternalSystemManager<*, *, *, *, *>, linkedProjectPaths: Set<String>) {
      EP_NAME.forEachExtensionSafe { it.onProjectsUnlinked(project, manager, linkedProjectPaths) }
    }
  }
}