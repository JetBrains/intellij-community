// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange

private class RepositoryLibraryChangeListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    val settings = RepositoryLibrarySettings.getInstanceOrDefaults(project)
    val jarRepositoryAutoBindEnabled = settings.isJarRepositoryAutoBindEnabled()
    val sha256ChecksumAutoBuildEnabled = settings.isSha256ChecksumAutoBuildEnabled()

    // exit fast, avoid heavy computations if the feature is not enabled
    if (!jarRepositoryAutoBindEnabled && !sha256ChecksumAutoBuildEnabled) {
      return
    }

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

    val utils = RepositoryLibraryUtils.getInstance(project)
    utils.computeExtendedPropertiesFor(libraries = changedLibraryEntities.filterNotNull().toSet(),
                                       buildSha256Checksum = sha256ChecksumAutoBuildEnabled,
                                       guessAndBindRemoteRepository = jarRepositoryAutoBindEnabled)
  }
}