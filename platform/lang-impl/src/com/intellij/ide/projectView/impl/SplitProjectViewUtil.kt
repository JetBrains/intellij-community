@file:ApiStatus.Internal
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.configurationStore.StateStorageBase
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.getStateSpecOrError
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.ide.productMode.IdeProductMode
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

fun isProjectViewSplit(): Boolean {
  return RegistryManager.getInstance().isProjectViewSplit()
}

suspend fun isProjectViewSplitAsync(): Boolean {
  return RegistryManager.getInstanceAsync().isProjectViewSplit()
}

private fun RegistryManager.isProjectViewSplit(): Boolean {
  if (IdeProductMode.isMonolith) {
    return `is`("project.view.toolwindow.split") || `is`("project.view.toolwindow.split.monolith")
  }
  else {
    return `is`("project.view.toolwindow.split") || `is`("project.view.toolwindow.split.remdev")
  }
}

/**
 * Loads the [ProjectViewState] of the [project] from its storage.
 *
 * The result does not include the changes that the remdev settings sync applies to the loaded [ProjectViewState].
 * It includes them only after the next save of the project settings.
 *
 * @return the stored state, or `null` if the storage has no state for the component
 */
fun loadStoredProjectViewState(project: Project): ProjectViewState? {
  val stateSpec = getStateSpecOrError(ProjectViewState::class.java)
  val storage = project.stateStore.storageManager.getStateStorage(stateSpec.storages.first { !it.deprecated })
  if (storage !is StateStorageBase<*>) return null
  return storage.getSerializedState(stateSpec.name)?.let { deserialize<ProjectViewState>(it) }
}

private fun <T : Any> StateStorageBase<T>.getSerializedState(componentName: String): Element? {
  return getSerializedState(storageData = getStorageData(), component = null, componentName = componentName, archive = false)
}
