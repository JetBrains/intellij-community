// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION", "removal")
internal val Storage.path: String
  get() = value.ifEmpty { file }

internal fun createComponentInfo(
  component: Any,
  stateSpec: State?,
  serviceDescriptor: ServiceDescriptor?,
  pluginId: PluginId,
): ComponentInfo {
  val result = when (component) {
    is PersistentStateComponentWithModificationTracker<*> -> {
      ComponentWithStateModificationTrackerInfo(
        pluginId = pluginId,
        component = component,
        stateSpec = stateSpec,
        configurationSchemaKey = serviceDescriptor?.configurationSchemaKey,
      )
    }
    is ModificationTracker -> {
      ComponentWithModificationTrackerInfo(
        pluginId = pluginId,
        component = component,
        stateSpec = stateSpec,
        configurationSchemaKey = serviceDescriptor?.configurationSchemaKey,
      )
    }
    else -> {
      ComponentInfoImpl(pluginId = pluginId, component = component, stateSpec = stateSpec)
    }
  }

  if (stateSpec != null && stateSpec.storages.isNotEmpty() && stateSpec.storages.all { it.deprecated || isUseSaveThreshold(it) }) {
    result.lastSaved = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
  }
  return result
}

private fun isUseSaveThreshold(storage: Storage): Boolean {
  return storage.useSaveThreshold != ThreeState.NO && getEffectiveRoamingType(storage.roamingType, storage.path) === RoamingType.DISABLED
}

@ApiStatus.Internal
sealed class ComponentInfo {
  abstract val pluginId: PluginId

  open val configurationSchemaKey: String?
    get() = null

  abstract val component: Any
  abstract val stateSpec: State?

  abstract val lastModificationCount: Long
  abstract val currentModificationCount: Long

  abstract val isModificationTrackingSupported: Boolean

  @Volatile
  @JvmField
  internal var lastSaved: Int = -1

  @JvmField
  var affectedPropertyNames: List<String> = emptyList()

  open fun updateModificationCount(newCount: Long) {
  }
}

internal class ComponentInfoImpl(
  override val pluginId: PluginId,
  override val component: Any,
  override val stateSpec: State?,
) : ComponentInfo() {
  override val isModificationTrackingSupported: Boolean = false

  override val lastModificationCount: Long
    get() = -1

  override val currentModificationCount: Long
    get() = -1
}

private abstract class ModificationTrackerAwareComponentInfo : ComponentInfo() {
  final override val isModificationTrackingSupported = true

  abstract override var lastModificationCount: Long

  final override fun updateModificationCount(newCount: Long) {
    lastModificationCount = newCount
  }
}

private class ComponentWithStateModificationTrackerInfo(
  override val pluginId: PluginId,
  override val component: PersistentStateComponentWithModificationTracker<*>,
  override val stateSpec: State?,
  override val configurationSchemaKey: String?,
) : ModificationTrackerAwareComponentInfo() {
  override val currentModificationCount: Long
    get() = component.stateModificationCount

  override var lastModificationCount = currentModificationCount
}

private class ComponentWithModificationTrackerInfo(
  override val pluginId: PluginId,
  override val component: ModificationTracker,
  override val stateSpec: State?,
  override val configurationSchemaKey: String?,
) : ModificationTrackerAwareComponentInfo() {
  override val currentModificationCount: Long
    get() = component.modificationCount

  override var lastModificationCount = currentModificationCount
}

internal fun getEffectiveRoamingType(roamingType: RoamingType, collapsedPath: String): RoamingType {
  if (isSpecialOrNonRoamableStorage(collapsedPath)) {
    return RoamingType.DISABLED
  }
  else {
    return roamingType
  }
}

internal fun isSpecialOrNonRoamableStorage(collapsedPath: String): Boolean {
  return collapsedPath == StoragePathMacros.WORKSPACE_FILE ||
         collapsedPath == StoragePathMacros.NON_ROAMABLE_FILE ||
         isSpecialStorage(collapsedPath)
}

internal fun isSpecialStorage(collapsedPath: String): Boolean {
  return collapsedPath == StoragePathMacros.CACHE_FILE || collapsedPath == StoragePathMacros.PRODUCT_WORKSPACE_FILE
}