// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bootstrap

import com.intellij.diagnostic.*
import com.intellij.history.LocalHistory
import com.intellij.ide.GeneralSettings
import com.intellij.ide.ScreenReaderStateManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException

fun CoroutineScope.preloadCriticalServices(app: ApplicationImpl, asyncScope: CoroutineScope, appRegistered: Job, initLafJob: Job) {
  val pathMacroJob = launch(CoroutineName("PathMacros preloading")) {
    // required for any persistence state component (pathMacroSubstitutor.expandPaths), so, preload
    app.serviceAsync<PathMacros>()
  }

  val managingFsJob = launch {
    // loading is started by StartupUtil, here we just "join" it
    subtask("ManagingFS preloading") { app.serviceAsync<ManagingFS>() }

    // PlatformVirtualFileManager also wants ManagingFS
    launch { app.serviceAsync<VirtualFileManager>() }

    // LocalHistory wants ManagingFS.
    // It should be fixed somehow, but for now, to avoid thread contention, preload it in a controlled manner.
    asyncScope.launch { app.getServiceAsyncIfDefined(LocalHistory::class.java) }
  }

  launch {
    pathMacroJob.join()

    // required for indexing tasks (see JavaSourceModuleNameIndex, for example)
    // FileTypeManager by mistake uses PropertiesComponent instead of own state - it should be fixed someday
    app.serviceAsync<PropertiesComponent>()

    // FileTypeManager requires appStarter execution
    launch {
      appRegistered.join()
      postAppRegistered(app, asyncScope, managingFsJob, initLafJob)
    }

    asyncScope.launch {
      // wants PropertiesComponent
      app.serviceAsync<DebugLogManager>()
    }
  }

  asyncScope.launch {
    launch {
      pathMacroJob.join()
      app.serviceAsync<RegistryManager>()
    }

    if (app.isHeadlessEnvironment) {
      return@launch
    }

    pathMacroJob.join()

    launch {
      // https://youtrack.jetbrains.com/issue/IDEA-321138/Large-font-size-in-2023.2
      initLafJob.join()
      subtask("UISettings preloading") { app.serviceAsync<UISettings>() }
    }
    launch(CoroutineName("CustomActionsSchema preloading")) {
      initLafJob.join()
      app.serviceAsync<CustomActionsSchema>()
    }
    // wants PathMacros
    launch(CoroutineName("GeneralSettings preloading")) { app.serviceAsync<GeneralSettings>() }

    launch {
      app.serviceAsync<PerformanceWatcher>()
    }

    // ActionManager uses KeymapManager
    subtask("KeymapManager preloading") { app.serviceAsync<KeymapManager>() }

    // https://youtrack.jetbrains.com/issue/IDEA-321138/Large-font-size-in-2023.2
    // ActionManager resolves icons
    initLafJob.join()

    subtask("ActionManager preloading") { app.serviceAsync<ActionManager>() }

    // serviceAsync is not supported for light services
    app.service<ScreenReaderStateManager>()
  }
}

private fun CoroutineScope.postAppRegistered(app: ApplicationImpl, asyncScope: CoroutineScope, managingFsJob: Job, initLafJob: Job) {
  launch {
    managingFsJob.join()

    // ProjectJdkTable wants FileTypeManager and VirtualFilePointerManager
    coroutineScope {
      launch {
        initLafJob.join()
        app.serviceAsync<FileTypeManager>()
      }
      // wants ManagingFS
      launch { app.serviceAsync<VirtualFilePointerManager>() }
    }

    app.serviceAsync<ProjectJdkTable>()
  }

  launch(CoroutineName("app service preloading (sync)")) {
    app.preloadServices(modules = PluginManagerCore.getPluginSet().getEnabledModules(),
                        activityPrefix = "",
                        syncScope = this,
                        asyncScope = asyncScope)
  }

  launch {
    val loadComponentInEdtTask = subtask("old component init task creating") {
      app.createInitOldComponentsTask()
    }
    if (loadComponentInEdtTask != null) {
      withContext(RawSwingDispatcher + CoroutineName("old component init")) {
        loadComponentInEdtTask()
      }
    }
    StartUpMeasurer.setCurrentState(LoadingState.COMPONENTS_LOADED)
  }

  if (!app.isHeadlessEnvironment && !app.isUnitTestMode && System.getProperty("enable.activity.preloading", "true").toBoolean()) {
    asyncScope.launch(CoroutineName("preloadingActivity executing")) {
      @Suppress("DEPRECATION")
      val extensionPoint = app.extensionArea.getExtensionPoint<com.intellij.openapi.application.PreloadingActivity>("com.intellij.preloadingActivity")
      @Suppress("DEPRECATION")
      ExtensionPointName<com.intellij.openapi.application.PreloadingActivity>("com.intellij.preloadingActivity").processExtensions { preloadingActivity, pluginDescriptor ->
        launch {
          executePreloadActivity(preloadingActivity, pluginDescriptor)
        }
      }
      extensionPoint.reset()
    }
  }
}

@Suppress("DEPRECATION")
private suspend fun executePreloadActivity(activity: com.intellij.openapi.application.PreloadingActivity, descriptor: PluginDescriptor) {
  val measureActivity = StartUpMeasurer.startActivity(activity.javaClass.name, ActivityCategory.PRELOAD_ACTIVITY, descriptor.pluginId.idString)
  try {
    activity.execute()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    logger<com.intellij.openapi.application.PreloadingActivity>().error(PluginException("cannot execute preloading activity ${activity.javaClass.name}", e, descriptor.pluginId))
  }
  finally {
    measureActivity.end()
  }
}