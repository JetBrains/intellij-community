// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryPropertiesEntity

private class RepositoryLibraryChangeListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    val changedLibraryEntities = mutableSetOf<LibraryEntity?>()

    // Handle library roots change
    event.getChanges(LibraryEntity::class.java).forEach {
      when (it) {
        is EntityChange.Added, is EntityChange.Replaced -> changedLibraryEntities.add(it.newEntity)
        else -> {}
      }
    }

    // Handle library version change
    event.getChanges(LibraryPropertiesEntity::class.java).forEach {
      when (it) {
        is EntityChange.Added, is EntityChange.Replaced -> changedLibraryEntities.add(it.newEntity?.library)
        else -> {}
      }
    }

    val settings = RepositoryLibrarySettings.getInstanceOrDefaults(project)
    val utils = RepositoryLibraryUtils.getInstance(project)
    utils.computePropertiesForLibraries(libraries = changedLibraryEntities.filterNotNull().toSet(),
                                        buildSha256Checksum = settings.isSha256ChecksumAutoBuildEnabled(),
                                        guessAndBindRemoteRepository = settings.isJarRepositoryAutoBindEnabled())
  }
}