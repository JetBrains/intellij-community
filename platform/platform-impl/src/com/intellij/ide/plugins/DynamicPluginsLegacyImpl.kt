// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.codeInsight.daemon.impl.InspectionVisitorOptimizer
import com.intellij.configurationStore.jdomSerializer
import com.intellij.diagnostic.MessagePool
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.hprof.action.SystemTempFilenameSupplier
import com.intellij.diagnostic.hprof.analysis.AnalyzeClassloaderReferencesGraph
import com.intellij.diagnostic.hprof.analysis.HProfAnalysis
import com.intellij.ide.DataManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.TopHitCache
import com.intellij.idea.IdeaLogger
import com.intellij.lang.Language
import com.intellij.notification.impl.ApplicationNotificationsModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointDeferredListenersNotification
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.getComponentManagerImpl
import com.intellij.ui.IconDeferrer
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.util.CachedValuesManagerImpl
import com.intellij.util.ReflectionUtil
import com.intellij.util.application
import com.intellij.util.containers.WeakList
import com.intellij.util.messages.impl.MessageBusEx
import com.intellij.util.xmlb.clearPropertyCollectorCache
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.function.Predicate
import javax.swing.ToolTipManager

private val LOG = logger<DynamicPlugins>()
internal val VETOER_EP_NAME = ExtensionPointName<DynamicPluginVetoer>("com.intellij.ide.dynamicPluginVetoer")

internal object DynamicPluginsLegacyImpl {
  internal fun clearCachesAfterUnload(
    classLoaders: WeakList<PluginClassLoader>,
  ) {
    (application as? ApplicationImpl)?.extensionArea?.clearUserCache()
    for (project in ProjectUtil.getOpenProjects()) {
      (project.extensionArea as ExtensionsAreaImpl).clearUserCache()
    }
    clearCachedValues()

    jdomSerializer.clearSerializationCaches()
    clearPropertyCollectorCache()
    InspectionVisitorOptimizer.clearCache()
    TopHitCache.getInstance().clear()
    ActionToolbarImpl.resetAllToolbars()
    PresentationFactory.clearPresentationCaches()
    TouchbarSupport.reloadAllActions()
    ApplicationNotificationsModel.expireAll()
    MessagePool.getInstance().clearErrors()
    LaterInvocator.purgeExpiredItems()
    FileAttribute.resetRegisteredIds()
    resetFocusCycleRoot()
    clearNewFocusOwner()
    hideTooltip()
    PerformanceWatcher.getInstance().clearFreezeStacktraces()

    for (classLoader in classLoaders) {
      IconLoader.detachClassLoader(classLoader)
    }
    serviceIfCreated<IconDeferrer>()?.clearCache()

    (ApplicationManager.getApplication().messageBus as MessageBusEx).clearPublisherCache()
    @Suppress("TestOnlyProblems")
    (ProjectManager.getInstanceIfCreated() as? ProjectManagerImpl)?.disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests()

    Disposer.clearDisposalTraces()   // ensure we don't have references to plugin classes in disposal backtraces
    ThrowableInterner.clearInternedBacktraces()
    IdeaLogger.ourErrorsOccurred = null   // ensure we don't have references to plugin classes in exception stacktraces
    clearTemporaryLostComponent()
    ActionToolbarImpl.updateAllToolbarsImmediately(true)
  }

  internal fun resetFocusCycleRoot() {
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    var focusCycleRoot = focusManager.currentFocusCycleRoot
    if (focusCycleRoot != null) {
      while (focusCycleRoot != null && focusCycleRoot !is IdeFrameImpl) {
        focusCycleRoot = focusCycleRoot.parent
      }
      if (focusCycleRoot is IdeFrameImpl) {
        focusManager.setGlobalCurrentFocusCycleRoot(focusCycleRoot)
      }
      else {
        focusCycleRoot = focusManager.currentFocusCycleRoot
        val dataContext = DataManager.getInstance().getDataContext(focusCycleRoot)
        val project = CommonDataKeys.PROJECT.getData(dataContext)
        if (project != null) {
          val projectFrame = WindowManager.getInstance().getFrame(project)
          if (projectFrame != null) {
            focusManager.setGlobalCurrentFocusCycleRoot(projectFrame)
          }
        }
      }
    }
  }

  // PluginId cannot be used to unload related resources because one plugin descriptor may consist of several sub descriptors,
  // each of them depends on presense of another plugin, here not the whole plugin is unloaded, but only one part.
  internal fun unloadModuleDescriptorNotRecursively(module: IdeaPluginDescriptorImpl) {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(module)

    val openedProjects = ProjectUtil.getOpenProjects().toMutableList()
    @Suppress("TestOnlyProblems")
    if (ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized) {
      openedProjects.add(ProjectManagerEx.getInstanceEx().defaultProject)
    }

    val appExtensionArea = app.extensionArea
    val priorityUnloadListeners = mutableListOf<Runnable>()
    val unloadListeners = mutableListOf<Runnable>()
    unregisterUnknownLevelExtensions(module.extensions, module, appExtensionArea, openedProjects,
                                     priorityUnloadListeners, unloadListeners)
    // note: here was a dead code for unregistering appContainer.extensions, but the map was always empty
    // note: here was a dead code for unregistering project level extensions, but it is already handled by a call above
    // note: here was a dead code for unregistering unknown level extensions with `moduleContainer.extensions` but the latter was always empty

    for (priorityUnloadListener in priorityUnloadListeners) {
      priorityUnloadListener.run()
    }
    for (unloadListener in unloadListeners) {
      unloadListener.run()
    }

    // first, reset all plugin extension points before unregistering, so that listeners don't see plugin in semi-torn-down state
    processExtensionPoints(module, openedProjects) { points, area ->
      area.resetExtensionPoints(points, module)
    }
    // unregister plugin extension points
    processExtensionPoints(module, openedProjects) { points, area ->
      area.unregisterExtensionPoints(points, module)
    }

    val appMessageBus = app.messageBus as MessageBusEx
    app.unloadServices(module, module.appContainerDescriptor.services)
    appMessageBus.unsubscribeLazyListeners(module, module.appContainerDescriptor.listeners)

    for (project in openedProjects) {
      (project as ComponentManagerEx).unloadServices(module, module.projectContainerDescriptor.services)
      (project.messageBus as MessageBusEx).unsubscribeLazyListeners(module, module.projectContainerDescriptor.listeners)

      val moduleServices = module.moduleContainerDescriptor.services
      for (ideaModule in ModuleManager.getInstance(project).modules) {
        (ideaModule as ComponentManagerEx).unloadServices(module, moduleServices)
        createDisposeTreePredicate(module)?.let { Disposer.disposeChildren(ideaModule, it) }
      }

      createDisposeTreePredicate(module)?.let { Disposer.disposeChildren(project, it) }
    }

    appMessageBus.disconnectPluginConnections(Predicate { aClass ->
      (aClass.classLoader as? PluginClassLoader)?.pluginDescriptor === module
    })

    createDisposeTreePredicate(module)?.let { Disposer.disposeChildren(ApplicationManager.getApplication(), it) }

    val pluginClassLoader = module.pluginClassLoader as? PluginClassLoader
    if (pluginClassLoader != null) {
      Language.unregisterAllLanguagesIn(pluginClassLoader, module)
    }
  }

  internal fun unregisterUnknownLevelExtensions(extensionMap: Map<String, List<ExtensionDescriptor>>,
                                               pluginDescriptor: IdeaPluginDescriptorImpl,
                                               appExtensionArea: ExtensionsAreaImpl,
                                               openedProjects: List<Project>,
                                               priorityUnloadListeners: MutableList<Runnable>,
                                               unloadListeners: MutableList<Runnable>) {
    for (epName in extensionMap.keys) {
      val isAppLevelEp = appExtensionArea.unregisterExtensions(epName, pluginDescriptor, priorityUnloadListeners,
                                                               unloadListeners)
      if (isAppLevelEp) {
        continue
      }

      for (project in openedProjects) {
        val isProjectLevelEp = (project.extensionArea as ExtensionsAreaImpl)
          .unregisterExtensions(epName, pluginDescriptor, priorityUnloadListeners, unloadListeners)
        if (!isProjectLevelEp) {
          for (module in ModuleManager.getInstance(project).modules) {
            (module.extensionArea as ExtensionsAreaImpl)
              .unregisterExtensions(epName, pluginDescriptor, priorityUnloadListeners, unloadListeners)
          }
        }
      }
    }
  }

  internal fun processExtensionPoints(pluginDescriptor: IdeaPluginDescriptorImpl,
                                            projects: List<Project>,
                                            processor: (points: List<ExtensionPointDescriptor>, area: ExtensionsAreaImpl) -> Unit) {
    pluginDescriptor.appContainerDescriptor.extensionPoints.takeIf { it.isNotEmpty() }?.let {
      processor(it, ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
    }
    pluginDescriptor.projectContainerDescriptor.extensionPoints.takeIf { it.isNotEmpty() }?.let { extensionPoints ->
      for (project in projects) {
        processor(extensionPoints, project.extensionArea as ExtensionsAreaImpl)
      }
    }
    pluginDescriptor.moduleContainerDescriptor.extensionPoints.takeIf { it.isNotEmpty() }?.let { extensionPoints ->
      for (project in projects) {
        for (module in ModuleManager.getInstance(project).modules) {
          processor(extensionPoints, module.extensionArea as ExtensionsAreaImpl)
        }
      }
    }
  }

  internal fun registerDescriptors(
    app: ApplicationImpl,
    descriptors: Sequence<IdeaPluginDescriptorImpl>,
    listenerCallbacks: MutableList<ExtensionPointDeferredListenersNotification>,
  ) {
    app.registerComponents(descriptors = descriptors, app = app, listenerCallbacks = listenerCallbacks)

    val openedProjects = ProjectUtil.getOpenProjects().toMutableList()
    @Suppress("TestOnlyProblems")
    if (ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized) {
      openedProjects.add(ProjectManagerEx.getInstanceEx().defaultProject)
    }

    for (openProject in openedProjects) {
      openProject.getComponentManagerImpl().registerComponents(descriptors = descriptors, app = app, listenerCallbacks = listenerCallbacks)

      for (module in ModuleManager.getInstance(openProject).modules) {
        module.getComponentManagerImpl().registerComponents(descriptors = descriptors, app = app, listenerCallbacks = listenerCallbacks)
      }
    }

    (ActionManager.getInstance() as ActionManagerImpl).registerActions(descriptors)
  }

  internal fun analyzeSnapshot(hprofPath: String, pluginId: PluginId): String {
    FileChannel.open(Paths.get(hprofPath), StandardOpenOption.READ).use { channel ->
      val analysis = HProfAnalysis(channel, SystemTempFilenameSupplier()) { analysisContext, listProvider, progressIndicator ->
        AnalyzeClassloaderReferencesGraph(analysisContext, listProvider, pluginId.idString).analyze(progressIndicator).mainReport.toString()
      }
      analysis.onlyStrongReferences = true
      analysis.includeClassesAsRoots = false
      analysis.setIncludeMetaInfo(false)
      return analysis.analyze(ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator())
    }
  }

  internal fun createDisposeTreePredicate(pluginDescriptor: IdeaPluginDescriptorImpl): Predicate<Disposable>? {
    val classLoader = pluginDescriptor.pluginClassLoader as? PluginClassLoader ?: return null
    return Predicate {
      it::class.java.classLoader === classLoader
    }
  }

  internal fun clearCachedValues() {
    for (project in ProjectUtil.getOpenProjects()) {
      (CachedValuesManager.getManager(project) as? CachedValuesManagerImpl)?.clearCachedValues()
    }
  }

  internal fun hideTooltip() {
    try {
      val showMethod = ToolTipManager::class.java.declaredMethods.find { it.name == "show" }
      if (showMethod == null) {
        LOG.info("ToolTipManager.show method not found")
        return
      }
      showMethod.isAccessible = true
      showMethod.invoke(ToolTipManager.sharedInstance(), null)
    }
    catch (e: Throwable) {
      LOG.info("Failed to hide tooltip", e)
    }
  }

  internal fun clearNewFocusOwner() {
    val field = ReflectionUtil.getDeclaredField(KeyboardFocusManager::class.java, "newFocusOwner")
    if (field != null) {
      try {
        field.set(null, null)
      }
      catch (e: Throwable) {
        LOG.info(e)
      }
    }
  }
}

private fun clearTemporaryLostComponent() {
  try {
    val clearMethod = Window::class.java.declaredMethods.find { it.name == "setTemporaryLostComponent" }
    if (clearMethod == null) {
      LOG.info("setTemporaryLostComponent method not found")
      return
    }
    clearMethod.isAccessible = true
    loop@ for (frame in WindowManager.getInstance().allProjectFrames) {
      val window = when (frame) {
        is ProjectFrameHelper -> frame.frame
        is Window -> frame
        else -> continue@loop
      }
      clearMethod.invoke(window, null)
    }
  }
  catch (e: Throwable) {
    LOG.info("Failed to clear Window.temporaryLostComponent", e)
  }
}