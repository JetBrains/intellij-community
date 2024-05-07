// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

internal val Project.isWorkspace get() = WorkspaceSettings.getInstance(this).isWorkspace

internal fun setWorkspace(project: Project) {
  WorkspaceSettings.getInstance(project).isWorkspace = true
}

@Service(Service.Level.PROJECT)
@State(name = "WorkspaceSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
private class WorkspaceSettings : BaseState(), PersistentStateComponent<WorkspaceSettings> {
  override fun getState(): WorkspaceSettings = this
  override fun loadState(state: WorkspaceSettings) {
    copyFrom(state)
  }

  var isWorkspace: Boolean by property(false)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): WorkspaceSettings = project.service()
  }
}
