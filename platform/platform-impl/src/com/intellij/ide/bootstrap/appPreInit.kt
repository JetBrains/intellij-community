// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bootstrap

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.subtask
import com.intellij.icons.AllIcons
import com.intellij.ide.ApplicationLoadListener
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.ui.IconMapLoader
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.idea.AppMode
import com.intellij.idea.AppStarter
import com.intellij.idea.prepareShowEuaIfNeededTask
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
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting

internal suspend fun initServiceContainer(app: ApplicationImpl, pluginSetDeferred: Deferred<Deferred<PluginSet>>) {
  val pluginSet = subtask("plugin descriptor init waiting") {
    pluginSetDeferred.await().await()
  }

  subtask("app component registration") {
    app.registerComponents(modules = pluginSet.getEnabledModules(),
                           app = app,
                           precomputedExtensionModel = null,
                           listenerCallbacks = null)
  }
}

internal suspend fun preInitApp(app: ApplicationImpl,
                                asyncScope: CoroutineScope,
                                log: Logger,
                                initServiceContainerJob: Job,
                                initLafJob: Job,
                                appRegisteredJob: Job,
                                telemetryInitJob: Job,
                                euaDocumentDeferred: Deferred<EndUserAgreement.Document?>) {
  coroutineScope {
    val euaTaskDeferred: Deferred<(suspend () -> Boolean)?>? = if (AppMode.isHeadless()) {
      null
    }
    else {
      async(CoroutineName("eua document")) {
        prepareShowEuaIfNeededTask(document = euaDocumentDeferred.await(), asyncScope = asyncScope)
      }
    }

    launch(CoroutineName("telemetry waiting")) {
      try {
        telemetryInitJob.join()
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        log.error("Can't initialize OpenTelemetry: will use default (noop) SDK impl", e)
      }
    }

    if (app.isInternal && !app.isHeadlessEnvironment) {
      launch {
        IconLoader.setStrictGlobally(true)
      }
    }

    initServiceContainerJob.join()

    val loadIconMapping = if (app.isHeadlessEnvironment) {
      null
    }
    else {
      launch(CoroutineName("icon mapping loading")) {
        runCatching {
          app.service<IconMapLoader>().preloadIconMapping()
        }.getOrLogException(log)
      }
    }

    initConfigurationStore(app)

    launch(CoroutineName("critical services preloading")) {
      preloadCriticalServices(app = app, asyncScope = asyncScope, appRegistered = appRegisteredJob, initLafJob = initLafJob)
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
      // preloaded as a part of preloadCriticalServices, used by LafManager
      app.serviceAsync<UISettings>()
      subtask("laf initialization", RawSwingDispatcher) {
        app.serviceAsync<LafManager>()
      }
      if (!app.isHeadlessEnvironment) {
        // preload only when LafManager is ready
        app.serviceAsync<EditorColorsManager>()

        launch(CoroutineName("icons preloading") + Dispatchers.IO) {
          AsyncProcessIcon("")
          AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
          AnimatedIcon.FS()
        }
      }
    }
  }
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
