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
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkEntityBuilder
import com.intellij.platform.workspace.storage.*
import com.intellij.workspaceModel.ide.impl.getInternalEnvironmentName
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.mutableSdkMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridge
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridgeRegistry
import com.intellij.workspaceModel.ide.toPath
import java.nio.file.InvalidPathException
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves the effective [EelDescriptor] (environment) that the Global Workspace Model
 * should associate with this [SdkEntity].
 *
 * Behavior depends on the per-environment model separation flag
 * (`ide.workspace.model.per.environment.model.separation`):
 *
 * - When separation is ON, the environment is inferred from `homePath`, so the SDK is attributed
 *   to the machine that actually owns the path (e.g., local OS, a particular WSL distribution,
 *   or a remote/container environment).
 * - When separation is OFF, [LocalEelDescriptor] is always returned so SDKs from different
 *   environments are kept together in a single, shared global model (compatibility mode for IDEs
 *   that expect cross-environment SDK visibility).
 * - If `homePath` is `null`, cannot be converted to a path, or throws [InvalidPathException],
 *   the method falls back to [LocalEelDescriptor].
 *
 * The resolved descriptor is used to:
 * - decide which [com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel] instance
 *   (per environment) should store the SDK entity;
 * - filter which per-environment bridges should create/load SDK bridges, preventing unrelated
 *   environments from processing changes.
 */
private fun SdkEntity.getAssociatedEffectiveWorkspaceEelDescriptor(): EelDescriptor {
  if (!Registry.`is`("ide.workspace.model.per.environment.model.separation")) {
    return LocalEelDescriptor
  }
  return try {
    homePath?.toPath()?.getEelDescriptor() ?: LocalEelDescriptor
  }
  catch (_: InvalidPathException) {
    LocalEelDescriptor
  }
}

private class GlobalSdkBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = true

  override fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkEntity::class.java] as? List<EntityChange<SdkEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkEntity>>()
    val projectEelDescriptor = project.getEelDescriptor()
    val environmentName = projectEelDescriptor.machine.getInternalEnvironmentName()

    for (addChange in addChanges) {
      if (addChange.newEntity.getAssociatedEffectiveWorkspaceEelDescriptor() != projectEelDescriptor) continue
      // Will initialize the bridge if missing
      builder.mutableSdkMap.getOrPutDataByEntity(addChange.newEntity) {
        val sdkEntityCopy = SdkBridgeImpl.createEmptySdkEntity("", "", environmentName = environmentName)
        sdkEntityCopy.applyChangesFrom(addChange.newEntity)
        ProjectJdkImpl(SdkBridgeImpl(sdkEntityCopy, environmentName))
      }
    }
  }
}

private class GlobalSdkBridgesLoader(private val eelMachine: EelMachine) : GlobalSdkTableBridge {
  override fun initializeBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                             initialEntityStorage: VersionedEntityStorage): () -> Unit {
    val environmentName = eelMachine.getInternalEnvironmentName()
    val sdks = mutableStorage
      .entities(SdkEntity::class.java)
      .filter { mutableStorage.sdkMap.getDataByEntity(it) == null }
      .filter { it.getAssociatedEffectiveWorkspaceEelDescriptor().machine == eelMachine }
      .map { sdkEntity ->
        val sdkEntityBuilder = sdkEntity.createEntityTreeCopy(false) as SdkEntityBuilder
        sdkEntity to ProjectJdkImpl(SdkBridgeImpl(sdkEntityBuilder, environmentName))
      }
      .toList()
    thisLogger().debug("Initial load of SDKs")

    for ((entity, sdkBridge) in sdks) {
      mutableStorage.mutableSdkMap.addIfAbsent(entity, sdkBridge)
    }
    return {}
  }

  override fun initializeBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkEntity::class.java] as? List<EntityChange<SdkEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkEntity>>()

    val environmentName = eelMachine.getInternalEnvironmentName()

    for (addChange in addChanges) {
      if (addChange.newEntity.getAssociatedEffectiveWorkspaceEelDescriptor().machine != eelMachine) continue
      // Will initialize the bridge if missing
      builder.mutableSdkMap.getOrPutDataByEntity(addChange.newEntity) {
        val sdkEntityCopy = SdkBridgeImpl.createEmptySdkEntity("", "", environmentName = environmentName)
        sdkEntityCopy.applyChangesFrom(addChange.newEntity)
        ProjectJdkImpl(SdkBridgeImpl(sdkEntityCopy, environmentName))
      }
    }
  }

  override fun handleBeforeChangeEvents(event: VersionedStorageChange) {}

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