// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SafeJdomFactory
import org.jdom.Element

internal class ClassPathXmlPathResolver(private val classLoader: ClassLoader) : PathResolver {
  override val isFlat: Boolean
    get() = true

  override fun loadXIncludeReference(dataLoader: DataLoader,
                                     base: String?,
                                     relativePath: String,
                                     jdomFactory: SafeJdomFactory): Element? {
    val path = PluginXmlPathResolver.toLoadPath(relativePath, base)
    return JDOMUtil.load(classLoader.getResourceAsStream(path) ?: return null, jdomFactory)
  }

  override fun resolvePath(dataLoader: DataLoader, relativePath: String, jdomFactory: SafeJdomFactory): Element? {
    val path = PluginXmlPathResolver.toLoadPath(relativePath, null)
    return JDOMUtil.load(classLoader.getResourceAsStream(path) ?: return null, jdomFactory)
  }
}