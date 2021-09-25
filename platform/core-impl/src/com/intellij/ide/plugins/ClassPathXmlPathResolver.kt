// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.platform.util.plugins.DataLoader
import com.intellij.platform.util.plugins.LocalFsDataLoader
import java.nio.file.Files
import java.nio.file.NoSuchFileException

internal class ClassPathXmlPathResolver(private val classLoader: ClassLoader, private val isRunningFromSources: Boolean) : PathResolver {
  override val isFlat: Boolean
    get() = true

  override fun loadXIncludeReference(readInto: RawPluginDescriptor,
                                     readContext: ReadModuleContext,
                                     dataLoader: DataLoader,
                                     base: String?,
                                     relativePath: String): Boolean {
    val path = PluginXmlPathResolver.toLoadPath(relativePath, base)
    readModuleDescriptor(inputStream = classLoader.getResourceAsStream(path) ?: return false,
                         readContext = readContext,
                         pathResolver = this,
                         dataLoader = dataLoader,
                         includeBase = PluginXmlPathResolver.getChildBase(base = base, relativePath = relativePath),
                         readInto = readInto,
                         locationSource = dataLoader.toString())
    return true
  }

  override fun resolveModuleFile(readContext: ReadModuleContext,
                                 dataLoader: DataLoader,
                                 path: String,
                                 readInto: RawPluginDescriptor?): RawPluginDescriptor {
    var resource = classLoader.getResourceAsStream(path)
    if (resource == null) {
      // todo (deal with different plugin content for ultimate and community)
      if (path == "intellij.profiler.ultimate.xml") {
        val descriptor = RawPluginDescriptor()
        descriptor.`package` = "com.intellij.profiler.ultimate"
        return descriptor
      }
      if (isRunningFromSources && path.startsWith("intellij.") && dataLoader is LocalFsDataLoader) {
        try {
          resource = Files.newInputStream(dataLoader.basePath.parent.resolve("${path.substring(0, path.length - 4)}/$path"))
        }
        catch (e: NoSuchFileException) {
        }
      }

      if (resource == null) {
        throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader, classLoader=$classLoader)")
      }
    }
    return readModuleDescriptor(inputStream = resource,
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
    val resource = classLoader.getResourceAsStream(path)
    return readModuleDescriptor(inputStream = resource ?: return null,
                                readContext = readContext,
                                pathResolver = this,
                                dataLoader = dataLoader,
                                includeBase = null,
                                readInto = readInto,
                                locationSource = dataLoader.toString())
  }
}