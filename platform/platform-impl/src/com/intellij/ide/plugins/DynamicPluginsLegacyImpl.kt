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
import com.intellij.notification.impl.ApplicationNotificationsModel
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.psi.util.CachedValuesManager
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
import javax.swing.ToolTipManager

private val LOG = logger<DynamicPlugins>()

internal object DynamicPluginsLegacyImpl {
  internal fun clearCachesAfterUnload(classLoaders: WeakList<PluginClassLoader>) {
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

  internal fun clearCachedValues() {
    for (project in ProjectUtil.getOpenProjects()) {
      (CachedValuesManager.getManager(project) as? CachedValuesManagerImpl)?.clearCachedValues()
    }
  }

  private fun resetFocusCycleRoot() {
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

  private fun hideTooltip() {
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

  private fun clearNewFocusOwner() {
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
}