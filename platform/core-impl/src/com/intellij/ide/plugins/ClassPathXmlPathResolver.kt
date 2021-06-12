// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.platform.util.plugins.DataLoader

internal class ClassPathXmlPathResolver(private val classLoader: ClassLoader) : PathResolver {
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