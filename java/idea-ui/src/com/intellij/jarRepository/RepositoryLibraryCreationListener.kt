// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity

private class RepositoryLibraryCreationListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    val entityChanges = event.getChanges(LibraryEntity::class.java)
    for (entityChange in entityChanges) {
      when (entityChange) {
        is EntityChange.Added -> onLibraryAdded(entityChange.newEntity)
        is EntityChange.Replaced -> onLibraryAdded(entityChange.newEntity)
        else -> {}
      }
    }
  }

  private fun onLibraryAdded(entity: LibraryEntity) {
    val settings = RepositoryLibrarySettings.getInstanceOrDefaults(project)
    val utils = RepositoryLibraryUtils.getInstance(project)
    utils.computePropertiesForNewLibrary(entity, settings.isSha256ChecksumAutoBuildEnabled(), settings.isJarRepositoryAutoBindEnabled())
  }
}