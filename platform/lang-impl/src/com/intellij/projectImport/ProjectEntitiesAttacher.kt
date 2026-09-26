// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectImport

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProjectEntitiesAttacher {
  fun extractEntitiesToAttach(storage: EntityStorage): MutableEntityStorage

  companion object {
    val EP: ExtensionPointName<ProjectEntitiesAttacher> = ExtensionPointName("com.intellij.projectEntitiesAttacher")

    fun getAllEntitiesToMigrate(attachingProjectStorage: EntityStorage): MutableEntityStorage {
      val newStorage = MutableEntityStorage.create()
      EP.forEachExtensionSafe { e -> newStorage.applyChangesFrom(e.extractEntitiesToAttach(attachingProjectStorage)) }
      return newStorage
    }
  }
}