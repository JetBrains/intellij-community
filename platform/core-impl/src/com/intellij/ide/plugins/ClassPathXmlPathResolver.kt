// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.pluginSystem.parser.impl.LoadPathUtil
import com.intellij.platform.pluginSystem.parser.impl.LoadedXIncludeReference
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorReaderContext
import com.intellij.platform.pluginSystem.parser.impl.XIncludeLoader
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.parser.impl.isV2ModulePath
import com.intellij.platform.pluginSystem.parser.impl.parsePluginXml
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class ClassPathXmlPathResolver(
  private val classLoader: ClassLoader,
  @JvmField val isRunningFromSourcesWithoutDevBuild: Boolean,
) : PathResolver, XIncludeLoader {
  override val isFlat: Boolean
    get() = true

  override fun loadXIncludeReference(path: String): LoadedXIncludeReference? {
    return LoadedXIncludeReference(inputStream = doLoadXIncludeReference(path) ?: return null, diagnosticReferenceLocation = toString())
  }

  override fun loadXIncludeReference(dataLoader: DataLoader, path: String): LoadedXIncludeReference? {
    return LoadedXIncludeReference(inputStream = doLoadXIncludeReference(path) ?: return null, diagnosticReferenceLocation = dataLoader.toString())
  }

  private fun doLoadXIncludeReference(path: String): ByteArray? {
    if (classLoader is UrlClassLoader) {
      return classLoader.getResourceAsBytes(path, true)
    }
    else {
      return classLoader.getResourceAsStream(path)?.use { it.readBytes() }
    }
  }

  override fun resolveModuleFile(readContext: PluginDescriptorReaderContext, dataLoader: DataLoader, path: String): PluginDescriptorBuilder {
    val resource: ByteArray?
    if (classLoader is UrlClassLoader) {
      resource = classLoader.getResourceAsBytes(path, true)
    }
    else {
      classLoader.getResourceAsStream(path)?.let {
        return parsePluginXml(it, dataLoader.toString(), readContext, createXIncludeLoader(this@ClassPathXmlPathResolver, dataLoader))
      }
      resource = null
    }

    if (resource == null) {
      val log = logger<ClassPathXmlPathResolver>()
      val moduleId = path.removeSuffix(".xml")
      when {
        isRunningFromSourcesWithoutDevBuild && isV2ModulePath(path) && dataLoader.emptyDescriptorIfCannotResolve -> {
          log.trace("Cannot resolve $path (dataLoader=$dataLoader, classLoader=$classLoader). ")
          return PluginDescriptorBuilder.builder().apply {
            visibility = ModuleVisibilityValue.PUBLIC
            `package` = "unresolved.$moduleId"
          }
        }
        else -> {
          throw RuntimeException("Cannot resolve $path (" +
                                 "dataLoader=$dataLoader, " +
                                 "classLoader=$classLoader, " +
                                 "isRunningFromSourcesWithoutDevBuild=$isRunningFromSourcesWithoutDevBuild, " +
                                 "dataLoader.emptyDescriptorIfCannotResolve=${dataLoader.emptyDescriptorIfCannotResolve}, " +
                                 "path.startsWith(\"intellij.\")=${path.startsWith("intellij.")})")
        }
      }
    }

    return parsePluginXml(resource, dataLoader.toString(), readContext, createXIncludeLoader(this@ClassPathXmlPathResolver, dataLoader))
  }

  override fun resolvePath(readContext: PluginDescriptorReaderContext, dataLoader: DataLoader, relativePath: String): PluginDescriptorBuilder? {
    val path = LoadPathUtil.toLoadPath(relativePath)
    val reader = getXmlReader(classLoader = classLoader, path = path, dataLoader = dataLoader) ?: return null
    return parsePluginXml(reader, readContext, createXIncludeLoader(this@ClassPathXmlPathResolver, dataLoader))
  }

  private fun getXmlReader(classLoader: ClassLoader, path: String, dataLoader: DataLoader): XMLStreamReader2? {
    if (classLoader is UrlClassLoader) {
      return createNonCoalescingXmlStreamReader(classLoader.getResourceAsBytes(path, true) ?: return null, dataLoader.toString())
    }
    else {
      return createNonCoalescingXmlStreamReader(classLoader.getResourceAsStream(path) ?: return null, dataLoader.toString())
    }
  }

  override fun toString(): String {
    return "ClassPathXmlPathResolver(" +
           "classLoader=${classLoader.javaClass.simpleName}(files=${(classLoader as? UrlClassLoader)?.files}), " +
           "isRunningFromSourcesWithoutDevBuild=$isRunningFromSourcesWithoutDevBuild" +
           ")"
  }
}