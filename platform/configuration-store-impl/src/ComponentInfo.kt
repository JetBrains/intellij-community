package com.intellij.configurationStore

import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.util.ModificationTracker
import java.util.concurrent.TimeUnit

internal fun createComponentInfo(component: Any, stateSpec: State?): ComponentInfo {
  val componentInfo = when (component) {
    is ModificationTracker -> ComponentWithModificationTrackerInfo(component, stateSpec)
    is PersistentStateComponentWithModificationTracker<*> -> ComponentWithStateModificationTrackerInfo(component, stateSpec!!)
    else -> ComponentInfoImpl(component, stateSpec)
  }

  if (stateSpec != null && !stateSpec.storages.isEmpty() && stateSpec.storages.all { it.roamingType === RoamingType.DISABLED }) {
    componentInfo.lastSaved = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
  }

  return componentInfo
}

internal abstract class ComponentInfo {
  abstract val component: Any
  abstract val stateSpec: State?

  abstract val lastModificationCount: Long
  abstract val currentModificationCount: Long

  abstract val isModificationTrackingSupported: Boolean

  var lastSaved: Int = -1

  open fun updateModificationCount(newCount: Long = currentModificationCount) {
  }
}

internal class ComponentInfoImpl(override val component: Any, override val stateSpec: State?) : ComponentInfo() {
  override val isModificationTrackingSupported = false

  override val lastModificationCount: Long
    get() = -1

  override val currentModificationCount: Long
    get() = -1
}

private abstract class ModificationTrackerAwareComponentInfo : ComponentInfo() {
  override final val isModificationTrackingSupported = true

  override abstract var lastModificationCount: Long

  override final fun updateModificationCount(newCount: Long) {
    lastModificationCount = newCount
  }
}

private class ComponentWithStateModificationTrackerInfo(override val component: PersistentStateComponentWithModificationTracker<*>, override val stateSpec: State) : ModificationTrackerAwareComponentInfo() {
  override val currentModificationCount: Long
    get() = component.stateModificationCount

  override var lastModificationCount = currentModificationCount
}

private class ComponentWithModificationTrackerInfo(override val component: ModificationTracker, override val stateSpec: State?) : ModificationTrackerAwareComponentInfo() {
  override val currentModificationCount: Long
    get() = component.modificationCount

  override var lastModificationCount = currentModificationCount
}