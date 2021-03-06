// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.WeakList

private val LOG = logger<ClassLoaderTreeChecker>()

internal class ClassLoaderTreeChecker(private val unloadedMainDescriptor: IdeaPluginDescriptorImpl,
                                      private val classLoaders: WeakList<PluginClassLoader>) {
  fun checkThatClassLoaderNotReferencedByPluginClassLoader() {
    if (!ClassLoaderConfigurationData.SEPARATE_CLASSLOADER_FOR_SUB || unloadedMainDescriptor.classLoader !is PluginClassLoader) {
      return
    }

    PluginManagerCore.getLoadedPlugins(null).forEach(this::checkThatClassLoaderNotReferencedByPluginClassLoader)
  }

  private fun checkThatClassLoaderNotReferencedByPluginClassLoader(descriptor: IdeaPluginDescriptorImpl) {
    checkThatClassloaderNotReferenced(descriptor)
    for (dependency in (descriptor.pluginDependencies ?: return)) {
      checkThatClassLoaderNotReferencedByPluginClassLoader(dependency.subDescriptor ?: continue)
    }
  }

  private fun checkThatClassloaderNotReferenced(descriptor: IdeaPluginDescriptorImpl) {
    val classLoader = descriptor.classLoader as? PluginClassLoader ?: return
    if (descriptor !== unloadedMainDescriptor) {
      // unrealistic case, but who knows
      if (classLoaders.contains(classLoader)) {
        LOG.error("$classLoader must be unloaded but still referenced")
      }

      if (classLoader.pluginId === unloadedMainDescriptor.pluginId && classLoader.pluginDescriptor === descriptor) {
        LOG.error("Classloader of $descriptor must be nullified")
      }
    }

    val parents = classLoader._getParents()

    for (unloadedClassLoader in classLoaders) {
      if (parents.contains(unloadedClassLoader)) {
        LOG.error("$classLoader references via parents $unloadedClassLoader that must be unloaded")
      }
    }

    for (parent in parents) {
      if (parent is PluginClassLoader && parent.pluginId === unloadedMainDescriptor.pluginId) {
        LOG.error("$classLoader references via parents $parent that must be unloaded")
      }
    }
  }
}