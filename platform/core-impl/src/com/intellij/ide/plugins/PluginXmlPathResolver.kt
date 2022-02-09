// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipFile

@Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
class PluginXmlPathResolver(private val pluginJarFiles: List<Path>) : PathResolver {
  companion object {
    // don't use Kotlin emptyList here
    @JvmField val DEFAULT_PATH_RESOLVER: PathResolver = PluginXmlPathResolver(Collections.emptyList())

    private fun loadUsingZipFile(readInto: RawPluginDescriptor,
                                 readContext: ReadModuleContext,
                                 pathResolver: PathResolver,
                                 dataLoader: DataLoader,
                                 jarFile: Path,
                                 relativePath: String,
                                 includeBase: String?): Boolean {
      val zipFile = ZipFile(jarFile.toFile())
      try {
        // do not use kotlin stdlib here
        val entry = zipFile.getEntry(if (relativePath.startsWith("/")) relativePath.substring(1) else relativePath) ?: return false
        readModuleDescriptor(input = zipFile.getInputStream(entry),
                             readContext = readContext,
                             pathResolver = pathResolver,
                             dataLoader = dataLoader,
                             includeBase = includeBase,
                             readInto = readInto,
                             locationSource = jarFile.toString())
        return true
      }
      catch (e: IOException) {
        Logger.getInstance(PluginXmlPathResolver::class.java).error("Corrupted jar file: $jarFile", e)
        return false
      }
      finally {
        zipFile.close()
      }
    }

    internal fun getParentPath(path: String): String {
      val end = path.lastIndexOf('/')
      return if (end == -1) "" else path.substring(0, end)
    }

    internal fun toLoadPath(relativePath: String, base: String?): String {
      return when {
        relativePath[0] == '/' -> relativePath.substring(1)
        relativePath.startsWith("intellij.") -> relativePath
        base == null -> "META-INF/$relativePath"
        else -> "$base/$relativePath"
      }
    }

    internal fun getChildBase(base: String?, relativePath: String): String? {
      val end = relativePath.lastIndexOf('/')
      if (end <= 0 || relativePath.startsWith("/META-INF/")) {
        return base
      }

      val childBase = relativePath.substring(0, end)
      return if (base == null) childBase else "$base/$childBase"
    }
  }

  override fun loadXIncludeReference(readInto: RawPluginDescriptor,
                                     readContext: ReadModuleContext,
                                     dataLoader: DataLoader,
                                     base: String?,
                                     relativePath: String): Boolean {
    val path = toLoadPath(relativePath, base)
    dataLoader.load(path)?.let {
      readModuleDescriptor(input = it,
                           readContext = readContext,
                           pathResolver = this,
                           dataLoader = dataLoader,
                           includeBase = getChildBase(base = base, relativePath = relativePath),
                           readInto = readInto,
                           locationSource = null)
      return true
    }

    if (findInJarFiles(readInto = readInto,
                       dataLoader = dataLoader,
                       readContext = readContext,
                       relativePath = path,
                       includeBase = getChildBase(base = base, relativePath = relativePath))) {
      return true
    }

    // it is allowed to reference any platform XML file using href="/META-INF/EnforcedPlainText.xml"
    if (path.startsWith("META-INF/")) {
      PluginXmlPathResolver::class.java.classLoader.getResourceAsStream(path)?.let {
        readModuleDescriptor(input = it,
                             readContext = readContext,
                             pathResolver = this,
                             dataLoader = dataLoader,
                             includeBase = null,
                             readInto = readInto,
                             locationSource = null)
        return true
      }
    }
    return false
  }

  override fun resolvePath(readContext: ReadModuleContext,
                           dataLoader: DataLoader,
                           relativePath: String,
                           readInto: RawPluginDescriptor?): RawPluginDescriptor? {
    val path = toLoadPath(relativePath, null)
    dataLoader.load(path)?.let {
      return readModuleDescriptor(input = it,
                                  readContext = readContext,
                                  pathResolver = this,
                                  dataLoader = dataLoader,
                                  includeBase = null,
                                  readInto = readInto,
                                  locationSource = null)
    }

    val result = readInto ?: RawPluginDescriptor()
    if (findInJarFiles(readInto = result, dataLoader = dataLoader, readContext = readContext, relativePath = path, includeBase = null)) {
      return result
    }

    if (relativePath.startsWith("intellij.")) {
      // module in a new file name format must be always resolved
      throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader)")
    }
    return null
  }

  override fun resolveModuleFile(readContext: ReadModuleContext,
                                 dataLoader: DataLoader,
                                 path: String,
                                 readInto: RawPluginDescriptor?): RawPluginDescriptor {
    val input = dataLoader.load(path)
    if (input == null) {
      if (path == "intellij.profiler.clion") {
        val descriptor = RawPluginDescriptor()
        descriptor.`package` = "com.intellij.profiler.clion"
        return descriptor
      }
      throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader, pluginJarFiles=${pluginJarFiles.joinToString(separator = "\n  ")})")
    }
    return readModuleDescriptor(input = input,
                                readContext = readContext,
                                pathResolver = this,
                                dataLoader = dataLoader,
                                includeBase = null,
                                readInto = readInto,
                                locationSource = null)
  }

  private fun findInJarFiles(readInto: RawPluginDescriptor,
                             readContext: ReadModuleContext,
                             dataLoader: DataLoader,
                             relativePath: String,
                             includeBase: String?): Boolean {
    val pool = dataLoader.pool
    for (jarFile in pluginJarFiles) {
      if (dataLoader.isExcludedFromSubSearch(jarFile)) {
        continue
      }

      if (pool == null) {
        if (loadUsingZipFile(readInto = readInto,
                             readContext = readContext,
                             pathResolver = this,
                             dataLoader = dataLoader,
                             jarFile = jarFile,
                             relativePath = relativePath,
                             includeBase = includeBase)) {
          return true
        }
      }
      else {
        val resolver = try {
          pool.load(jarFile)
        }
        catch (e: IOException) {
          Logger.getInstance(PluginXmlPathResolver::class.java).error("Corrupted jar file: $jarFile", e)
          continue
        }

        resolver.loadZipEntry(relativePath)?.let {
          readModuleDescriptor(input = it,
                               readContext = readContext,
                               pathResolver = this,
                               dataLoader = dataLoader,
                               includeBase = includeBase,
                               readInto = readInto,
                               locationSource = jarFile.toString())
          return true
        }
      }
    }
    return false
  }
}