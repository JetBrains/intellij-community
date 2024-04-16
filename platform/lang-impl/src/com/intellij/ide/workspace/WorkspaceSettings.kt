// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "WorkspaceSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class WorkspaceSettings : BaseState(), PersistentStateComponent<WorkspaceSettings> {
  override fun getState(): WorkspaceSettings = this
  override fun loadState(state: WorkspaceSettings) {
    copyFrom(state)
  }

  var isWorkspace: Boolean by property(false)
  private var importedModules by stringSet()

  fun isImportedModule(module: Module) = importedModules.contains(module.name)
  fun setImportedModule(module: Module, value: Boolean) {
    if (value) {
      importedModules.add(module.name)
    }
    else {
      importedModules.remove(module.name)
    }
    incrementModificationCount()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): WorkspaceSettings = project.service()
  }
}
