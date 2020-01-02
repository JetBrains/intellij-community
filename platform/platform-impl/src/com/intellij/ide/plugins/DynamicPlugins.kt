// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.configurationStore.jdomSerializer
import com.intellij.ide.plugins.cl.PluginClassLoader
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
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.keymap.impl.BundledKeymapBean
import com.intellij.openapi.keymap.impl.BundledKeymapProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.PlatformComponentManagerImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.CachedValuesManagerImpl
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.MessageBusImpl
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.BeanBinding
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JComponent

interface DynamicPluginListener {
  @JvmDefault
  fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  @JvmDefault
  fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  /**
   * @param isUpdate true if the plugin is being unloaded as part of an update installation and a new version will be loaded afterwards
   */
  @JvmDefault
  fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }

  @JvmDefault
  fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }

  companion object {
    @JvmField val TOPIC = Topic.create("DynamicPluginListener", DynamicPluginListener::class.java)
  }
}

object DynamicPlugins {
  private val LOG = Logger.getInstance(DynamicPlugins::class.java)
  private val GROUP = NotificationGroup("Dynamic plugin installation", NotificationDisplayType.BALLOON, false)

  val pluginDisposables = ConcurrentFactoryMap.createWeakMap<PluginDescriptor, Disposable> {
    plugin -> Disposer.newDisposable("Plugin disposable [${plugin.name}]")
  }

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
    return isContainerExtensionPoint(pluginDescriptor.app, epName) ||
           isContainerExtensionPoint(pluginDescriptor.project, epName) ||
           isContainerExtensionPoint(pluginDescriptor.module, epName)
  }

  private fun isContainerExtensionPoint(containerDescriptor: ContainerDescriptor, epName: String): Boolean {
    val extensionPoints = containerDescriptor.extensionPoints ?: return false
    return extensionPoints.any {
      it.name == epName
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
  fun unloadPluginWithProgress(parentComponent: JComponent?,
                               pluginDescriptor: IdeaPluginDescriptorImpl,
                               disable: Boolean = false,
                               isUpdate: Boolean = false): Boolean {
    var result = false
    val indicator = PotemkinProgress("Unloading plugin ${pluginDescriptor.name}", null, parentComponent, null)
    indicator.runInSwingThread {
      result = unloadPlugin(pluginDescriptor, disable, isUpdate)
    }
    return result
  }

  @JvmStatic
  @JvmOverloads
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, disable: Boolean = false, isUpdate: Boolean = false): Boolean {
    val application = ApplicationManager.getApplication() as ApplicationImpl

    // The descriptor passed to `unloadPlugin` is the full descriptor loaded from disk, it does not have a classloader.
    // We need to find the real plugin loaded into the current instance and unload its classloader.
    val loadedPluginDescriptor = PluginManagerCore.getPlugin(pluginDescriptor.pluginId) as? IdeaPluginDescriptorImpl
                                 ?: return false

    try {
      application.runWriteAction {
        try {
          application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(pluginDescriptor, isUpdate)


          if (!pluginDescriptor.useIdeaClassLoader) {
            IconLoader.detachClassLoader(loadedPluginDescriptor.pluginClassLoader)
          }

          (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)

          val openProjects = ProjectManager.getInstance().openProjects

          val unloadListeners = mutableListOf<Runnable>()
          pluginDescriptor.extensions?.let { extensions ->
            for (epName in extensions.keys) {
              val appEp = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName)
              if (appEp != null) {
                appEp.unregisterExtensions({ _, adapter -> adapter.pluginDescriptor != pluginDescriptor }, false, unloadListeners)
              }
              else {
                for (openProject in openProjects) {
                  val projectEp = openProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
                  projectEp?.unregisterExtensions({ _, adapter -> adapter.pluginDescriptor != pluginDescriptor }, false, unloadListeners)
                }
              }
            }
          }
          for (unloadListener in unloadListeners) {
            unloadListener.run()
          }

          pluginDescriptor.app.extensionPoints?.let {
            for (point in it) {
              val rootArea = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl
              rootArea.unregisterExtensionPoint(point.name)
            }
          }
          pluginDescriptor.project.extensionPoints?.let {
            for (point in it) {
              val extensionPointName = point.name
              for (openProject in openProjects) {
                openProject.extensionArea.unregisterExtensionPoint(extensionPointName)
              }
            }
          }

          val appServiceInstances = application.unloadServices(pluginDescriptor.app)
          for (appServiceInstance in appServiceInstances) {
            application.stateStore.unloadComponent(appServiceInstance)
          }
          (application.messageBus as MessageBusImpl).unsubscribePluginListeners(loadedPluginDescriptor)

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
            (CachedValuesManager.getManager(project) as CachedValuesManagerImpl).clearCachedValues()
            (project.messageBus as MessageBusImpl).unsubscribePluginListeners(loadedPluginDescriptor)
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

        }
        finally {
          val disposable = pluginDisposables.remove(pluginDescriptor)
          if (disposable != null) {
            Disposer.dispose(disposable)
          }
          application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginUnloaded(pluginDescriptor, isUpdate)
        }
      }
    } finally {
      UIUtil.dispatchAllInvocationEvents()

      val classLoaderUnloaded = loadedPluginDescriptor.unloadClassLoader()
      if (!classLoaderUnloaded && Registry.`is`("ide.plugins.snapshot.on.unload.fail") && MemoryDumpHelper.memoryDumpAvailable()) {
        val snapshotFolder = System.getProperty("snapshots.path", SystemProperties.getUserHome())
        val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
        val snapshotPath = "$snapshotFolder/unload-${pluginDescriptor.pluginId}-$snapshotDate.hprof"
        MemoryDumpHelper.captureMemoryDump(snapshotPath)
        GROUP.createNotification("Captured memory snapshot on plugin unload fail: $snapshotPath",
                                 NotificationType.WARNING).notify(null)
      }

      return classLoaderUnloaded
    }
  }

  @JvmStatic
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, wasDisabled: Boolean) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      PluginManagerCore.initClassLoader(pluginDescriptor)
    }
    val application = ApplicationManager.getApplication() as ApplicationImpl
    application.runWriteAction {
      application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginLoaded(pluginDescriptor)

      try {
        application.registerComponents(listOf(pluginDescriptor), true)
        for (openProject in ProjectManager.getInstance().openProjects) {
          (openProject as ProjectImpl).registerComponents(listOf(pluginDescriptor), true)
          for (module in ModuleManager.getInstance(openProject).modules) {
            (module as PlatformComponentManagerImpl).registerComponents(listOf(pluginDescriptor), true)
          }
          (CachedValuesManager.getManager(openProject) as CachedValuesManagerImpl).clearCachedValues()
        }
        (ActionManager.getInstance() as ActionManagerImpl).registerPluginActions(pluginDescriptor)

        if (wasDisabled) {
          // Update list of disabled plugins
          (PluginManagerCore.getPlugin(pluginDescriptor.pluginId) as? IdeaPluginDescriptorImpl)?.setLoader(pluginDescriptor.pluginClassLoader)
          PluginManagerCore.setPlugins(PluginManagerCore.getPlugins())
        }
        else {
          PluginManagerCore.setPlugins(ArrayUtil.mergeArrays(PluginManagerCore.getPlugins(), arrayOf(pluginDescriptor)))
        }
      }
      finally {
        application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginLoaded(pluginDescriptor)
      }
    }
  }

  @JvmStatic
  fun pluginDisposable(clazz: Class<*>): Disposable? {
    val classLoader = clazz.classLoader
    if (classLoader is PluginClassLoader) {
      val pluginDescriptor = classLoader.pluginDescriptor
      if (pluginDescriptor != null) {
        return pluginDisposables[pluginDescriptor]
      }
    }
    return null
  }

  @JvmStatic
  fun pluginDisposableWrapper(clazz: Class<*>, defaultValue: Disposable): Disposable {
    val pluginDisposable = pluginDisposable(clazz)
    if (pluginDisposable != null) {
      val result = Disposer.newDisposable()
      Disposer.register(pluginDisposable, result)
      Disposer.register(defaultValue, result)
      return result
    }
    return defaultValue
  }

  @JvmStatic
  fun onPluginUnload(parentDisposable: Disposable, callback: Runnable) {
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        callback.run()
      }
    })
  }
}
