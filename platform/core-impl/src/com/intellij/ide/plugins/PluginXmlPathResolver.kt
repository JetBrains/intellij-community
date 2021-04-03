// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SafeJdomFactory
import org.jdom.Element
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipFile

@Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
class PluginXmlPathResolver(private val pluginJarFiles: List<Path>) : PathResolver {
  companion object {
    @JvmField
    // don't use Kotlin emptyList here
    val DEFAULT_PATH_RESOLVER: PathResolver = PluginXmlPathResolver(Collections.emptyList())

    @JvmStatic
    private fun loadUsingZipFile(jarFile: Path, relativePath: String, jdomFactory: SafeJdomFactory): Element? {
      val zipFile = ZipFile(jarFile.toFile())
      try {
        // do not use kotlin stdlib here
        val entry = zipFile.getEntry(if (relativePath.startsWith("/")) relativePath.substring(1) else relativePath) ?: return null
        return JDOMUtil.load(zipFile.getInputStream(entry), jdomFactory)
      }
      catch (e: IOException) {
        Logger.getInstance(PluginXmlPathResolver::class.java).error("Corrupted jar file: $jarFile", e)
        return null
      }
      finally {
        zipFile.close()
      }
    }

    @JvmStatic
    fun getParentPath(path: String): String {
      val end = path.lastIndexOf('/')
      return if (end == -1) "" else path.substring(0, end)
    }

    @JvmStatic
    fun toLoadPath(relativePath: String, base: String?): String {
      return when {
        relativePath[0] == '/' -> relativePath.substring(1)
        relativePath.startsWith("intellij.") -> relativePath
        base == null -> "META-INF/$relativePath"
        else -> "$base/$relativePath"
      }
    }
  }

  override fun loadXIncludeReference(dataLoader: DataLoader,
                                     base: String?,
                                     relativePath: String,
                                     jdomFactory: SafeJdomFactory): Element? {
    val path = toLoadPath(relativePath, base)
    dataLoader.load(path)?.let {
      return JDOMUtil.load(it, jdomFactory)
    }

    findInJarFiles(dataLoader, path, jdomFactory)?.let {
      return it
    }

    // it is allowed to reference any platform XML file using href="/META-INF/EnforcedPlainText.xml"
    if (path.startsWith("META-INF/")) {
      PluginXmlPathResolver::class.java.classLoader.getResourceAsStream(path)?.let {
        return JDOMUtil.load(it)
      }
    }

    return null
  }

  override fun resolvePath(dataLoader: DataLoader, relativePath: String, jdomFactory: SafeJdomFactory): Element? {
    val path = toLoadPath(relativePath, null)
    dataLoader.load(path)?.let {
      return JDOMUtil.load(it)
    }

    findInJarFiles(dataLoader, path, jdomFactory)?.let {
      return it
    }

    if (relativePath.startsWith("intellij.")) {
      // module in a new file name format must be always resolved
      throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader)")
    }
    else {
      return null
    }
  }

  private fun findInJarFiles(dataLoader: DataLoader, relativePath: String, jdomFactory: SafeJdomFactory): Element? {
    val pool = dataLoader.pool
    for (jarFile in pluginJarFiles) {
      if (dataLoader.isExcludedFromSubSearch(jarFile)) {
        continue
      }

      if (pool == null) {
        loadUsingZipFile(jarFile, relativePath, jdomFactory)?.let {
          return it
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
          return JDOMUtil.load(it, jdomFactory)
        }
      }
    }
    return null
  }
}