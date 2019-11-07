// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.configurationStore.jdomSerializer
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.keymap.impl.BundledKeymapBean
import com.intellij.openapi.keymap.impl.BundledKeymapProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.serviceContainer.PlatformComponentManagerImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.BeanBinding
import java.text.SimpleDateFormat
import java.util.*

interface DynamicPluginListener {
  @JvmDefault
  fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  @JvmDefault
  fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor) {
  }

  companion object {
    @JvmField val TOPIC = Topic.create("DynamicPluginListener", DynamicPluginListener::class.java)
  }
}

object DynamicPlugins {
  private val LOG = Logger.getInstance(DynamicPlugins::class.java)
  private val GROUP = NotificationGroup("Dynamic plugin installation", NotificationDisplayType.BALLOON, false)

  @JvmStatic
  fun allowLoadUnloadWithoutRestart(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    if (!ApplicationManager.getApplication().isInternal) {
      return allowLoadUnloadSynchronously(pluginDescriptor)
    }

    val anyProject = ProjectManager.getInstance().openProjects.firstOrNull() ?:
                     ProjectManager.getInstance().defaultProject

    val extensions = pluginDescriptor.extensions
    if (extensions != null) {
      for (epName in extensions.keys) {
        if (isPluginExtensionPoint(pluginDescriptor, epName)) continue

        val ep = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName) ?:
          anyProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
        if (ep == null || !ep.isDynamic) {
          LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because of extension $epName")
          return false
        }
      }
    }

    return hasNoComponents(pluginDescriptor) &&
           (ActionManager.getInstance() as ActionManagerImpl).canUnloadActions(pluginDescriptor)
  }

  private fun isPluginExtensionPoint(pluginDescriptor: IdeaPluginDescriptorImpl, epName: String): Boolean {
    return isContainerExtensionPoint(pluginDescriptor.app, pluginDescriptor, epName) ||
           isContainerExtensionPoint(pluginDescriptor.project, pluginDescriptor, epName) ||
           isContainerExtensionPoint(pluginDescriptor.module, pluginDescriptor, epName)
  }

  private fun isContainerExtensionPoint(containerDescriptor: ContainerDescriptor, pluginDescriptor: IdeaPluginDescriptorImpl, epName: String): Boolean {
    val extensionPoints = containerDescriptor.extensionPoints ?: return false
    return extensionPoints.any {
      (Extensions.getRootArea() as ExtensionsAreaImpl).getExtensionPointName(it, pluginDescriptor) == epName
    }
  }

  /**
   * Checks if the plugin can be loaded/unloaded immediately when the corresponding action is invoked in the
   * plugins settings, without pressing the Apply button.
   */
  @JvmStatic
  fun allowLoadUnloadSynchronously(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    val extensions = pluginDescriptor.extensions
    if (extensions != null && !extensions.all {
        it.key == UIThemeProvider.EP_NAME.name ||
        it.key == BundledKeymapBean.EP_NAME.name ||
        it.key == BundledKeymapProvider.EP_NAME.name }) {
      return false
    }
    return hasNoComponents(pluginDescriptor) && pluginDescriptor.actionDescriptionElements.isNullOrEmpty()
  }

  private fun hasNoComponents(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    return isUnloadSafe(pluginDescriptor.appContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.projectContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.moduleContainerDescriptor)
  }

  private fun isUnloadSafe(containerDescriptor: ContainerDescriptor): Boolean {
    return containerDescriptor.components.isNullOrEmpty()
  }

  @JvmStatic
  @JvmOverloads
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, disable: Boolean = false): Boolean {
    val application = ApplicationManager.getApplication() as ApplicationImpl

    application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(pluginDescriptor)

    // The descriptor passed to `unloadPlugin` is the full descriptor loaded from disk, it does not have a classloader.
    // We need to find the real plugin loaded into the current instance and unload its classloader.
    val loadedPluginDescriptor = PluginManagerCore.getPlugin(pluginDescriptor.pluginId) as? IdeaPluginDescriptorImpl ?: return false

    if (!pluginDescriptor.useIdeaClassLoader) {
      IconLoader.detachClassLoader(loadedPluginDescriptor.pluginClassLoader)
    }

    application.runWriteAction {
      (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)

      val openProjects = ProjectManager.getInstance().openProjects

      pluginDescriptor.extensions?.let { extensions ->
        for (epName in extensions.keys) {
          val appEp = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName)
          if (appEp != null) {
            appEp.unregisterExtensions({ _, adapter -> adapter.pluginDescriptor != pluginDescriptor }, false)
          }
          else {
            for (openProject in openProjects) {
              val projectEp = openProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
              projectEp?.unregisterExtensions({ _, adapter -> adapter.pluginDescriptor != pluginDescriptor }, false)
            }
          }
        }
      }

      pluginDescriptor.app.extensionsPoints?.let {
        for (extensionPointElement in it) {
          val rootArea = Extensions.getRootArea() as ExtensionsAreaImpl
          rootArea.unregisterExtensionPoint(rootArea.getExtensionPointName(extensionPointElement, pluginDescriptor))
        }
      }
      pluginDescriptor.project.extensionsPoints?.let {
        for (extensionPointElement in it) {
          val extensionPointName = (Extensions.getRootArea() as ExtensionsAreaImpl).getExtensionPointName(extensionPointElement, pluginDescriptor)
          for (openProject in openProjects) {
            openProject.extensionArea.unregisterExtensionPoint(extensionPointName)
          }
        }
      }

      val appServiceInstances = application.unloadServices(pluginDescriptor.app)
      for (appServiceInstance in appServiceInstances) {
        application.stateStore.unloadComponent(appServiceInstance)
      }

      for (project in openProjects) {
        val projectServiceInstances = (project as ProjectImpl).unloadServices(pluginDescriptor.project)
        for (projectServiceInstance in projectServiceInstances) {
          project.stateStore.unloadComponent(projectServiceInstance)
        }

        for (module in ModuleManager.getInstance(project).modules) {
          val moduleServiceInstances = (module as PlatformComponentManagerImpl).unloadServices(pluginDescriptor.module)
          for (moduleServiceInstance in moduleServiceInstances) {
            module.stateStore.unloadComponent(moduleServiceInstance)
          }
        }
      }
    }

    jdomSerializer.clearSerializationCaches()
    BeanBinding.clearSerializationCaches()
    Disposer.clearDisposalTraces()  // ensure we don't have references to plugin classes in disposal backtraces

    if (disable) {
      // Update list of disabled plugins
      PluginManagerCore.setPlugins(PluginManagerCore.getPlugins())
    }
    else {
      PluginManagerCore.setPlugins(ArrayUtil.remove(PluginManagerCore.getPlugins(), loadedPluginDescriptor))
    }

    UIUtil.dispatchAllInvocationEvents()

    val classLoaderUnloaded = loadedPluginDescriptor.unloadClassLoader()
    if (!classLoaderUnloaded && Registry.`is`("ide.plugins.snapshot.on.unload.fail") && MemoryDumpHelper.memoryDumpAvailable()) {
      val snapshotFolder = System.getProperty("snapshots.path", SystemProperties.getUserHome())
      val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
      val snapshotPath = "$snapshotFolder/unload-${pluginDescriptor.pluginId}-$snapshotDate.hprof"
      MemoryDumpHelper.captureMemoryDump(snapshotPath)
      GROUP.createNotification("Captured memory snapshot on plugin unload fail: $snapshotPath", NotificationType.WARNING).notify(null)
    }

    return classLoaderUnloaded
  }

  @JvmStatic
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl) {
    PluginManagerCore.initClassLoader(pluginDescriptor)

    val application = ApplicationManager.getApplication() as ApplicationImpl
    application.runWriteAction {
      application.registerComponents(listOf(pluginDescriptor), true)
      for (openProject in ProjectManager.getInstance().openProjects) {
        (openProject as ProjectImpl).registerComponents(listOf(pluginDescriptor), true)
        for (module in ModuleManager.getInstance(openProject).modules) {
          (module as PlatformComponentManagerImpl).registerComponents(listOf(pluginDescriptor), true)
        }
      }
      (ActionManager.getInstance() as ActionManagerImpl).registerPluginActions(pluginDescriptor)
    }

    PluginManagerCore.setPlugins(ArrayUtil.mergeArrays(PluginManagerCore.getPlugins(), arrayOf(pluginDescriptor)))
    application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginLoaded(pluginDescriptor)
  }

  @JvmStatic
  fun onPluginUnload(parentDisposable: Disposable, callback: Runnable) {
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor) {
        callback.run()
      }
    })
  }
}
