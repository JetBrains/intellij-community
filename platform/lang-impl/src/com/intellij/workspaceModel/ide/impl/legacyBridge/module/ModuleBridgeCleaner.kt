// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnSnapshot
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ModuleBridgeCleaner : WorkspaceModelChangeListener {
  override fun beforeChanged(event: VersionedStorageChange) {
    event.getChanges(ModuleEntity::class.java)
      .forEach {
        if (it !is EntityChange.Removed<ModuleEntity>) return@forEach

        val bridge = event.storageBefore.moduleMap.getDataByEntity(it.oldEntity)
        bridge?.entityStorage = VersionedEntityStorageOnSnapshot(event.storageBefore)
      }
  }
}
