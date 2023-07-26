// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bootstrap

import com.intellij.diagnostic.*
import com.intellij.icons.AllIcons
import com.intellij.ide.ApplicationLoadListener
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.ui.IconMapLoader
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.idea.AppMode
import com.intellij.idea.AppStarter
import com.intellij.idea.CommandLineArgs
import com.intellij.idea.prepareShowEuaIfNeededTask
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.AsyncProcessIcon
import com.jetbrains.JBR
import kotlinx.coroutines.*

internal fun CoroutineScope.loadApp(app: ApplicationImpl,
                                    pluginSetDeferred: Deferred<Deferred<PluginSet>>,
                                    euaDocumentDeferred: Deferred<EndUserAgreement.Document?>,
                                    asyncScope: CoroutineScope,
                                    initLafJob: Job,
                                    logDeferred: Deferred<Logger>,
                                    appRegisteredJob: CompletableDeferred<Unit>,
                                    args: List<String>,
                                    telemetryInitJob: Job) {
  val initServiceContainerJob = launch {
    initServiceContainer(app = app, pluginSetDeferred = pluginSetDeferred)

    subtask("telemetry waiting") {
      telemetryInitJob.join()
    }
  }

  val euaTaskDeferred: Deferred<(suspend () -> Boolean)?>? = if (AppMode.isHeadless()) {
    null
  }
  else {
    async(CoroutineName("eua document")) {
      prepareShowEuaIfNeededTask(document = euaDocumentDeferred.await(), asyncScope = asyncScope)
    }
  }

  val initConfigurationStoreJob = launch {
    initServiceContainerJob.join()
    initConfigurationStore(app)
  }

  val preloadCriticalServicesJob = async(CoroutineName("app pre-initialization")) {
    initConfigurationStoreJob.join()
    preInitApp(app = app,
               asyncScope = asyncScope,
               initLafJob = initLafJob,
               log = logDeferred.await(),
               appRegisteredJob = appRegisteredJob,
               euaTaskDeferred = euaTaskDeferred)
    LoadingState.setCurrentState(LoadingState.COMPONENTS_LOADED)
  }

  launch {
    initServiceContainerJob.join()

    val appInitListeners = async(CoroutineName("app init listener preload")) {
      getAppInitializedListeners(app)
    }

    // only here as the last - it is a heavy-weight (~350ms) activity, let's first schedule more important tasks
    if (System.getProperty("idea.enable.coroutine.dump", "true").toBoolean()) {
      launch(CoroutineName("coroutine debug probes init")) {
        enableCoroutineDump()
        JBR.getJstack()?.includeInfoFrom {
          """
      $COROUTINE_DUMP_HEADER
      ${dumpCoroutines(stripDump = false)}
      """
        }
      }
    }

    initConfigurationStoreJob.join()
    appRegisteredJob.join()

    initApplicationImpl(args = args.filterNot { CommandLineArgs.isKnownArgument(it) },
                        appInitListeners = appInitListeners,
                        app = app,
                        preloadCriticalServicesJob = preloadCriticalServicesJob,
                        asyncScope = asyncScope)
  }
}

private suspend fun initServiceContainer(app: ApplicationImpl, pluginSetDeferred: Deferred<Deferred<PluginSet>>) {
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

private suspend fun preInitApp(app: ApplicationImpl,
                                asyncScope: CoroutineScope,
                                log: Logger,
                                initLafJob: Job,
                                appRegisteredJob: Job,
                                euaTaskDeferred: Deferred<(suspend () -> Boolean)?>?) {
  coroutineScope {
    launch(CoroutineName("critical services preloading")) {
      preloadCriticalServices(app = app, asyncScope = asyncScope, appRegistered = appRegisteredJob, initLafJob = initLafJob)
    }

    val loadIconMapping = if (app.isHeadlessEnvironment) {
      null
    }
    else {
      launch(CoroutineName("icon mapping loading")) {
        runCatching {
          app.serviceAsync<IconMapLoader>().preloadIconMapping()
        }.getOrLogException(log)
      }
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
      if (!app.isHeadlessEnvironment) {
        launch(CoroutineName("icons preloading") + Dispatchers.IO) {
          AsyncProcessIcon.createBig(this)
          AsyncProcessIcon(this)
          AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
          AnimatedIcon.FS()
        }
      }

      coroutineScope {
        loadIconMapping?.join()
        launch {
          // used by LafManager
          app.serviceAsync<UISettings>()
        }
        launch(CoroutineName("UiThemeProviderListManager preloading")) {
          app.serviceAsync<UiThemeProviderListManager>()
        }
      }

      subtask("laf initialization", RawSwingDispatcher) {
        app.serviceAsync<LafManager>()
      }
      if (!app.isHeadlessEnvironment) {
        // preload only when LafManager is ready
        launch {
          app.serviceAsync<EditorColorsManager>()
        }
      }
    }
  }
}

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
    LoadingState.setCurrentState(LoadingState.CONFIGURATION_STORE_INITIALIZED)
  }
}
