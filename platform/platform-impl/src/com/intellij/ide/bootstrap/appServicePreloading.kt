// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bootstrap

import com.intellij.diagnostic.DebugLogManager
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.subtask
import com.intellij.history.LocalHistory
import com.intellij.ide.GeneralSettings
import com.intellij.ide.ScreenReaderStateManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.ManagingFS
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun CoroutineScope.preloadCriticalServices(app: ApplicationImpl, asyncScope: CoroutineScope) {
  launch(CoroutineName("PathMacros preloading")) {
    // required for any persistence state component (pathMacroSubstitutor.expandPaths), so, preload
    app.serviceAsync<PathMacros>()
  }

  launch {
    // loading is started by StartupUtil, here we just "join" it
    subtask("ManagingFS preloading") { app.serviceAsync<ManagingFS>() }

    // PlatformVirtualFileManager also wants ManagingFS
    launch { app.serviceAsync<VirtualFileManager>() }

    launch {
      // loading is started above, here we just "join" it
      app.serviceAsync<PathMacros>()

      // required for indexing tasks (see JavaSourceModuleNameIndex, for example)
      // FileTypeManager by mistake uses PropertiesComponent instead of own state - it should be fixed someday
      app.serviceAsync<PropertiesComponent>()

      asyncScope.launch {
        // wants PropertiesComponent
        launch { app.serviceAsync<DebugLogManager>() }

        app.serviceAsync<RegistryManager>()
        // wants RegistryManager
        if (!app.isHeadlessEnvironment) {
          app.serviceAsync<PerformanceWatcher>()
          // cache it as IdeEventQueue should use loaded PerformanceWatcher service as soon as it is ready (getInstanceIfCreated is used)
          PerformanceWatcher.getInstance()
        }
      }
    }

    // LocalHistory wants ManagingFS.
    // It should be fixed somehow, but for now, to avoid thread contention, preload it in a controlled manner.
    asyncScope.launch { app.getServiceAsyncIfDefined(LocalHistory::class.java) }
  }

  if (!app.isHeadlessEnvironment) {
    asyncScope.launch {
      // loading is started above, here we just "join" it
      // KeymapManager is a PersistentStateComponent
      app.serviceAsync<PathMacros>()

      launch(CoroutineName("UISettings preloading")) { app.serviceAsync<UISettings>() }
      launch(CoroutineName("CustomActionsSchema preloading")) { app.serviceAsync<CustomActionsSchema>() }
      // wants PathMacros
      launch(CoroutineName("GeneralSettings preloading")) { app.serviceAsync<GeneralSettings>() }

      // ActionManager uses KeymapManager
      subtask("KeymapManager preloading") { app.serviceAsync<KeymapManager>() }
      subtask("ActionManager preloading") { app.serviceAsync<ActionManager>() }

      // serviceAsync is not supported for light services
      app.service<ScreenReaderStateManager>()
    }
  }
}