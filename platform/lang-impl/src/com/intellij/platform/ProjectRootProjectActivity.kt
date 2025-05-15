// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.ProjectRootEntitySource

internal class ProjectRootProjectActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    if (!Registry.`is`("ide.create.project.root.entity")) return
    if (ApplicationManager.getApplication().isUnitTestMode) return

    val baseDir = project.guessProjectDir() ?: return
    val workspaceModel = project.serviceAsync<WorkspaceModel>()
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    val entity = ProjectRootEntity(baseDir.toVirtualFileUrl(urlManager), ProjectRootEntitySource)

    val newStorage = MutableEntityStorage.create()
    newStorage.addEntity(entity)
    workspaceModel.update("Add project root on the first project open") { storage ->
      storage.replaceBySource({ it is ProjectRootEntitySource}, newStorage)
    }
  }
}
