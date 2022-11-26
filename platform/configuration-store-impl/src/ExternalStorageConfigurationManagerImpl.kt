// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.annotations.Property
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.WorkspaceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    if (project.isDefault) return
    val app = ApplicationManager.getApplication()
    app.invokeAndWait { app.runWriteAction(::updateEntitySource) }
  }

  override fun loadState(state: ExternalStorageConfiguration) {
    super.loadState(state)

    if (project.isDefault) {
      return
    }

    project.coroutineScope.launch(Dispatchers.EDT) {
      ApplicationManager.getApplication().runWriteAction(::updateEntitySource)
    }
  }

  private fun updateEntitySource() {
    val value = state.enabled
    WorkspaceModel.getInstance(project).updateProjectModel("Change entity sources to externally imported") { updater ->
      val entitiesMap = updater.entitiesBySource { it is JpsImportedEntitySource && it.storedExternally != value }
      entitiesMap.values.asSequence().flatMap { it.values.asSequence().flatMap { entities -> entities.asSequence() } }.forEach { entity ->
        val source = entity.entitySource
        if (source is JpsImportedEntitySource) {
          updater.modifyEntity(WorkspaceEntity.Builder::class.java, entity) {
            this.entitySource = JpsImportedEntitySource(source.internalFile, source.externalSystemId, value)
          }
        }
      }
    }
  }
}