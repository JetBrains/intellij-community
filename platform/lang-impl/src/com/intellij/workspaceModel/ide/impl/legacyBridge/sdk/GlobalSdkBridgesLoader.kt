// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.platform.backend.workspace.BridgeInitializer
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.mutableSdkMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridge

class GlobalSdkBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = GlobalSdkTableBridge.isEnabled()

  override fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkEntity::class.java] as? List<EntityChange<SdkEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableSdkMap.getOrPutDataByEntity(addChange.entity) {
        val sdkEntityCopy = SdkBridgeImpl.createEmptySdkEntity("", "", "")
        sdkEntityCopy.applyChangesFrom(addChange.entity)
        ProjectJdkImpl(SdkBridgeImpl(sdkEntityCopy))
      }
    }
  }
}

class GlobalSdkBridgesLoader: GlobalSdkTableBridge {

  override fun initializeSdkBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                                initialEntityStorage: VersionedEntityStorage): () -> Unit {
    val sdks = mutableStorage
      .entities(SdkEntity::class.java)
      .filter { mutableStorage.sdkMap.getDataByEntity(it) == null }
      .map { sdkEntity -> sdkEntity to ProjectJdkImpl(SdkBridgeImpl(sdkEntity as SdkEntity.Builder)) }
      .toList()
    thisLogger().debug("Initial load of SDKs")

    for ((entity, sdkBridge) in sdks) {
      mutableStorage.mutableSdkMap.addIfAbsent(entity, sdkBridge)
    }
    return {}
  }

  override fun initializeSdkBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkEntity::class.java] as? List<EntityChange<SdkEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableSdkMap.getOrPutDataByEntity(addChange.entity) {
        val sdkEntityCopy = SdkBridgeImpl.createEmptySdkEntity("", "", "")
        sdkEntityCopy.applyChangesFrom(addChange.entity)
        ProjectJdkImpl(SdkBridgeImpl(sdkEntityCopy))
      }
    }
  }

  override fun handleBeforeChangeEvents(event: VersionedStorageChange) { }

  override fun handleChangedEvents(event: VersionedStorageChange) {
    // Since the listener is not deprecated, it will be better to keep the order of events as remove -> replace -> add
    val changes = event.getChanges(SdkEntity::class.java).orderToRemoveReplaceAdd()
    if (changes.isEmpty()) return

    for (change in changes) {
      LOG.debug { "Process sdk change $change" }
      when (change) {
        is EntityChange.Added -> {
          val createdSdkBridge = event.storageAfter.sdkMap.getDataByEntity(change.entity)
                                      ?: error("Sdk bridge should be created before in `GlobalWorkspaceModel.initializeBridges`")
          ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectJdkTable.JDK_TABLE_TOPIC).jdkAdded(createdSdkBridge)
        }
        is EntityChange.Replaced -> {
          val previousName = change.oldEntity.name
          val newName = change.newEntity.name

          if (previousName != newName) {
            event.storageBefore.sdkMap.getDataByEntity(change.oldEntity)?.let { sdkBridge ->
              // fire changes because after renaming JDK its name may match the associated jdk name of modules/project
              ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectJdkTable.JDK_TABLE_TOPIC).jdkNameChanged(sdkBridge, previousName)
            }
          }
        }
        is EntityChange.Removed -> {
          val sdkBridge = event.storageBefore.sdkMap.getDataByEntity(change.entity)
          LOG.debug { "Fire 'jdkRemoved' event for ${change.entity.name}, sdk = $sdkBridge" }
          if (sdkBridge != null) {
            ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectJdkTable.JDK_TABLE_TOPIC).jdkRemoved(sdkBridge)
          }
        }
      }
    }
  }


  companion object {
    private val LOG = logger<GlobalSdkBridgesLoader>()
  }
}