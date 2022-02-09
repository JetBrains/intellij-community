// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.createNonCoalescingXmlStreamReader
import com.intellij.util.lang.UrlClassLoader
import org.codehaus.stax2.XMLStreamReader2

internal class ClassPathXmlPathResolver(private val classLoader: ClassLoader, val isRunningFromSources: Boolean) : PathResolver {
  override val isFlat: Boolean
    get() = true

  override fun loadXIncludeReference(readInto: RawPluginDescriptor,
                                     readContext: ReadModuleContext,
                                     dataLoader: DataLoader,
                                     base: String?,
                                     relativePath: String): Boolean {
    val path = PluginXmlPathResolver.toLoadPath(relativePath, base)
    val reader: XMLStreamReader2
    if (classLoader is UrlClassLoader) {
      reader = createNonCoalescingXmlStreamReader(classLoader.getResourceAsBytes(path, true) ?: return false, dataLoader.toString())
    }
    else {
      reader = createNonCoalescingXmlStreamReader(classLoader.getResourceAsStream(path) ?: return false, dataLoader.toString())
    }
    readModuleDescriptor(reader = reader,
                         readContext = readContext,
                         pathResolver = this,
                         dataLoader = dataLoader,
                         includeBase = PluginXmlPathResolver.getChildBase(base = base, relativePath = relativePath),
                         readInto = readInto)
    return true
  }

  override fun resolveModuleFile(readContext: ReadModuleContext,
                                 dataLoader: DataLoader,
                                 path: String,
                                 readInto: RawPluginDescriptor?): RawPluginDescriptor {
    var resource: ByteArray?
    if (classLoader is UrlClassLoader) {
      resource = classLoader.getResourceAsBytes(path, true)
    }
    else {
      classLoader.getResourceAsStream(path)?.let {
        return readModuleDescriptor(input = it,
                                    readContext = readContext,
                                    pathResolver = this,
                                    dataLoader = dataLoader,
                                    includeBase = null,
                                    readInto = readInto,
                                    locationSource = dataLoader.toString())
      }
      resource = null
    }

    if (resource == null) {
      if (path == "intellij.profiler.clion") {
        val descriptor = RawPluginDescriptor()
        descriptor.`package` = "com.intellij.profiler.clion"
        return descriptor
      }

      if (isRunningFromSources && path.startsWith("intellij.") && dataLoader.emptyDescriptorIfCannotResolve) {
        Logger.getInstance(ClassPathXmlPathResolver::class.java).warn(
          "Cannot resolve $path (dataLoader=$dataLoader, classLoader=$classLoader). " +
          "Please ensure that project is built (Build -> Build Project)."
        )
        val descriptor = RawPluginDescriptor()
        descriptor.`package` = "unresolved.${path.removeSuffix(".xml")}"
        return descriptor
      }

      throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader, classLoader=$classLoader)")
    }

    return readModuleDescriptor(input = resource,
                                readContext = readContext,
                                pathResolver = this,
                                dataLoader = dataLoader,
                                includeBase = null,
                                readInto = readInto,
                                locationSource = dataLoader.toString())
  }

  override fun resolvePath(readContext: ReadModuleContext,
                           dataLoader: DataLoader,
                           relativePath: String,
                           readInto: RawPluginDescriptor?): RawPluginDescriptor? {
    val path = PluginXmlPathResolver.toLoadPath(relativePath, null)
    return readModuleDescriptor(getXmlReader(classLoader, path, dataLoader) ?: return null,
                                readContext = readContext,
                                pathResolver = this,
                                dataLoader = dataLoader,
                                includeBase = null,
                                readInto = readInto)
  }

  private fun getXmlReader(classLoader: ClassLoader, path: String, dataLoader: DataLoader): XMLStreamReader2? {
    if (classLoader is UrlClassLoader) {
      return createNonCoalescingXmlStreamReader(classLoader.getResourceAsBytes(path, true) ?: return null, dataLoader.toString())
    }
    else {
      return createNonCoalescingXmlStreamReader(classLoader.getResourceAsStream(path) ?: return null, dataLoader.toString())
    }
  }
}