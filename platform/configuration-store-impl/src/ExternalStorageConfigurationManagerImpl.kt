// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.xmlb.annotations.Property
import kotlinx.coroutines.CoroutineScope
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
internal class ExternalStorageConfigurationManagerImpl(private val project: Project, private val coroutineScope: CoroutineScope)
  : SimplePersistentStateComponent<ExternalStorageConfiguration>(ExternalStorageConfiguration()), ExternalStorageConfigurationManager {
  override fun isEnabled(): Boolean = state.enabled

  /**
   * Internal use only. Call ExternalProjectsManagerImpl.setStoreExternally instead.
   */
  override fun setEnabled(value: Boolean) {
    state.enabled = value
    if (project.isDefault) {
      return
    }
    val app = ApplicationManager.getApplication()
    val workspaceModel = WorkspaceModel.getInstance(project)
    app.invokeAndWait { app.runWriteAction { updateEntitySource(workspaceModel) } }
  }

  override fun loadState(state: ExternalStorageConfiguration) {
    super.loadState(state)

    if (project.isDefault) {
      return
    }

    coroutineScope.launch {
      val workspaceModel = project.serviceAsync<WorkspaceModel>()
      writeAction {
        updateEntitySource(workspaceModel)
      }
    }
  }

  private fun updateEntitySource(workspaceModel: WorkspaceModel) {
    val value = state.enabled
    workspaceModel.updateProjectModel("Change entity sources to externally imported") { updater ->
      val entitiesMap = updater.entitiesBySource { it is JpsImportedEntitySource && it.storedExternally != value }
      entitiesMap.forEach { entity ->
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