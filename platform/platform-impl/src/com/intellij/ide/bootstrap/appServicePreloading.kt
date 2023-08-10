// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bootstrap

import com.intellij.diagnostic.DebugLogManager
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.PluginException
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
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.util.childScope
import com.intellij.util.indexing.FileBasedIndex
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException

fun CoroutineScope.preloadCriticalServices(app: ApplicationImpl,
                                           asyncScope: CoroutineScope,
                                           appRegistered: Job,
                                           initLafJob: Job,
                                           initAwtToolkitAndEventQueueJob: Job?) {
  val pathMacroJob = launch(CoroutineName("PathMacros preloading")) {
    // required for any persistence state component (pathMacroSubstitutor.expandPaths), so, preload
    app.serviceAsync<PathMacros>()
  }

  val managingFsJob = asyncScope.launch {
    // loading is started by StartupUtil, here we just "join" it
    span("ManagingFS preloading") {
      app.serviceAsync<ManagingFS>()
      // cache it to field
      ManagingFS.getInstance()
    }

    // PlatformVirtualFileManager also wants ManagingFS
    launch(CoroutineName("VirtualFileManager preloading")) { app.serviceAsync<VirtualFileManager>() }

    // LocalHistory wants ManagingFS.
    // It should be fixed somehow, but for now, to avoid thread contention, preload it in a controlled manner.
    asyncScope.launch(CoroutineName("LocalHistory preloading")) { app.getServiceAsyncIfDefined(LocalHistory::class.java) }
  }

  launch {
    // required for indexing tasks (see JavaSourceModuleNameIndex, for example)
    // FileTypeManager by mistake uses PropertiesComponent instead of own state - it should be fixed someday
    app.serviceAsync<PropertiesComponent>()

    pathMacroJob.join()

    // FileTypeManager requires appStarter execution
    launch {
      appRegistered.join()
      postAppRegistered(app = app,
                        asyncScope = asyncScope,
                        managingFsJob = managingFsJob,
                        initAwtToolkitAndEventQueueJob = initAwtToolkitAndEventQueueJob)
    }

    asyncScope.launch {
      // wants PropertiesComponent
      app.serviceAsync<DebugLogManager>()
    }
  }

  asyncScope.launch {
    launch {
      app.serviceAsync<RegistryManager>()
    }

    pathMacroJob.join()

    if (app.isHeadlessEnvironment) {
      return@launch
    }

    launch {
      // https://youtrack.jetbrains.com/issue/IDEA-321138/Large-font-size-in-2023.2
      initLafJob.join()
      span("UISettings preloading") { app.serviceAsync<UISettings>() }
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
    span("KeymapManager preloading") { app.serviceAsync<KeymapManager>() }
    span("ActionManager preloading") { app.serviceAsync<ActionManager>() }

    app.serviceAsync<ScreenReaderStateManager>()
  }
}

private fun CoroutineScope.postAppRegistered(app: ApplicationImpl,
                                             asyncScope: CoroutineScope,
                                             managingFsJob: Job,
                                             initAwtToolkitAndEventQueueJob: Job?) {
  asyncScope.launch {
    val fileTypeManagerJob = launch(CoroutineName("FileTypeManager preloading")) {
      app.serviceAsync<FileTypeManager>()
    }

    managingFsJob.join()

    launch {
      fileTypeManagerJob.join()
      val fileBasedIndex = span("FileBasedIndex preloading") {
        app.serviceAsync<FileBasedIndex>()
      }
      span("FileBasedIndex.loadIndexes") {
        fileBasedIndex.loadIndexes()
      }
    }

    launch {
      // ProjectJdkTable wants FileTypeManager and VirtualFilePointerManager
      fileTypeManagerJob.join()
      // wants ManagingFS
      span("VirtualFilePointerManager preloading") {
        app.serviceAsync<VirtualFilePointerManager>()
      }
      span("ProjectJdkTable preloading") {
        app.serviceAsync<ProjectJdkTable>()
      }
    }
  }

  launch {
    if (initAwtToolkitAndEventQueueJob != null) {
      span("waiting for rw lock for app instantiation") {
        initAwtToolkitAndEventQueueJob.join()
      }

      ApplicationImpl.postInit(app)
    }

    launch(CoroutineName("app service preloading (sync)")) {
      app.preloadServices(modules = PluginManagerCore.getPluginSet().getEnabledModules(),
                          activityPrefix = "",
                          syncScope = this,
                          asyncScope = app.coroutineScope.childScope(supervisor = false))
    }
  }

  launch {
    val loadComponentInEdtTask = span("old component init task creating") {
      app.createInitOldComponentsTask()
    }
    if (loadComponentInEdtTask != null) {
      withContext(RawSwingDispatcher + CoroutineName("old component init")) {
        loadComponentInEdtTask()
      }
    }
  }

  if (!app.isHeadlessEnvironment && !app.isUnitTestMode && System.getProperty("enable.activity.preloading", "true").toBoolean()) {
    asyncScope.launch(CoroutineName("preloadingActivity executing")) {
      @Suppress("DEPRECATION")
      val extensionPoint = app.extensionArea.getExtensionPoint<com.intellij.openapi.application.PreloadingActivity>("com.intellij.preloadingActivity")
      @Suppress("DEPRECATION")
      ExtensionPointName<com.intellij.openapi.application.PreloadingActivity>("com.intellij.preloadingActivity").processExtensions { activity, pluginDescriptor ->
        launch(CoroutineName(activity.javaClass.name)) {
          executePreloadActivity(activity, pluginDescriptor)
        }
      }
      extensionPoint.reset()
    }
  }
}

@Suppress("DEPRECATION")
private suspend fun executePreloadActivity(activity: com.intellij.openapi.application.PreloadingActivity, descriptor: PluginDescriptor) {
  try {
    activity.execute()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    logger<com.intellij.openapi.application.PreloadingActivity>()
      .error(PluginException("cannot execute preloading activity ${activity.javaClass.name}", e, descriptor.pluginId))
  }
}