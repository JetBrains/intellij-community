// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.FrameStateListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * @author yole
 */
private const val AUTO_RELOAD_PLUGINS_SYSTEM_PROPERTY = "idea.auto.reload.plugins"

class DynamicPluginVfsListener : AsyncFileListener {
  init {
    if (System.getProperty(AUTO_RELOAD_PLUGINS_SYSTEM_PROPERTY) != null) {
      val pluginsPath = PathManager.getPluginsPath()
      LocalFileSystem.getInstance().addRootToWatch(pluginsPath, true)
      val pluginsRoot = LocalFileSystem.getInstance().findFileByPath(pluginsPath)
      if (pluginsRoot != null) {
        // ensure all plugins are in VFS
        VfsUtilCore.processFilesRecursively(pluginsRoot) { true }
      }
    }
  }

  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (System.getProperty(AUTO_RELOAD_PLUGINS_SYSTEM_PROPERTY) == null) return null

    val pluginsToReload = hashSetOf<IdeaPluginDescriptorImpl>()
    for (event in events) {
      if (!event.isFromRefresh) continue
      if (event is VFileContentChangeEvent) {
        findPluginByPath(event.path)?.let { pluginsToReload.add(it) }
      }
    }
    val descriptorsToReload = pluginsToReload
      .filter { it.isEnabled }
      .map { PluginEnabler.loadFullDescriptor(it) }
      .filter { DynamicPlugins.allowLoadUnloadWithoutRestart(it) }

    if (descriptorsToReload.isEmpty()) return null

    return object : AsyncFileListener.ChangeApplier {
      override fun afterVfsChange() {
        ApplicationManager.getApplication().invokeLater {
          val reloaded = mutableListOf<String>()
          for (pluginDescriptor in descriptorsToReload) {
            if (!DynamicPlugins.unloadPlugin(pluginDescriptor, isUpdate = true)) {
              continue
            }
            reloaded.add(pluginDescriptor.name)
            DynamicPlugins.loadPlugin(pluginDescriptor, false)
          }
          if (reloaded.isNotEmpty()) {
            DynamicPlugins.notify("${reloaded.joinToString()} reloaded successfully", NotificationType.INFORMATION)
          }
        }
      }
    }
  }

  private fun findPluginByPath(path: String): IdeaPluginDescriptorImpl? {
    if (!FileUtil.isAncestor(PathManager.getPluginsPath(), path, false)) return null
    return PluginManager.getPlugins().firstOrNull {
      FileUtil.isAncestor(it.path.absolutePath, path, false)
    } as IdeaPluginDescriptorImpl?
  }
}

class DynamicPluginsFrameStateListener : FrameStateListener {
  override fun onFrameActivated() {
    if (System.getProperty(AUTO_RELOAD_PLUGINS_SYSTEM_PROPERTY) == null) return

    val pluginsRoot = LocalFileSystem.getInstance().findFileByPath(PathManager.getPluginsPath())
    pluginsRoot?.refresh(true, true)
  }
}
