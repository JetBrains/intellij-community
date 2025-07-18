// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.accessibility.enableScreenReaderSupportIfNeeded
import com.intellij.diagnostic.EdtLockLoadMonitorService
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.*
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.ide.ui.IconDbMaintainer
import com.intellij.internal.inspector.UiInspectorUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.migrateRegistryToAdvSettings
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ex.NoProjectStateHandler
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.ui.updateAppWindowIcon
import com.intellij.util.io.URLUtil.SCHEME_SEPARATOR
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import javax.swing.JOptionPane

open class IdeStarter : ModernApplicationStarter() {
  companion object {
    private var filesToLoad: List<Path> = Collections.emptyList()
    private var uriToOpen: String? = null

    fun openFilesOnLoading(value: List<Path>) {
      filesToLoad = value
    }

    fun openUriOnLoading(value: String) {
      uriToOpen = value
    }
  }

  override val isHeadless: Boolean
    get() = false

  @OptIn(IntellijInternalApi::class)
  override suspend fun start(args: List<String>) {
    coroutineScope {
      if (ApplicationManager.getApplication().isInternal) {
        launch {
          if (serviceAsync<RegistryManager>().`is`("ui.inspector.save.stacktraces")) {
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              UiInspectorUtil.initStacktraceSaving()
            }
          }
        }
      }

      val app = ApplicationManager.getApplication()
      val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)

      val openProjectBlock: suspend CoroutineScope.() -> Unit = {
        openProjectIfNeeded(args = args, app = app, coroutineScope = this, publisher = lifecyclePublisher)
        // update an "open projects" state to whichever projects were decided to be opened in openProjectIfNeeded (=which are open now)
        serviceAsync<RecentProjectsManager>().updateLastProjectPath()
      }

      val starter = FUSProjectHotStartUpMeasurer.getStartUpContextElementIntoIdeStarter(close = isHeadless || !shouldRunFusStartUpMeasurer())
      if (starter != null) {
        if ((app as ApplicationEx).isLightEditMode) {
          FUSProjectHotStartUpMeasurer.lightEditProjectFound()
        }
        withContext(starter, openProjectBlock)
      }
      else {
        openProjectBlock()
      }

      app.serviceAsync<PerformanceWatcher>()
      // cache it as IdeEventQueue should use loaded PerformanceWatcher service as soon as it is ready (getInstanceIfCreated is used)
      PerformanceWatcher.getInstance().startEdtSampling()

      if (Registry.`is`("ide.enable.edt.lock.load.monitor")) {
        app.serviceAsync<EdtLockLoadMonitorService>().initialize()
      }

      launch { reportPluginErrors() }

      LoadingState.setCurrentStateIfAtLeast(LoadingState.COMPONENTS_LOADED, LoadingState.APP_STARTED)
      runCatching {
        lifecyclePublisher.appStarted()
      }.getOrLogException(thisLogger())

      if (!app.isHeadlessEnvironment) {
        postOpenUiTasks(scope = this)
      }
    }
  }

  protected open suspend fun openProjectIfNeeded(
    args: List<String>,
    app: Application,
    coroutineScope: CoroutineScope,
    publisher: AppLifecycleListener,
  ) {
    var willReopenRecentProjectOnStart = false
    lateinit var recentProjectManager: RecentProjectsManager
    val isOpenProjectNeeded = span("isOpenProjectNeeded") {
      span("app frame created callback") {
        runCatching {
          publisher.appFrameCreated(args)
        }.getOrLogException(thisLogger())
      }

      // must be after `AppLifecycleListener#appFrameCreated`, because some listeners can mutate the state of `RecentProjectsManager`
      if (app.isHeadlessEnvironment) {
        LifecycleUsageTriggerCollector.onIdeStart()
        return@span false
      }

      coroutineScope.launch {
        LifecycleUsageTriggerCollector.onIdeStart()
      }

      if (uriToOpen != null || args.isNotEmpty() && args.first().contains(SCHEME_SEPARATOR)) {
        FUSProjectHotStartUpMeasurer.reportUriOpening()
        processUriParameter(uri = uriToOpen ?: args.first(), lifecyclePublisher = publisher)
        return@span false
      }

      recentProjectManager = serviceAsync<RecentProjectsManager>()
      willReopenRecentProjectOnStart = recentProjectManager.willReopenProjectOnStart()
      val willOpenProject = willReopenRecentProjectOnStart || !args.isEmpty() || !filesToLoad.isEmpty()
      if (willOpenProject) {
        return@span true
      }
      val customHandler = NoProjectStateHandler.EP_NAME.lazySequence().firstOrNull { it.canHandle() }
      if (customHandler != null) {
        customHandler.handle()
        return@span false
      }
      return@span showWelcomeFrame(publisher)
    }

    if (!isOpenProjectNeeded) {
      return
    }

    val project = when {
      filesToLoad.isNotEmpty() -> {
        FUSProjectHotStartUpMeasurer.reportProjectType(FUSProjectHotStartUpMeasurer.ProjectsType.FromFilesToLoad)
        ProjectUtil.openOrImportFilesAsync(filesToLoad, "IdeStarter")
      }
      args.isNotEmpty() -> {
        FUSProjectHotStartUpMeasurer.reportProjectType(FUSProjectHotStartUpMeasurer.ProjectsType.FromArgs)
        loadProjectFromExternalCommandLine(args)
      }
      else -> null
    }

    if (project != null) {
      return
    }

    val isOpened = willReopenRecentProjectOnStart && try {
      span("reopenLastProjectsOnStart") {
        recentProjectManager.reopenLastProjectsOnStart()
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      logger<IdeStarter>().error("Cannot reopen recent projects", e)
      false
    }

    if (!isOpened) {
      WelcomeFrame.showIfNoProjectOpened(publisher)
    }
  }

  @ApiStatus.Internal
  protected open fun shouldRunFusStartUpMeasurer(): Boolean = javaClass == IdeStarter::class.java

  private suspend fun showWelcomeFrame(lifecyclePublisher: AppLifecycleListener): Boolean {
    val showWelcomeFrameTask = WelcomeFrame.prepareToShow() ?: return true
    serviceAsync<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      // https://youtrack.jetbrains.com/issue/IJPL-522
      launch {
        serviceAsync<ActionManager>()
      }

      withContext(Dispatchers.EDT) {
        showWelcomeFrameTask()
        runCatching {
          lifecyclePublisher.welcomeScreenDisplayed()
        }.getOrLogException(thisLogger())
      }
    }
    return false
  }

  private suspend fun processUriParameter(uri: String, lifecyclePublisher: AppLifecycleListener) {
    val result = CommandLineProcessor.processProtocolCommand(uri)
    if (result.exitCode == ProtocolHandler.PLEASE_QUIT) {
      withContext(Dispatchers.EDT) {
        ApplicationManagerEx.getApplicationEx().exit(false, true)
      }
    }
    else if (result.exitCode != ProtocolHandler.PLEASE_NO_UI) {
      WelcomeFrame.showIfNoProjectOpened(lifecyclePublisher)
    }
  }

  internal class StandaloneLightEditStarter : IdeStarter() {
    override suspend fun openProjectIfNeeded(
      args: List<String>,
      app: Application,
      coroutineScope: CoroutineScope,
      publisher: AppLifecycleListener,
    ) {
      val project = when {
        filesToLoad.isNotEmpty() -> ProjectUtil.openOrImportFilesAsync(list = filesToLoad, location = "MacMenu")
        args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
        else -> null
      }

      if (project != null) {
        return
      }

      val recentProjectManager = serviceAsync<RecentProjectsManager>()
      val isOpened = (if (recentProjectManager.willReopenProjectOnStart()) recentProjectManager.reopenLastProjectsOnStart() else true)
      if (!isOpened) {
        coroutineScope.launch(Dispatchers.EDT) {
          serviceAsync<LightEditService>().showEditorWindow()
        }
      }
    }

    override fun shouldRunFusStartUpMeasurer(): Boolean {
      return javaClass == StandaloneLightEditStarter::class.java
    }
  }
}

private suspend fun loadProjectFromExternalCommandLine(commandLineArgs: List<String>): Project? {
  val result = CommandLineProcessor.processExternalCommandLine(commandLineArgs, currentDirectory = null)
  if (result.hasError) {
    withContext(Dispatchers.EDT) {
      if (!ApplicationManagerEx.isInIntegrationTest() ||
          !java.lang.Boolean.parseBoolean(System.getProperty("closeIDESilentlyOnStartupErrorInTests"))) {
        result.showError()
      }
      ApplicationManager.getApplication().exit(true, true, false)
    }
  }
  return result.project
}

private fun postOpenUiTasks(scope: CoroutineScope) {
  if (PluginManagerCore.isRunningFromSources()) {
    updateAppWindowIcon(JOptionPane.getRootFrame())
  }

  if (SystemInfoRt.isMac) {
    scope.launch(CoroutineName("mac touchbar on app init")) {
      TouchbarSupport.onApplicationLoaded()
    }
  }
  else if (SystemInfoRt.isUnix && SystemInfo.isJetBrainsJvm) {
    scope.launch(CoroutineName("input method disabling on Linux")) {
      disableInputMethodsIfPossible()
    }
  }

  scope.launch {
    migrateRegistryToAdvSettings()
  }

  scope.launch {
    SystemHealthMonitor.start()
  }

  scope.launch {
    FUSProjectHotStartUpMeasurer.startWritingStatistics()
  }

  scope.launch {
    serviceAsync<IconDbMaintainer>()
  }

  scope.launch {
    enableScreenReaderSupportIfNeeded()
  }
}

private suspend fun reportPluginErrors() {
  val pluginErrors = PluginManagerCore.getAndClearPluginLoadingErrors()
  if (pluginErrors.isEmpty()) {
    return
  }

  withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
    val title = IdeBundle.message("title.plugin.error")
    val pluginErrorMessages = pluginErrors.map { it.get() }.toMutableList()
    val actions = linksToActions(pluginErrorMessages)
    val content = HtmlBuilder().appendWithSeparators(HtmlChunk.p(), pluginErrorMessages).toString()
    @Suppress("DEPRECATION")
    serviceAsync<NotificationGroupManager>().getNotificationGroup(
      "Plugin Error").createNotification(title, content, NotificationType.ERROR)
      .setListener { notification, event ->
        notification.expire()
        PluginManagerMain.onEvent(event.description)
      }
      .addActions(actions)
      .notify(null)
  }
}

private fun linksToActions(errors: MutableList<HtmlChunk>): Collection<AnAction> {
  val link = "<a href=\""
  val actions = ArrayList<AnAction>()

  while (!errors.isEmpty()) {
    val builder = StringBuilder()
    errors[errors.lastIndex].appendTo(builder)
    val error = builder.toString()

    if (error.startsWith(link)) {
      val descriptionEnd = error.indexOf('"', link.length)
      val description = error.substring(link.length, descriptionEnd)
      @Suppress("HardCodedStringLiteral")
      val text = error.substring(descriptionEnd + 2, error.lastIndexOf("</a>"))
      errors.removeAt(errors.lastIndex)

      actions.add(NotificationAction.createSimpleExpiring(text) {
        PluginManagerMain.onEvent(description)
      })
    }
    else {
      break
    }
  }

  return actions
}
