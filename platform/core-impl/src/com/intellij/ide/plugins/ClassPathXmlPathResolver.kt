// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.plugins.parser.impl.LoadPathUtil
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.ReadModuleContext
import com.intellij.platform.plugins.parser.impl.XIncludeLoader
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.ByteArrayInputStream
import java.io.InputStream

@Internal
class ClassPathXmlPathResolver(
  private val classLoader: ClassLoader,
  @JvmField val isRunningFromSources: Boolean,
) : PathResolver {
  override val isFlat: Boolean
    get() = true

  override fun loadXIncludeReference(dataLoader: DataLoader, path: String): XIncludeLoader.LoadedXIncludeReference? {
    val input: InputStream?
    if (classLoader is UrlClassLoader) {
      input = classLoader.getResourceAsBytes(path, true)?.let(::ByteArrayInputStream)
    }
    else {
      input = classLoader.getResourceAsStream(path)
    }
    if (input == null) {
      return null
    }
    return XIncludeLoader.LoadedXIncludeReference(input, dataLoader.toString())
  }

  override fun resolveModuleFile(readContext: ReadModuleContext, dataLoader: DataLoader, path: String): PluginDescriptorBuilder {
    val resource: ByteArray?
    if (classLoader is UrlClassLoader) {
      resource = classLoader.getResourceAsBytes(path, true)
    }
    else {
      classLoader.getResourceAsStream(path)?.let {
        val reader = PluginDescriptorFromXmlStreamConsumer(readContext, toXIncludeLoader(dataLoader))
        reader.consume(it, dataLoader.toString())
        return reader.getBuilder()
      }
      resource = null
    }

    if (resource == null) {
      val log = logger<ClassPathXmlPathResolver>()
      val moduleName = path.removeSuffix(".xml")
      when {
        isRunningFromSources && path.startsWith("intellij.") && dataLoader.emptyDescriptorIfCannotResolve -> {
          log.trace("Cannot resolve $path (dataLoader=$dataLoader, classLoader=$classLoader). ")
          return PluginDescriptorBuilder.builder().apply {
            `package` = "unresolved.$moduleName"
          }
        }
        ProductLoadingStrategy.strategy.isOptionalProductModule(moduleName) -> {
          // this check won't be needed when we are able to load optional modules directly from product-modules.xml
          log.debug { "Skip module '$path' since its descriptor cannot be found and it's optional" }
          return PluginDescriptorBuilder.builder().apply {
            `package` = "unresolved.$moduleName"
          }
        }
        else -> {
          throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader, classLoader=$classLoader)")
        }
      }
    }

    return PluginDescriptorFromXmlStreamConsumer(readContext, toXIncludeLoader(dataLoader)).let {
      it.consume(resource, dataLoader.toString())
      it.getBuilder()
    }
  }

  override fun resolvePath(readContext: ReadModuleContext, dataLoader: DataLoader, relativePath: String): PluginDescriptorBuilder? {
    val path = LoadPathUtil.toLoadPath(relativePath)
    val reader = getXmlReader(classLoader = classLoader, path = path, dataLoader = dataLoader) ?: return null
    return PluginDescriptorFromXmlStreamConsumer(readContext, toXIncludeLoader(dataLoader)).let {
      it.consume(reader)
      it.getBuilder()
    }
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