// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.projectImport.ProjectEntitiesAttacher
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class JpsEntitiesProjectEntitiesAttacher : ProjectEntitiesAttacher {
  override fun extractEntitiesToAttach(currentSnapshot: EntityStorage, attachingProjectStorage: EntityStorage): MutableEntityStorage {
    return MutableEntityStorage.create().also { newStorage ->
      newStorage.replaceBySource({ it is JpsFileEntitySource && it !is JpsGlobalFileEntitySource }, attachingProjectStorage)

      // ProjectSettingsEntity is a singleton, do not duplicate it
      if (currentSnapshot.entities(ProjectSettingsEntity::class.java).toList().isNotEmpty()) {
        newStorage.entities(ProjectSettingsEntity::class.java).toList().forEach { newStorage.removeEntity(it) }
      }
    }
  }
}
