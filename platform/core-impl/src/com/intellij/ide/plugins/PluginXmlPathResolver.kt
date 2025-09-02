// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.plugins.parser.impl.LoadPathUtil
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.PluginDescriptorReaderContext
import com.intellij.platform.plugins.parser.impl.XIncludeLoader
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.util.lang.ZipEntryResolverPool
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.*

@ApiStatus.Internal
class PluginXmlPathResolver(private val pluginJarFiles: List<Path>, private val pool: ZipEntryResolverPool?) : PathResolver {
  companion object {
    // don't use Kotlin emptyList here
    @JvmField
    val DEFAULT_PATH_RESOLVER: PathResolver = PluginXmlPathResolver(pluginJarFiles = Collections.emptyList(), pool = null)
  }

  override fun loadXIncludeReference(dataLoader: DataLoader, path: String): XIncludeLoader.LoadedXIncludeReference? {
    try {
      dataLoader.load(path, pluginDescriptorSourceOnly = false)?.let { input ->
        return XIncludeLoader.LoadedXIncludeReference(input, null)
      }
      if (pool != null) {
        val fromJar = findInJarFiles(
          dataLoader = dataLoader,
          relativePath = path,
          pool = pool,
        )
        if (fromJar != null) {
          return XIncludeLoader.LoadedXIncludeReference(fromJar.inputStream, fromJar.diagnosticLocation)
        }
      }
      // it is allowed to reference any platform XML file using href="/META-INF/EnforcedPlainText.xml"
      if (path.startsWith("META-INF/")) {
        PluginXmlPathResolver::class.java.classLoader.getResourceAsStream(path)?.let { input ->
          return XIncludeLoader.LoadedXIncludeReference(input, null)
        }
      }
    }
    catch (e: Throwable) {
      throw IOException("Exception ${e.message} while loading $path", e)
    }
    return null
  }

  override fun resolvePath(readContext: PluginDescriptorReaderContext, dataLoader: DataLoader, relativePath: String): PluginDescriptorBuilder? {
    val path = LoadPathUtil.toLoadPath(relativePath)
    dataLoader.load(path, pluginDescriptorSourceOnly = false)?.let { input ->
      return PluginDescriptorFromXmlStreamConsumer(readContext, toXIncludeLoader(dataLoader)).let {
        it.consume(input, null)
        it.getBuilder()
      }
    }

    if (pool != null) {
      val fromJar = findInJarFiles(dataLoader = dataLoader, relativePath = path, pool = pool)
      if (fromJar != null) {
        return fromJar.inputStream.let { input ->
          PluginDescriptorFromXmlStreamConsumer(readContext, toXIncludeLoader(dataLoader)).let {
            it.consume(input, null)
            it.getBuilder()
          }
        }
      }
    }

    if (relativePath.startsWith("intellij.")) {
      // module in a new file name format must always be resolved
      throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader)")
    }
    return null
  }

  override fun resolveModuleFile(
    readContext: PluginDescriptorReaderContext,
    dataLoader: DataLoader,
    path: String,
  ): PluginDescriptorBuilder {
    val input = dataLoader.load(path, pluginDescriptorSourceOnly = true)
    if (input == null) {
      if (path == "intellij.profiler.clion") {
        return PluginDescriptorBuilder.builder().apply {
          `package` = "com.intellij.profiler.clion"
        }
      }
      throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader, pluginJarFiles=${pluginJarFiles.joinToString(separator = "\n  ")})")
    }

    val builder = PluginDescriptorFromXmlStreamConsumer(readContext, toXIncludeLoader(dataLoader)).let {
      it.consume(input, null)
      it.getBuilder()
    }
    return builder
  }

  private fun findInJarFiles(
    dataLoader: DataLoader,
    pool: ZipEntryResolverPool,
    relativePath: String,
  ): ResolvedFromJar? {
    for (jarFile in pluginJarFiles) {
      if (dataLoader.isExcludedFromSubSearch(jarFile)) {
        continue
      }
      val resolver = try {
        pool.load(jarFile)
      }
      catch (e: IOException) {
        Logger.getInstance(PluginXmlPathResolver::class.java).error("Corrupted jar file: $jarFile", e)
        continue
      }
      val result = resolver.loadZipEntry(relativePath) // do not close, resource must be freed together with resolver/dataLoader
      if (result != null) {
        return ResolvedFromJar(result, jarFile.toString())
      }
    }
    return null
  }

  private class ResolvedFromJar(val inputStream: InputStream, val diagnosticLocation: String?)
}