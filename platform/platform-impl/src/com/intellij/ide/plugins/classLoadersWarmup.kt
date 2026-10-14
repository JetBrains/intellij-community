// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.util.coroutines.forEachConcurrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private val CLASS_ATTRIBUTES = arrayOf("implementationClass", "class", "instance", "factoryClass")

internal suspend fun warmupClassLoaders(loadedGroups: List<RuntimeModuleGroup>) {
  // TODO currently, ClassLoaderConfigurator sets the classloader instances right into the plugin descriptors.
  //   This does not allow us to do a warmup concurrently with the unloading, because some descriptors may need reloading, so they don't have
  //   the right classloader yet. If ClassLoaderConfigurator instead returns a mapping Descriptor -> ClassLoader, we could do a warmup concurrently with the unloading.
  val classes = ArrayList<Pair<ClassLoader, String>>(1024)
  for (group in loadedGroups) {
    for (descriptor in group.sortedDescriptors) {
      val classloader = descriptor.pluginClassLoader ?: run {
        fileLogger().error("$descriptor supposed to have a classloader by now")
        continue
      }
      scheduleExtensionsLoading(descriptor, classloader, classes)
    }
  }
  if (classes.isEmpty()) {
    return
  }
  withContext(Dispatchers.Default) {
    classes.forEachConcurrent { (loader, className) ->
      try {
        Class.forName(className, false, loader).declaredConstructors
      }
      catch (e: Throwable) {
        currentCoroutineContext().ensureActive()
        fileLogger().debug { "Cannot pre-load $className with $loader: $e" }
      }
    }
  }
}

private fun scheduleExtensionsLoading(
  descriptor: IdeaPluginDescriptorImpl,
  classloader: ClassLoader,
  classes: ArrayList<Pair<ClassLoader, String>>,
) {
  for (extensionsForEP in descriptor.extensions.values) {
    for (extension in extensionsForEP) {
      extension.implementation?.let { classes.add(classloader to it) }
      for (attribute in CLASS_ATTRIBUTES) {
        val className = extension.element?.attributes?.get(attribute) ?: continue
        if (className.indexOf('.') > 0 && ' ' !in className) {
          classes.add(classloader to className)
        }
      }
    }
  }
}
