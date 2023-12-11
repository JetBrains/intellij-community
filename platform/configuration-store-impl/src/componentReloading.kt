// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.configurationStore.schemeManager.SchemeManagerImpl
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.options.SchemeManagerFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun ComponentStoreImpl.reloadComponents(changedFileSpecs: Collection<String>,
                                        deletedFileSpecs: Collection<String>,
                                        componentNames2reload: Set<String>? = null
) {
  LOG.debug { "reloadComponents: changed=$changedFileSpecs, deleted=$deletedFileSpecs, componentNames2=$componentNames2reload" }
  val schemeManagerFactory = SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase
  val storageManager = storageManager as StateStorageManagerImpl
  val (changed, deleted) = storageManager.getCachedFileStorages(changedFileSpecs, deletedFileSpecs, null)

  val changedComponentNames = componentNames2reload ?: LinkedHashSet<String>().also {
    updateStateStorage(it, changed, false)
    updateStateStorage(it, deleted, true)
    LOG.debug { "calculated changed component names: $it" }
  }

  val schemeManagersToReload = calcSchemeManagersToReload(changedFileSpecs + deletedFileSpecs, schemeManagerFactory)
  for (schemeManager in schemeManagersToReload) {
    if (schemeManager.fileSpec == "colors") {
      EditorColorsManager.getInstance().reloadKeepingActiveScheme()
    }
    else {
      schemeManager.reload()
    }
  }

  val notReloadableComponents = getNotReloadableComponents(changedComponentNames)
  LOG.debug { "non-reloadable components: $notReloadableComponents" }
  reinitComponents(changedComponentNames, (changed + deleted).toSet(), notReloadableComponents)
}

private fun updateStateStorage(changedComponentNames: MutableSet<String>, stateStorages: Collection<StateStorage>, deleted: Boolean) {
  for (stateStorage in stateStorages) {
    try {
      // todo maybe we don't need "from stream provider" here since we modify the settings in place?
      (stateStorage as XmlElementStorage).updatedFromStreamProvider(changedComponentNames, deleted)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
}

private fun calcSchemeManagersToReload(pathsToCheck: List<String>,
                                       schemeManagerFactory: SchemeManagerFactoryBase): List<SchemeManagerImpl<*, *>> {
  val schemeManagersToReload = mutableListOf<SchemeManagerImpl<*, *>>()
  schemeManagerFactory.process {
    if (shouldReloadSchemeManager(it, pathsToCheck)) {
      schemeManagersToReload.add(it)
    }
  }
  return schemeManagersToReload
}

private fun shouldReloadSchemeManager(schemeManager: SchemeManagerImpl<*, *>, pathsToCheck: Collection<String>): Boolean {
  val fileSpec = schemeManager.fileSpec
  return pathsToCheck.any { path ->
    fileSpec == path || path.startsWith("$fileSpec/")
  }
}
