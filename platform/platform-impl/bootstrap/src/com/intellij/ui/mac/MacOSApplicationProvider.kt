// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.CommandLineProcessor
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.AboutAction
import com.intellij.ide.actions.ActionsCollector
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.idea.IdeStarter.Companion.openFilesOnLoading
import com.intellij.idea.IdeStarter.Companion.openUriOnLoading
import com.intellij.jna.JnaLoader
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import com.sun.jna.Callback
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Desktop
import java.awt.event.MouseEvent
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

private val LOG: Logger
  get() = logger<Application>()

private val ENABLED = AtomicBoolean(true)

@Suppress("unused")
private var UPDATE_CALLBACK_REF: Any? = null

@Internal
fun initMacApplication(mainScope: CoroutineScope) {
  val desktop = Desktop.getDesktop()
  desktop.setAboutHandler {
    submit("About", mainScope) { ideFocusManager ->
      val project = withContext(Dispatchers.UiWithModelAccess) {
        val project = (ideFocusManager.lastFocusedIdeWindow as? IdeFrame)?.project
        AboutAction.perform(project)
        project
      }

      reportActionUsed(project, "About")
    }
  }
  desktop.setPreferencesHandler {
    submit("Settings", mainScope) { ideFocusManager ->
      val showSettingsUtil = serviceAsync<ShowSettingsUtil>()
      // we should not use RW to get focused frame - pure UI should be enough
      val project = withContext(Dispatchers.ui(CoroutineSupport.UiDispatcherKind.STRICT)) {
        (ideFocusManager.lastFocusedIdeWindow as? IdeFrame)?.project
      }

      if (project == null || project.isDefault) {
        LOG.debug("MacMenu: no opened project frame, use default project instead")
        val defaultProject = project ?: serviceAsync<ProjectManager>().defaultProject
        showSettingsUtil.showSettingsDialog(defaultProject, createConfigurableGroups(defaultProject))
      }
      else {
        // Execute in the project coroutine scope to ensure that,
        // if project opening is canceled or the project is closed, we cancel settings opening.
        // Still, we `.join` to ensure that mac menu actions is disabled for the entire duration of the task (contract of `submit`).
        project.serviceAsync<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
          (project.serviceAsync<StartupManager>() as StartupManagerEx).waitForInitProjectActivities(IdeBundle.message("settings.modal.opening.message"))
          showSettingsUtil.showSettingsDialog(project, createConfigurableGroups(project))
        }.join()
      }

      reportActionUsed(project, "ShowSettings")
    }
  }
  desktop.setQuitHandler { _, response ->
    if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
      response.performQuit()
      return@setQuitHandler
    }

    submit("Quit", mainScope) {
      // Dispatchers.EDT implies WIL — we should rework app exit and add a new suspend API
      withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().exit()
      }
    }
    response.cancelQuit()
  }
  desktop.setOpenFileHandler { event ->
    val files = event.files
    if (files.isEmpty()) {
      return@setOpenFileHandler
    }

    val list = files.map(File::toPath)
    if (LoadingState.COMPONENTS_LOADED.isOccurred) {
      val project = getNonDefaultProject()
      mainScope.launch {
        ProjectUtil.openOrImportFilesAsync(list = list, location = "MacMenu", projectToClose = project)
      }
    }
    else {
      openFilesOnLoading(list)
    }
    desktop.requestForeground(true)
  }
  if (JnaLoader.isLoaded()) {
    Foundation.executeOnMainThread(false, false, Runnable { installAutoUpdateMenu() })
    installProtocolHandler(desktop, mainScope)
  }
}

private fun createConfigurableGroups(project: Project): List<ConfigurableGroup> {
  return listOf(ConfigurableExtensionPointUtil.doGetConfigurableGroup(project, true))
}

private suspend fun reportActionUsed(project: Project?, actionId: String) {
  serviceAsync<ActionsCollector>().record(project, serviceAsync<ActionManager>().getAction(actionId), null, null)
}

private fun installAutoUpdateMenu() {
  val pool = Foundation.invoke("NSAutoreleasePool", "new")
  val app = Foundation.invoke("NSApplication", "sharedApplication")
  val menu = Foundation.invoke(app, Foundation.createSelector("menu"))
  val item = Foundation.invoke(menu, Foundation.createSelector("itemAtIndex:"), 0)
  val appMenu = Foundation.invoke(item, Foundation.createSelector("submenu"))
  val checkForUpdateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSMenuItem"), "NSCheckForUpdates")
  val impl = object : Callback {
    @Suppress("unused", "UNUSED_PARAMETER")
    fun callback(self: ID?, selector: String?) {
      SwingUtilities.invokeLater {
        val mouseEvent = MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false)
        val actionManager = ApplicationManager.getApplication()?.getServiceIfCreated(ActionManager::class.java) ?: return@invokeLater
        actionManager.tryToExecute(actionManager.getAction("CheckForUpdate"), mouseEvent, null, null, false)
      }
    }
  }
  // prevents the callback from being collected
  UPDATE_CALLBACK_REF = impl
  Foundation.addMethod(checkForUpdateClass, Foundation.createSelector("checkForUpdates"), impl, "v")
  Foundation.registerObjcClassPair(checkForUpdateClass)
  val checkForUpdates = Foundation.invoke("NSCheckForUpdates", "alloc")
  Foundation.invoke(checkForUpdates, Foundation.createSelector("initWithTitle:action:keyEquivalent:"),
                    Foundation.nsString("Check for Updates..."), Foundation.createSelector("checkForUpdates"), Foundation.nsString(""))
  Foundation.invoke(checkForUpdates, Foundation.createSelector("setTarget:"), checkForUpdates)
  Foundation.invoke(appMenu, Foundation.createSelector("insertItem:atIndex:"), checkForUpdates, 1)
  Foundation.invoke(checkForUpdates, Foundation.createSelector("release"))
  Foundation.invoke(pool, Foundation.createSelector("release"))
}

@RequiresEdt
private fun getNonDefaultProject(): Project? {
  @Suppress("DEPRECATION")
  var project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().dataContext)
  if (project == null) {
    LOG.debug("MacMenu: no project in data context")
    project = ProjectManager.getInstanceIfCreated()?.openProjects?.firstOrNull()
  }
  LOG.debug { "MacMenu: project = $project" }
  return project
}

// The `java.awt.Desktop` API invokes the handler on an AWT thread.
// However, UI actions must be executed on the EDT — and nowadays, we have multiple "EDT-like" contexts,
// most notably the write-intent-lock context.
//
// Therefore, the most reliable and future-proof approach is to reschedule execution to the main coroutine scope
// (i.e., the scope from the root `runBlocking` of the application) in a suspend-aware context,
// and then use the appropriate API to perform UI operations.
//
// Yes, this adds a slight delay in reacting to the user action due to the rescheduling,
// but it aligns with the platform’s threading model.
private fun submit(name: String, scope: CoroutineScope, task: suspend CoroutineScope.(ideFocusManager: IdeFocusManager) -> Unit) {
  val log = LOG
  log.debug { "MacMenu: on EDT = ${EDT.isCurrentThreadEdt()}, ENABLED = ${ENABLED.get()}" }
  if (!ENABLED.get()) {
    log.debug("MacMenu: disabled")
    return
  }
  if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
    log.debug("MacMenu: called too early")
    return
  }

  val ideFocusManager = IdeFocusManager.getGlobalInstance()
  val component = ideFocusManager.focusOwner
  if (component != null && IdeKeyEventDispatcher.isModalContext(component)) {
    log.debug("MacMenu: component in modal context")
  }
  else {
    ENABLED.set(false)
    scope.launch {
      log.debug { "MacMenu: init $name" }
      task(ideFocusManager)
    }.invokeOnCompletion {
      try {
        log.debug { "MacMenu: done $name" }
      }
      finally {
        ENABLED.set(true)
      }
    }
  }
}

private fun installProtocolHandler(desktop: Desktop, mainScope: CoroutineScope) {
  val mainBundle = Foundation.invoke("NSBundle", "mainBundle")
  val urlTypes = Foundation.invoke(mainBundle, "objectForInfoDictionaryKey:", Foundation.nsString("CFBundleURLTypes"))
  if (urlTypes == ID.NIL) {
    val build = ApplicationInfoImpl.getShadowInstance().build
    if (!build.isSnapshot) {
      LOG.warn("""
        No URL bundle (CFBundleURLTypes) is defined in the main bundle.
        To be able to open external links, specify protocols in the app layout section of the build file.
        Example: args.urlSchemes = ["your-protocol"] will handle following links: your-protocol://open?file=file&line=line
        """.trimIndent())
    }
    return
  }

  desktop.setOpenURIHandler { event ->
    val uri = event.uri
    var uriString = uri.toString()
    URLDecoder.decode(uriString, "UTF-8")
    if ("open" == uri.host && QueryStringDecoder(uri).parameters()["file"] != null) {
      uriString = CommandLineProcessor.SCHEME_INTERNAL + uriString
    }
    if (LoadingState.APP_STARTED.isOccurred) {
      mainScope.launch {
        CommandLineProcessor.processProtocolCommand(uriString)
      }
    }
    else {
      openUriOnLoading(uriString)
    }
  }
}
