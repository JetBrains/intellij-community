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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.TelemetryManagerImpl
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.AsyncProcessIcon
import com.jetbrains.JBR
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException

internal fun CoroutineScope.loadApp(app: ApplicationImpl,
                                    pluginSetDeferred: Deferred<Deferred<PluginSet>>,
                                    euaDocumentDeferred: Deferred<EndUserAgreement.Document?>,
                                    asyncScope: CoroutineScope,
                                    initLafJob: Job,
                                    logDeferred: Deferred<Logger>,
                                    appRegisteredJob: CompletableDeferred<Unit>,
                                    args: List<String>,
                                    initAwtToolkitAndEventQueueJob: Job) {
  val initServiceContainerJob = launch {
    initServiceContainer(app = app, pluginSetDeferred = pluginSetDeferred)
    // ApplicationManager.getApplication may be used in ApplicationInitializedListener constructor
    ApplicationManager.setApplication(app)
  }

  val initTelemetryJob = launch(CoroutineName("opentelemetry configuration")) {
    initServiceContainerJob.join()
    try {
      TelemetryManager.setTelemetryManager(TelemetryManagerImpl(app))
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      logDeferred.await().error("Can't initialize OpenTelemetry: will use default (noop) SDK impl", e)
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

  val initConfigurationStoreAndSetRwLockJob = launch {
    initServiceContainerJob.join()

    ApplicationImpl.postInit(app)
    initConfigurationStore(app)

    span("waiting for rw lock for app instantiation") {
      initAwtToolkitAndEventQueueJob.join()
    }
  }

  val preloadCriticalServicesJob = async(CoroutineName("app pre-initialization")) {
    initConfigurationStoreAndSetRwLockJob.join()
    span("telemetry waiting") {
      initTelemetryJob.join()
    }
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

    initConfigurationStoreAndSetRwLockJob.join()
    appRegisteredJob.join()

    initApplicationImpl(args = args.filterNot { CommandLineArgs.isKnownArgument(it) },
                        appInitListeners = appInitListeners,
                        app = app,
                        preloadCriticalServicesJob = preloadCriticalServicesJob,
                        asyncScope = asyncScope)
  }
}

private suspend fun initServiceContainer(app: ApplicationImpl, pluginSetDeferred: Deferred<Deferred<PluginSet>>) {
  val pluginSet = span("plugin descriptor init waiting") {
    pluginSetDeferred.await().await()
  }

  span("app component registration") {
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
    span("init laf waiting") {
      initLafJob.join()
    }

    euaTaskDeferred?.await()?.invoke()

    coroutineScope {
      launch {
        // used by LafManager
        app.serviceAsync<UISettings>()
      }
      launch(CoroutineName("UiThemeProviderListManager preloading")) {
        app.serviceAsync<UiThemeProviderListManager>()
      }
    }

    loadIconMapping?.join()

    if (!app.isHeadlessEnvironment) {
      asyncScope.launch(CoroutineName("icons preloading") + Dispatchers.IO) {
        AsyncProcessIcon.createBig(this)
        AsyncProcessIcon(this)
        AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
        AnimatedIcon.FS()
      }
    }

    span("laf initialization", RawSwingDispatcher) {
      app.serviceAsync<LafManager>()
    }

    if (!app.isHeadlessEnvironment) {
      // preload only when LafManager is ready
      asyncScope.launch {
        app.serviceAsync<EditorColorsManager>()
      }
    }
  }
}

suspend fun initConfigurationStore(app: ApplicationImpl) {
  val configPath = PathManager.getConfigDir()

  span("beforeApplicationLoaded") {
    for (listener in ApplicationLoadListener.EP_NAME.lazySequence()) {
      launch {
        runCatching {
          listener.beforeApplicationLoaded(app, configPath)
        }.getOrLogException(logger<AppStarter>())
      }
    }
  }

  span("init app store") {
    // we set it after beforeApplicationLoaded call, because the app store can depend on a stream provider state
    app.stateStore.setPath(configPath)
    LoadingState.setCurrentState(LoadingState.CONFIGURATION_STORE_INITIALIZED)
  }
}
