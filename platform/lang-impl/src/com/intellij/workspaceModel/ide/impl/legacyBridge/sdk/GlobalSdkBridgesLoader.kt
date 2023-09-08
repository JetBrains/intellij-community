// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.BridgeInitializer
import com.intellij.platform.workspace.jps.entities.SdkMainEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkTableBridgeImpl.Companion.mutableSdkMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkTableBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.sdk.GlobalSdkTableBridge

class GlobalSdkBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = GlobalSdkTableBridge.isEnabled()

  override fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkMainEntity::class.java] as? List<EntityChange<SdkMainEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkMainEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableSdkMap.getOrPutDataByEntity(addChange.entity) {
        val sdkEntityCopy = SdkTableBridgeImpl.createEmptySdkEntity("", "", "")
        sdkEntityCopy.applyChangesFrom(addChange.entity)
        SdkBridgeImpl(sdkEntityCopy)
      }
    }
  }
}

class GlobalSdkBridgesLoader: GlobalSdkTableBridge {

  override fun initializeSdkBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                                initialEntityStorage: VersionedEntityStorage): () -> Unit {
    val sdks = mutableStorage
      .entities(SdkMainEntity::class.java)
      .filter { mutableStorage.sdkMap.getDataByEntity(it) == null }
      .map { sdkEntity -> sdkEntity to SdkBridgeImpl(sdkEntity as SdkMainEntity.Builder) }
      .toList()
    thisLogger().debug("Initial load of SDKs")

    for ((entity, sdkBridge) in sdks) {
      mutableStorage.mutableSdkMap.addIfAbsent(entity, sdkBridge)
    }
    return {}
  }

  override fun initializeSdkBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkMainEntity::class.java] as? List<EntityChange<SdkMainEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkMainEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableSdkMap.getOrPutDataByEntity(addChange.entity) {
        val sdkEntityCopy = SdkTableBridgeImpl.createEmptySdkEntity("", "", "")
        sdkEntityCopy.applyChangesFrom(addChange.entity)
        SdkBridgeImpl(sdkEntityCopy)
      }
    }
  }
}