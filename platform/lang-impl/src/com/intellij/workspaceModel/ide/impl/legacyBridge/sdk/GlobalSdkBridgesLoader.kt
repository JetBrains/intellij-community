// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.BridgeInitializer
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.mutableSdkMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridge
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridgeRegistry
import com.intellij.workspaceModel.ide.toPath
import java.util.concurrent.ConcurrentHashMap

private class GlobalSdkBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = true

  override fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkEntity::class.java] as? List<EntityChange<SdkEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableSdkMap.getOrPutDataByEntity(addChange.newEntity) {
        val sdkEntityCopy = SdkBridgeImpl.createEmptySdkEntity("", "", "")
        sdkEntityCopy.applyChangesFrom(addChange.newEntity)
        ProjectJdkImpl(SdkBridgeImpl(sdkEntityCopy))
      }
    }
  }
}

private class GlobalSdkBridgesLoader(private val eelMachine: EelMachine) : GlobalSdkTableBridge {
  override fun initializeBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                             initialEntityStorage: VersionedEntityStorage): () -> Unit {
    val sdks = mutableStorage
      .entities(SdkEntity::class.java)
      .filter { mutableStorage.sdkMap.getDataByEntity(it) == null }
      .map { sdkEntity ->
        val sdkEntityBuilder = sdkEntity.createEntityTreeCopy(false) as SdkEntity.Builder
        sdkEntity to ProjectJdkImpl(SdkBridgeImpl(sdkEntityBuilder))
      }
      .toList()
    thisLogger().debug("Initial load of SDKs")

    for ((entity, sdkBridge) in sdks) {
      if (shouldSkipEntityProcessing(entity)) {
        // The SDKs are populated from a single file for now. All loaded SDK entities go to the same entity storage
        // We want to avoid having alien SDKs in storages, hence we filter them
        mutableStorage.removeEntity(entity)
      }
      else {
        mutableStorage.mutableSdkMap.addIfAbsent(entity, sdkBridge)
      }
    }
    return {}
  }

  override fun initializeBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkEntity::class.java] as? List<EntityChange<SdkEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkEntity>>()

    for (addChange in addChanges) {
      if (shouldSkipEntityProcessing(addChange.newEntity)) {
        continue
      }

      // Will initialize the bridge if missing
      builder.mutableSdkMap.getOrPutDataByEntity(addChange.newEntity) {
        val sdkEntityCopy = SdkBridgeImpl.createEmptySdkEntity("", "", "")
        sdkEntityCopy.applyChangesFrom(addChange.newEntity)
        ProjectJdkImpl(SdkBridgeImpl(sdkEntityCopy))
      }
    }
  }

  override fun handleBeforeChangeEvents(event: VersionedStorageChange) { }

  override fun handleChangedEvents(event: VersionedStorageChange) {
    val changes = event.getChanges(SdkEntity::class.java)
    if (changes.isEmpty()) return

    for (change in changes) {
      if ((change.newEntity ?: change.oldEntity)?.let(::shouldSkipEntityProcessing) == true) {
        continue
      }
      LOG.debug { "Process sdk change $change" }
      when (change) {
        is EntityChange.Added -> {
          val createdSdkBridge = event.storageAfter.sdkMap.getDataByEntity(change.newEntity)
                                      ?: error("Sdk bridge should be created before in `GlobalWorkspaceModel.initializeBridges`")
          ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectJdkTable.JDK_TABLE_TOPIC).jdkAdded(createdSdkBridge)
        }
        is EntityChange.Replaced -> {
          val previousName = change.oldEntity.name
          val newName = change.newEntity.name

          if (previousName != newName) {
            event.storageBefore.sdkMap.getDataByEntity(change.oldEntity)?.let { sdkBridge ->
              // fire changes because after renaming JDK, its name may match the associated jdk name of modules/project
              ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectJdkTable.JDK_TABLE_TOPIC).jdkNameChanged(sdkBridge, previousName)
            }
          }
        }
        is EntityChange.Removed -> {
          val sdkBridge = event.storageBefore.sdkMap.getDataByEntity(change.oldEntity)
          LOG.debug { "Fire 'jdkRemoved' event for ${change.oldEntity.name}, sdk = $sdkBridge" }
          if (sdkBridge != null) {
            ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectJdkTable.JDK_TABLE_TOPIC).jdkRemoved(sdkBridge)
          }
        }
      }
    }
  }

  /**
   * The events about changes in global models are broadcasted to all existing `GlobalWorkspaceModel`s.
   * Not all of them are interested in every change, especially if the change happens in an unrelated environment.
   */
  private fun shouldSkipEntityProcessing(entity: SdkEntity): Boolean {
    if (!Registry.`is`("ide.workspace.model.per.environment.model.separation")) {
      return false
    }
    return entity.homePath?.toPath()?.getEelDescriptor()?.machine != eelMachine
  }
}

private val LOG = logger<GlobalSdkBridgesLoader>()

private class GlobalSdkTableBridgeRegistryImpl : GlobalSdkTableBridgeRegistry {
  private val registry = ConcurrentHashMap<EelMachine, GlobalSdkTableBridge>()

  override fun getTableBridge(eelMachine: EelMachine): GlobalSdkTableBridge {
    return registry.computeIfAbsent(eelMachine) {
      GlobalSdkBridgesLoader(eelMachine)
    }
  }
}