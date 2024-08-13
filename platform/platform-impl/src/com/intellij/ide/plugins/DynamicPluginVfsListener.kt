// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.IdeFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

private const val AUTO_RELOAD_PLUGINS_SYSTEM_PROPERTY = "idea.auto.reload.plugins"

private var initialRefreshDone = false

private val LOG: Logger
  get() = logger<DynamicPluginVfsListener>()

private class DynamicPluginVfsListenerInitializer : ApplicationActivity {
  init {
    if (!java.lang.Boolean.getBoolean(AUTO_RELOAD_PLUGINS_SYSTEM_PROPERTY) || ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    val pluginsPath = PathManager.getPluginsPath()
    val localFileSystem = LocalFileSystem.getInstance()
    localFileSystem.addRootToWatch(pluginsPath, true)
    val pluginRoot = withContext(Dispatchers.IO) {
      localFileSystem.refreshAndFindFileByNioFile(Path.of(pluginsPath))
    }
    if (pluginRoot == null) {
      LOG.info("Dynamic plugin VFS listener not active, couldn't find plugins root in VFS")
    }
    else {
      // ensure all plugins are in VFS
      VfsUtilCore.processFilesRecursively(pluginRoot) { true }
      RefreshQueue.getInstance().refresh(true, true, Runnable { initialRefreshDone = true }, pluginRoot)
    }
  }
}

private class DynamicPluginVfsListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (!java.lang.Boolean.getBoolean(AUTO_RELOAD_PLUGINS_SYSTEM_PROPERTY)) {
      return null
    }
    if (!initialRefreshDone) {
      return null
    }

    val pluginsToReload = hashSetOf<IdeaPluginDescriptorImpl>()
    for (event in events) {
      if (!event.isFromRefresh) continue
      if (event is VFileContentChangeEvent) {
        findPluginByPath(event.file)?.let {
          LOG.info("Detected plugin .jar file change ${event.path}, reloading plugin")
          pluginsToReload.add(it)
        }
      }
    }

    val descriptorsToReload = pluginsToReload.filter { it.isEnabled && DynamicPlugins.allowLoadUnloadWithoutRestart(it) }
    if (descriptorsToReload.isEmpty()) {
      return null
    }

    return object : AsyncFileListener.ChangeApplier {
      override fun afterVfsChange() {
        ApplicationManager.getApplication().invokeLater {
          val reloaded = mutableListOf<String>()
          val unloadFailed = mutableListOf<String>()
          for (pluginDescriptor in descriptorsToReload) {
            if (!DynamicPlugins.unloadPlugin(pluginDescriptor,
                                             DynamicPlugins.UnloadPluginOptions(isUpdate = true, waitForClassloaderUnload = true))) {
              unloadFailed.add(pluginDescriptor.name)
              continue
            }
            reloaded.add(pluginDescriptor.name)
            DynamicPlugins.loadPlugin(pluginDescriptor)
          }
          if (unloadFailed.isNotEmpty()) {
            DynamicPlugins.notify(IdeBundle.message("failed.to.unload.modified.plugins", unloadFailed.joinToString()),
                                  NotificationType.INFORMATION,
                                  object : AnAction(IdeBundle.message("ide.restart.action")) {
                                    override fun actionPerformed(e: AnActionEvent) {
                                      ApplicationManager.getApplication().restart()
                                    }
                                  })
          }
          else if (reloaded.isNotEmpty()) {
            DynamicPlugins.notify(IdeBundle.message("plugins.reloaded.successfully", reloaded.joinToString()), NotificationType.INFORMATION)
          }
        }
      }
    }
  }

  private fun findPluginByPath(file: VirtualFile): IdeaPluginDescriptorImpl? {
    if (!VfsUtilCore.isAncestorOrSelf(PathManager.getPluginsPath(), file)) {
      return null
    }
    return PluginManager.getPlugins().firstOrNull {
      VfsUtilCore.isAncestorOrSelf(it.pluginPath.toAbsolutePath().toString(), file)
    } as IdeaPluginDescriptorImpl?
  }
}

private class DynamicPluginsFrameStateListener : ApplicationActivationListener {
  init {
    if (!java.lang.Boolean.getBoolean(AUTO_RELOAD_PLUGINS_SYSTEM_PROPERTY)) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun applicationActivated(ideFrame: IdeFrame) {
    LocalFileSystem.getInstance().findFileByPath(PathManager.getPluginsPath())?.refresh(true, true)
  }
}
