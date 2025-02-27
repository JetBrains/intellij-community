// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.parser.PluginXmlFromXmlStreamBuilder
import com.intellij.ide.plugins.parser.RawPluginDescriptor
import com.intellij.ide.plugins.parser.ReadModuleContext
import com.intellij.ide.plugins.parser.consume
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.lang.ZipEntryResolverPool
import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.io.IOException
import java.nio.file.Path
import java.util.*

@ApiStatus.Internal
class PluginXmlPathResolver(private val pluginJarFiles: List<Path>, private val pool: ZipEntryResolverPool?) : PathResolver {
  companion object {
    // don't use Kotlin emptyList here
    @JvmField
    val DEFAULT_PATH_RESOLVER: PathResolver = PluginXmlPathResolver(pluginJarFiles = Collections.emptyList(), pool = null)

    /**
     * By default, plugin.xml resides in the `/META-INF/` directory, and it serves as a default for `baseDir`
     *
     * `intellij.*` and `kotlin.*` relative paths are treated as references to module XMLs which reside in resource root rather than in `META-INF`.
     *
     * Returned path does _not_ have leading '/' to use it in classloader's `getResource`
     */
    fun toLoadPath(relativePath: String, baseDir: String? = null): String {
      return when {
        relativePath[0] == '/' -> relativePath.substring(1)
        relativePath.startsWith("intellij.")
        // TODO to be removed after KTIJ-29799
        || relativePath.startsWith("kotlin.") -> relativePath
        else -> (baseDir ?: "META-INF") + '/' + relativePath
      }
    }

    // FIXME this thing is bugged when relative path is an absolute path outside /META-INF, probably it has to trim first /
    internal fun getChildBaseDir(base: String?, relativePath: String): String? {
      val end = relativePath.lastIndexOf('/')
      if (end <= 0 || relativePath.startsWith("/META-INF/")) {
        return base
      }

      val childBase = relativePath.substring(0, end)
      return if (base == null) childBase else "$base/$childBase"
    }
  }

  override fun loadXIncludeReference(readInto: RawPluginDescriptor, readContext: ReadModuleContext, dataLoader: DataLoader, base: String?, relativePath: String): Boolean {
    val path = toLoadPath(relativePath, base)
    try {
      dataLoader.load(path, pluginDescriptorSourceOnly = false)?.let { input ->
        PluginXmlFromXmlStreamBuilder(readContext, dataLoader, this, getChildBaseDir(base = base, relativePath = relativePath), readInto).let {
          it.consume(input, null)
          it.getRawPluginDescriptor()
        }
        return true
      }

      if (pool != null && findInJarFiles(
          readInto = readInto,
          dataLoader = dataLoader,
          readContext = readContext,
          relativePath = path,
          includeBase = getChildBaseDir(base = base, relativePath = relativePath),
          pool = pool,
        )) {
        return true
      }

      // it is allowed to reference any platform XML file using href="/META-INF/EnforcedPlainText.xml"
      if (path.startsWith("META-INF/")) {
        PluginXmlPathResolver::class.java.classLoader.getResourceAsStream(path)?.let { input ->
          PluginXmlFromXmlStreamBuilder(readContext, dataLoader, this, null, readInto).let {
            it.consume(input, null)
            it.getRawPluginDescriptor()
          }
          return true
        }
      }
    }
    catch (e: Throwable) {
      throw IOException("Exception ${e.message} while loading $path", e)
    }
    return false
  }

  override fun resolvePath(readContext: ReadModuleContext, dataLoader: DataLoader, relativePath: String, readInto: RawPluginDescriptor?): RawPluginDescriptor? {
    val path = toLoadPath(relativePath)
    dataLoader.load(path, pluginDescriptorSourceOnly = false)?.let { input ->
      return PluginXmlFromXmlStreamBuilder(readContext, dataLoader, this, null, readInto).let {
        it.consume(input, null)
        it.getRawPluginDescriptor()
      }
    }

    val result = readInto ?: RawPluginDescriptor()
    if (pool != null && findInJarFiles(readInto = result, dataLoader = dataLoader, readContext = readContext, relativePath = path, includeBase = null, pool = pool)) {
      return result
    }

    if (relativePath.startsWith("intellij.")) {
      // module in a new file name format must always be resolved
      throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader)")
    }
    return null
  }

  override fun resolveModuleFile(
    readContext: ReadModuleContext,
    dataLoader: DataLoader,
    path: String,
    readInto: RawPluginDescriptor?,
  ): RawPluginDescriptor {
    val input = dataLoader.load(path, pluginDescriptorSourceOnly = true)
    if (input == null) {
      if (path == "intellij.profiler.clion") {
        val descriptor = RawPluginDescriptor()
        descriptor.`package` = "com.intellij.profiler.clion"
        return descriptor
      }
      throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader, pluginJarFiles=${pluginJarFiles.joinToString(separator = "\n  ")})")
    }

    val descriptor = PluginXmlFromXmlStreamBuilder(readContext, dataLoader, this, null, readInto).let {
      it.consume(input, null)
      it.getRawPluginDescriptor()
    }
    return descriptor
  }

  private fun findInJarFiles(
    readInto: RawPluginDescriptor,
    readContext: ReadModuleContext,
    dataLoader: DataLoader,
    pool: ZipEntryResolverPool,
    relativePath: String,
    includeBase: String?,
  ): Boolean {
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

      val result = resolver.loadZipEntry(relativePath)?.let { input ->
        PluginXmlFromXmlStreamBuilder(readContext, dataLoader, this, includeBase, readInto).let {
          it.consume(input, jarFile.toString())
          it.getRawPluginDescriptor()
        }
      }

      (resolver as? Closeable)?.close()
      if (result != null) {
        return true
      }
    }
    return false
  }
}