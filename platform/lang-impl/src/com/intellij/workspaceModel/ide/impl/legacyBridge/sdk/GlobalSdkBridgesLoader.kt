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
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.getEelMachine
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkEntityBuilder
import com.intellij.platform.workspace.storage.*
import com.intellij.workspaceModel.ide.impl.getInternalEnvironmentName
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.mutableSdkMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridge
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridgeRegistry
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.util.concurrent.ConcurrentHashMap

/**
 * Determines whether this [EelMachine] owns the given [SdkEntity] based on its home path.
 *
 * Behavior depends on the per-environment model separation flag
 * (`ide.workspace.model.per.environment.model.separation`):
 *
 * - When separation is ON, ownership is determined by checking if the SDK's home path
 *   belongs to this machine via [EelMachine.ownsPath]. This ensures SDKs are attributed
 *   to the machine that actually owns the path (e.g., local OS, a particular WSL distribution,
 *   or a remote/container environment).
 * - When separation is OFF, returns `true` for all SDKs regardless of their home path,
 *   so SDKs from different environments are kept together in a single, shared global model
 *   (compatibility mode for IDEs that expect cross-environment SDK visibility).
 * - If `homePath` is `null`, cannot be converted to a path, or throws [InvalidPathException],
 *   the method checks if this machine is the [LocalEelMachine].
 *
 * The ownership determination is used to:
 * - decide which [com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel] instance
 *   (per environment) should store the SDK entity;
 * - filter which per-environment bridges should create/load SDK bridges, preventing unrelated
 *   environments from processing changes.
 *
 * @see com.intellij.platform.eel.EelMachine.ownsPath
 * @see com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
 */
private fun EelMachine.ownsSdkEntry(sdkEntity: SdkEntity): Boolean {
  if (!Registry.`is`("ide.workspace.model.per.environment.model.separation", false)) {
    return true
  }

  return try {
    val nioPath = sdkEntity.homePath?.toPath()

    return if (nioPath != null) {
      ownsPath(nioPath)
    }
    else {
      this == LocalEelMachine
    }
  }
  catch (_: InvalidPathException) {
    this == LocalEelMachine
  }
}

private class GlobalSdkBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = true

  override fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkEntity::class.java] as? List<EntityChange<SdkEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkEntity>>()
    val projectEelMachine = project.getEelMachine()
    val environmentName = projectEelMachine.getInternalEnvironmentName()

    for (addChange in addChanges) {
      if (!projectEelMachine.ownsSdkEntry(addChange.newEntity)) continue
      // Will initialize the bridge if missing
      builder.mutableSdkMap.getOrPutDataByEntity(addChange.newEntity) {
        val sdkEntityCopy = SdkBridgeImpl.createEmptySdkEntity("", "", environmentName = environmentName)
        sdkEntityCopy.applyChangesFrom(addChange.newEntity)
        ProjectJdkImpl(SdkBridgeImpl(sdkEntityCopy, environmentName))
      }
    }
  }
}

@ApiStatus.Internal
fun initializeSdkBridges(mutableStorage: MutableEntityStorage, eelMachine: EelMachine) {
  val environmentName = eelMachine.getInternalEnvironmentName()
  val sdks = mutableStorage
    .entities(SdkEntity::class.java)
    .filter { mutableStorage.sdkMap.getDataByEntity(it) == null }
    .filter(eelMachine::ownsSdkEntry)
    .map { sdkEntity ->
      val sdkEntityBuilder = sdkEntity.createEntityTreeCopy(false) as SdkEntityBuilder
      sdkEntity to ProjectJdkImpl(SdkBridgeImpl(sdkEntityBuilder, environmentName))
    }
    .toList()

  for ((entity, sdkBridge) in sdks) {
    mutableStorage.mutableSdkMap.addIfAbsent(entity, sdkBridge)
  }
}

private class GlobalSdkBridgesLoader(private val eelMachine: EelMachine) : GlobalSdkTableBridge {
  override fun initializeBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                             initialEntityStorage: VersionedEntityStorage): () -> Unit {
    initializeSdkBridges(mutableStorage, eelMachine)
    thisLogger().debug("Initial load of SDKs")
    return {}
  }

  override fun initializeBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val sdkChanges = (changes[SdkEntity::class.java] as? List<EntityChange<SdkEntity>>) ?: emptyList()
    val addChanges = sdkChanges.filterIsInstance<EntityChange.Added<SdkEntity>>()

    val environmentName = eelMachine.getInternalEnvironmentName()

    for (addChange in addChanges) {
      if (!eelMachine.ownsSdkEntry(addChange.newEntity)) continue
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
    return !eelMachine.ownsSdkEntry(entity)
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