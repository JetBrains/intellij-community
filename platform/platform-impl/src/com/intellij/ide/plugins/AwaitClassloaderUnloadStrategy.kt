// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.DynamicPluginsLegacyImpl.analyzeSnapshot
import com.intellij.ide.plugins.DynamicPluginsLegacyImpl.clearCachesAfterUnload
import com.intellij.ide.plugins.PluginUtils.asSanitizedPathElement
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.util.containers.WeakList
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val LOG get() = Logger.getInstance(AwaitClassloaderUnloadStrategy::class.java)

internal interface AwaitClassloaderUnloadStrategy {
  /** @return false if dynamic reconfiguration should be terminated and treated as incomplete */
  suspend fun awaitClassloadersUnloadedBeforeLoad(classloaders: WeakList<PluginClassLoader>): Boolean
  /** @return false if dynamic reconfiguration should be treated as incomplete */
  suspend fun awaitClassloadersUnloadedPostReconfiguration(classloaders: WeakList<PluginClassLoader>): Boolean
}

internal class AwaitClassloaderUnloadBeforeLoading : AwaitClassloaderUnloadStrategy {
  override suspend fun awaitClassloadersUnloadedBeforeLoad(classloaders: WeakList<PluginClassLoader>): Boolean {
    val collected = withProgressText(IdeBundle.message("progress.text.waiting.for.plugins.to.fully.unload")) {
      try {
        withTimeout(5.seconds) {
          awaitClassLoadersGetGarbageCollected(classloaders)
        }
        true
      }
      catch (e: TimeoutCancellationException) {
        false
      }
    }
    return collected
  }

  override suspend fun awaitClassloadersUnloadedPostReconfiguration(classloaders: WeakList<PluginClassLoader>): Boolean {
    return true
  }
}

internal class AwaitClassloaderUnloadAsyncPostReconfiguration : AwaitClassloaderUnloadStrategy {
  override suspend fun awaitClassloadersUnloadedBeforeLoad(classloaders: WeakList<PluginClassLoader>): Boolean {
    return true
  }

  override suspend fun awaitClassloadersUnloadedPostReconfiguration(classloaders: WeakList<PluginClassLoader>): Boolean {
    if (classloaders.firstOrNull() == null) {
      return true
    }
    // try trigger full GC on JBR, perhaps we won't need to bother the user with background progress
    GCWatcher.tracking(classloaders).tryCollect(0)
    if (classloaders.firstOrNull() == null) {
      return true
    }
    service<DynamicPluginsSupportService>().coroutineScope.launch(Dispatchers.Default) {
      delay(3.seconds) // give time for theme plugins to unload without showing a progress bar
      val project = ProjectUtil.getActiveProject() ?: ProjectUtil.getOpenProjects().firstOrNull() // TODO this is kinda clumsy
      if (project != null) {
        withBackgroundProgress(project, IdeBundle.message("progress.text.waiting.for.plugins.to.fully.unload")) {
          postReconfigurationAwaitImpl(classloaders)
        }
      } else {
        postReconfigurationAwaitImpl(classloaders)
      }
    }
    return true
  }

  private suspend fun postReconfigurationAwaitImpl(classloaders: WeakList<PluginClassLoader>) {
    val unloaded = withContext(Dispatchers.IO) {
      awaitUnload(classloaders)
    }
    if (!unloaded) {
      val notification = UpdateCheckerFacade.getInstance().getNotificationGroupForPluginUpdateResults().createNotification(
        IdeBundle.message("notification.on.tool.window.title.restart.advised"),
        IdeBundle.message("notification.content.plugins.didnt.unload.cleanly"),
        NotificationType.WARNING
      )
      notification.addAction(object : AnAction(IdeBundle.message("ide.restart.action")), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = ApplicationManager.getApplication().restart()
      })
      notification.addAction(object : AnAction(IdeBundle.message("action.save.memory.snapshot.text")), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
          val project = e.project
          service<DynamicPluginsSupportService>().coroutineScope.launch(CoroutineName("saving memory snapshot") + Dispatchers.IO) {
            fun doSave() {
              saveMemorySnapshot(classloaders.firstOrNull()?.pluginId ?: PluginId.getId("unknown"))
            }
            if (project != null) {
              withBackgroundProgress(project, IdeBundle.message("progress.title.saving.memory.snapshot")) {
                doSave()
              }
            } else {
              doSave()
            }
            notification.expire()
          }
        }
      })
      notification.notify(null)
    }
  }

  private suspend fun awaitUnload(classloaders: WeakList<PluginClassLoader>): Boolean {
    try {
      withTimeout(15.seconds) {
        while (true) {
          if (classloaders.firstOrNull() == null) {
            return@withTimeout
          }
          delay(250.milliseconds)
        }
      }
    }
    catch (e: TimeoutCancellationException) { }
    GCWatcher.tracking(classloaders).tryCollect(1)
    if (classloaders.firstOrNull() == null) {
      return true
    }
    return false
  }
}

private suspend fun awaitClassLoadersGetGarbageCollected(classloaders: WeakList<PluginClassLoader>) {
  try {
    while (true) {
      val collected = withContext(Dispatchers.IO) {
        GCWatcher.tracking(classloaders).tryCollect(50)
      }
      if (collected) {
        return
      }
      withContext(Dispatchers.EDT) {
        clearCachesAfterUnload(classloaders) // TODO :ded-smile:
      }
    }
  }
  catch (e: CancellationException) {
    withContext(NonCancellable + Dispatchers.IO) {
      if (Registry.`is`("ide.plugins.snapshot.on.unload.fail") && !application.isUnitTestMode && MemoryDumpHelper.memoryDumpAvailable()) {
        saveMemorySnapshot(classloaders.firstOrNull()?.pluginId ?: PluginId.getId("unknown"))
      }
    }
    throw e
  }
}

private fun saveMemorySnapshot(pluginId: PluginId) {
  val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
  val snapshotFileName = "unload-${pluginId.asSanitizedPathElement()}-$snapshotDate.hprof"
  val snapshotPath = System.getProperty("memory.snapshots.path", SystemProperties.getUserHome()) + "/" + snapshotFileName

  MemoryDumpHelper.captureMemoryDump(snapshotPath)

  if (Registry.`is`("ide.plugins.analyze.snapshot")) {
    val analysisResult = analyzeSnapshot(snapshotPath, pluginId)
    @Suppress("ReplaceSizeZeroCheckWithIsEmpty")
    if (analysisResult.length == 0) {
      LOG.info("Successfully unloaded plugin $pluginId (no strong references to classloader in .hprof file)")
      return
    }
    else {
      LOG.error("Snapshot analysis result: $analysisResult")
    }
  }

  DynamicPluginsLegacyImpl.notify(
    IdeBundle.message("memory.snapshot.captured.text", snapshotPath),
    NotificationType.WARNING,
    object : AnAction(IdeBundle.message("ide.restart.action")), DumbAware {
      override fun actionPerformed(e: AnActionEvent) = ApplicationManager.getApplication().restart()
    },
    object : AnAction(
      IdeBundle.message("memory.snapshot.captured.action.text", snapshotFileName, RevealFileAction.getFileManagerName())), DumbAware {
      override fun actionPerformed(e: AnActionEvent) = RevealFileAction.openFile(Paths.get(snapshotPath))
    }
  )

  LOG.info("Plugin $pluginId is not unload-safe because class loader cannot be unloaded. Memory snapshot created at $snapshotPath")
}
