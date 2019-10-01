// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ReflectionUtil
import com.intellij.util.messages.Topic

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

  @JvmStatic
  fun isUnloadSafe(pluginDescriptor: IdeaPluginDescriptor): Boolean {
    if (!ApplicationManager.getApplication().isInternal) return false

    if (pluginDescriptor !is IdeaPluginDescriptorImpl) return false

    val anyProject = ProjectManager.getInstance().openProjects.firstOrNull() ?:
                     ProjectManager.getInstance().defaultProject

    val extensions = pluginDescriptor.extensions
    if (extensions != null) {
      for (epName in extensions.keys) {
        val ep = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName) ?:
          anyProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
        if (ep == null || !ep.isDynamic) {
          LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because of extension $epName")
          return false
        }
      }
    }

    return isUnloadSafe(pluginDescriptor.appContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.projectContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.moduleContainerDescriptor) &&
           (ActionManager.getInstance() as ActionManagerImpl).canUnloadActions(pluginDescriptor)
  }

  private fun isUnloadSafe(containerDescriptor: ContainerDescriptor): Boolean {
    return containerDescriptor.components.isNullOrEmpty()
  }

  @JvmStatic
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
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
      }
    }

    return loadedPluginDescriptor.unloadClassLoader()
  }

  @JvmStatic
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl) {
    val coreLoader = ReflectionUtil.findCallerClass(1)!!.classLoader
    val pluginsWithNewPlugin = (PluginManagerCore.getPlugins().filterIsInstance<IdeaPluginDescriptorImpl>() + listOf(pluginDescriptor)).toTypedArray()
    PluginManagerCore.initClassLoader(pluginDescriptor, coreLoader, PluginManagerCore.pluginIdTraverser(pluginsWithNewPlugin))

    val application = ApplicationManager.getApplication() as ApplicationImpl
    application.runWriteAction {
      application.registerComponents(listOf(pluginDescriptor))
      for (openProject in ProjectManager.getInstance().openProjects) {
        (openProject as ProjectImpl).registerComponents(listOf(pluginDescriptor))
      }
      (ActionManager.getInstance() as ActionManagerImpl).registerPluginActions(pluginDescriptor)
    }

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
