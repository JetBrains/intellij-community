// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.history.LocalHistory
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.indexing.FileBasedIndex
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

fun CoroutineScope.preloadCriticalServices(
  app: ApplicationImpl,
  asyncScope: CoroutineScope,
  appRegistered: Job,
  initAwtToolkitAndEventQueueJob: Job?,
): Job {
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

    val registryManagerJob = asyncScope.launch {
      app.serviceAsync<RegistryManager>()
    }

    // FileTypeManager requires appStarter execution
    launch {
      appRegistered.join()
      postAppRegistered(app = app,
                        asyncScope = asyncScope,
                        managingFsJob = managingFsJob,
                        registryManagerJob = registryManagerJob,
                        initAwtToolkitAndEventQueueJob = initAwtToolkitAndEventQueueJob)
    }
  }

  return pathMacroJob
}

private fun CoroutineScope.postAppRegistered(app: ApplicationImpl,
                                             asyncScope: CoroutineScope,
                                             managingFsJob: Job,
                                             registryManagerJob: Job,
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
      // RegistryManager is needed for ProjectJdkTable
      registryManagerJob.join()
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

      // wants app info as service
      launch {
        app.serviceAsync<PerformanceWatcher>()
      }
    }

    launch(CoroutineName("app service preloading (sync)")) {
      app.preloadServices(modules = PluginManagerCore.getPluginSet().getEnabledModules(),
                          activityPrefix = "",
                          syncScope = this,
                          asyncScope = app.getCoroutineScope().childScope(supervisor = false))
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
}