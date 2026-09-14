// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.conversion.ConversionService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.project.stateStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Saves every store once, in parallel. Returns `true` if every save succeeds. [saveSettings] logs a failed save.
 */
internal suspend fun saveSettingsBatch(componentManagers: List<ComponentManager>): Boolean {
  try {
    return coroutineScope {
      componentManagers.distinct().map { componentManager ->
        async {
          val saved = saveSettings(componentManager, forceSavingAllSettings = true)
          if (componentManager is Project && !ApplicationManager.getApplication().isUnitTestMode) {
            runCatching { saveConversionResult(componentManager) }.getOrLogException(LOG)
          }
          saved
        }
      }.awaitAll().all { it }
    }
  }
  finally {
    ManagingFS.getInstance().flushPendingUpdates()
  }
}

/**
 * Updates the last-modified stamp of the project files that changed between the project open and the close.
 */
private suspend fun saveConversionResult(project: Project) {
  val descriptor = project.stateStore.storeDescriptor
  val path = if (descriptor.dotIdea == null) descriptor.presentableUrl else descriptor.historicalProjectBasePath
  (project as ComponentManagerEx).getServiceAsyncIfDefined(ConversionService::class.java)?.saveConversionResult(path)
}
