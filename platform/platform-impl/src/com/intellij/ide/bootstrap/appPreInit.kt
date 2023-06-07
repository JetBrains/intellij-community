// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bootstrap

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.subtask
import com.intellij.ide.ApplicationLoadListener
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.ui.IconMapLoader
import com.intellij.ide.ui.LafManager
import com.intellij.idea.AppStarter
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting

internal suspend fun preInitApp(app: ApplicationImpl,
                                asyncScope: CoroutineScope,
                                log: Logger,
                                initLafJob: Job,
                                appRegistered: Job,
                                pluginSetDeferred: CompletableDeferred<Deferred<PluginSet>>,
                                euaTaskDeferred: Deferred<(suspend () -> Boolean)?>?): Job {
  val pluginSet = subtask("plugin descriptor init waiting") {
    pluginSetDeferred.await().await()
  }

  subtask("app component registration") {
    app.registerComponents(modules = pluginSet.getEnabledModules(),
                           app = app,
                           precomputedExtensionModel = null,
                           listenerCallbacks = null)
  }

  val loadIconMapping = if (app.isHeadlessEnvironment) {
    null
  }
  else {
    asyncScope.launch(CoroutineName("icon mapping loading")) {
      runCatching {
        app.service<IconMapLoader>().preloadIconMapping()
      }.getOrLogException(log)
    }
  }

  initConfigurationStore(app)

  val preloadCriticalServicesJob = asyncScope.launch(CoroutineName("critical services preloading")) {
    preloadCriticalServices(app = app, asyncScope = asyncScope, appRegistered = appRegistered)
  }

  if (!app.isHeadlessEnvironment) {
    asyncScope.launch(CoroutineName("FUS class preloading")) {
      // preload FUS classes (IDEA-301206)
      ActionsEventLogGroup.GROUP.id
    }
  }

  // LaF must be initialized before app init because icons maybe requested and, as a result,
  // a scale must be already initialized (especially important for Linux)
  subtask("init laf waiting") {
    initLafJob.join()
  }

  euaTaskDeferred?.await()?.invoke()

  asyncScope.launch {
    loadIconMapping?.join()
    subtask("laf initialization", RawSwingDispatcher) {
      app.serviceAsync<LafManager>()
    }
    if (!app.isHeadlessEnvironment) {
      // preload only when LafManager is ready
      app.serviceAsync<EditorColorsManager>()
    }
  }

  return preloadCriticalServicesJob
}

@VisibleForTesting
suspend fun initConfigurationStore(app: ApplicationImpl) {
  val configPath = PathManager.getConfigDir()

  subtask("beforeApplicationLoaded") {
    for (listener in ApplicationLoadListener.EP_NAME.lazySequence()) {
      launch {
        runCatching {
          listener.beforeApplicationLoaded(app, configPath)
        }.getOrLogException(logger<AppStarter>())
      }
    }
  }

  subtask("init app store") {
    // we set it after beforeApplicationLoaded call, because the app store can depend on a stream provider state
    app.stateStore.setPath(configPath)
    StartUpMeasurer.setCurrentState(LoadingState.CONFIGURATION_STORE_INITIALIZED)
  }
}
