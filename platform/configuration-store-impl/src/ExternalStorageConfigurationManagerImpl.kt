// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.annotations.Property
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel

@Property(style = Property.Style.ATTRIBUTE)
class ExternalStorageConfiguration : BaseState() {
  var enabled by property(false)
}

/**
 * This class isn't used in the new implementation of project model, which is based on [Workspace Model][com.intellij.workspaceModel.ide].
 * It shouldn't be used directly, its interface [ExternalStorageConfigurationManager] should be used instead.
 */
@State(name = "ExternalStorageConfigurationManager")
internal class ExternalStorageConfigurationManagerImpl(private val project: Project) : SimplePersistentStateComponent<ExternalStorageConfiguration>(ExternalStorageConfiguration()), ExternalStorageConfigurationManager {
  override fun isEnabled(): Boolean = state.enabled

  /**
   * Internal use only. Call ExternalProjectsManagerImpl.setStoreExternally instead.
   */
  override fun setEnabled(value: Boolean) {
    state.enabled = value
    if (!project.isDefault && WorkspaceModel.isEnabled) {
      ApplicationManager.getApplication().invokeAndWait(Runnable {
        runWriteAction {
          WorkspaceModel.getInstance(project).updateProjectModel { updater ->
            val entitiesMap = updater.entitiesBySource { it is JpsImportedEntitySource && it.storedExternally != value }
            entitiesMap.values.asSequence().flatMap { it.values.asSequence().flatMap { entities -> entities.asSequence() } }.forEach { entity ->
              val source = entity.entitySource
              if (source is JpsImportedEntitySource) {
                updater.changeSource(entity, JpsImportedEntitySource(source.internalFile, source.externalSystemId, value))
              }
            }
          }
        }
      })
    }
  }
}