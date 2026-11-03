// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CacheFileComponents")

package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Lists the application components with the `$CACHE_FILE$` storage as `<plugin id>\t<component name>`.
 *
 * A key of such a component in the cache store starts with `<plugin id>.<component name>`.
 * The reused IDE recovery of IDE Starter finds the component of a changed key with this list.
 * The answer is `null` when the application store cannot list its components.
 */
@ApiStatus.Internal
@TestOnly
fun describeCacheFileComponents(): List<String>? {
  val store = ApplicationManager.getApplication().stateStore as? ComponentStoreImpl ?: return null
  return store.getComponents().entries
    .filter { [_, info] -> info.stateSpec?.storages?.any { it.value == StoragePathMacros.CACHE_FILE } == true }
    .map { [name, info] -> "${info.pluginId.idString}\t$name" }
    .sorted()
}

/**
 * Reloads the application components with these names from their storage, and answers the names that cannot reload.
 *
 * The reused IDE recovery of IDE Starter writes the `$CACHE_FILE$` state of a component back, and then reloads the component.
 * When one of the components cannot reload, nothing reloads. When the application store cannot reload, the answer names
 * each component.
 */
@ApiStatus.Internal
@TestOnly
fun reloadCacheFileComponents(componentNames: List<String>): List<String> {
  val store = ApplicationManager.getApplication().stateStore as? ComponentStoreImpl ?: return componentNames
  val notReloadable = store.getNotReloadableComponents(componentNames)
  if (notReloadable.isNotEmpty()) {
    return notReloadable.toList()
  }
  val cacheStorages = componentNames.flatMapTo(HashSet()) { name ->
    val storages = store.getComponents()[name]?.stateSpec?.storages ?: emptyArray()
    storages.filter { it.value == StoragePathMacros.CACHE_FILE }.map { store.storageManager.getStateStorage(it) }
  }
  // With its storage marked as changed, a component that has no stored state loads its initial state.
  runBlockingMaybeCancellable {
    store.reinitComponents(componentNames.toSet(), changedStorages = cacheStorages, notReloadableComponents = emptySet())
  }
  return emptyList()
}
