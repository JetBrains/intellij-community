// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.BootstrapBundle
import com.intellij.ide.BytecodeTransformer
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.ide.bootstrap.StartupErrorReporter
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private const val MARKETPLACE_PLUGIN_DIR: @NonNls String = "marketplace"
private const val MARKETPLACE_BOOTSTRAP_JAR = "marketplace-bootstrap.jar"

private fun findMarketplaceBootDir(pluginDir: Path): Path {
  return pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).resolve("lib/boot")
}

private fun isMarketplacePluginCompatible(homePath: Path, pluginDir: Path, mpBoot: Path): Boolean {
  if (Files.notExists(mpBoot)) {
    return false
  }

  try {
    var ideVersion: SimpleVersion? = null
    try {
      Files.newBufferedReader(homePath.resolve("build.txt")).use { reader ->
        ideVersion = parseVersion(reader.readLine())
      }
    }
    catch (ignored: IOException) {
    }
    if (ideVersion == null && SystemInfoRt.isMac) {
      Files.newBufferedReader(homePath.resolve("Resources/build.txt")).use { reader ->
        ideVersion = parseVersion(reader.readLine())
      }
    }
    if (ideVersion != null) {
      var sinceVersion: SimpleVersion? = null
      var untilVersion: SimpleVersion? = null
      try {
        Files.newBufferedReader(pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).resolve("platform-build.txt")).use { reader ->
          sinceVersion = parseVersion(reader.readLine())
          untilVersion = parseVersion(reader.readLine())
        }
      }
      catch (ignored: IOException) {
      }
      return ideVersion!!.isCompatible(sinceVersion, untilVersion)
    }
  }
  catch (ignored: Throwable) {
  }
  return true
}
/**
 * Initializes the marketplace by adding necessary classloaders and resolving required files.
 * If the marketplace is not compatible, or the required files are not found, the method returns without performing any further action.
 *
 * Currently used in Rider.
 */
fun initMarketplace() {
  val distDir = Path.of(PathManager.getHomePath())
  val classLoader = AppMode::class.java.classLoader as? PathClassLoader
                    ?: throw RuntimeException("You must run JVM with -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader")
  val preinstalledPluginDir = distDir.resolve("plugins")

  var pluginDir = preinstalledPluginDir
  var marketPlaceBootDir = findMarketplaceBootDir(pluginDir)
  var mpBoot = marketPlaceBootDir.resolve(MARKETPLACE_BOOTSTRAP_JAR)
  // enough to check for existence as preinstalled plugin is always compatible
  var installMarketplace = Files.exists(mpBoot)

  if (!installMarketplace) {
    pluginDir = Path.of(PathManager.getPluginsPath())
    marketPlaceBootDir = findMarketplaceBootDir(pluginDir)
    mpBoot = marketPlaceBootDir.resolve(MARKETPLACE_BOOTSTRAP_JAR)
    installMarketplace = isMarketplacePluginCompatible(homePath = distDir, pluginDir = pluginDir, mpBoot = mpBoot)

    if (!installMarketplace) {
      return
    }
  }

  val marketplaceImpl = marketPlaceBootDir.resolve("marketplace-impl.jar")
  if (Files.exists(marketplaceImpl)) {
    classLoader.classPath.addFiles(listOf(marketplaceImpl))
  }
  else {
    return
  }

  try {
    val spiLoader = PathClassLoader(UrlClassLoader.build().files(listOf(mpBoot)).parent(classLoader))
    val transformers = ServiceLoader.load(BytecodeTransformer::class.java, spiLoader).iterator()
    if (transformers.hasNext()) {
      classLoader.setTransformer(BytecodeTransformerAdapter(transformers.next()))
    }
  }
  catch (e: Throwable) {
    // at this point, logging is not initialized yet, so reporting the error directly
    val path = pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).toString()
    val message = "As a workaround, you may uninstall or update JetBrains Marketplace Support plugin at $path"
    StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.jetbrains.marketplace.boot.failure"),
                                     Exception(message, e))
  }
}

private class SimpleVersion(private val major: Int, private val minor: Int) : Comparable<SimpleVersion> {
  private fun isAtLeast(ver: Comparable<SimpleVersion>) = ver <= this

  fun isCompatible(since: SimpleVersion?, until: SimpleVersion?): Boolean {
    return when {
      since != null && until != null -> compareTo(since) >= 0 && compareTo(until) <= 0
      since != null -> isAtLeast(since)
      until != null -> until.isAtLeast(this)
      // assume compatible of nothing is specified
      else -> true
    }
  }

  override fun compareTo(other: SimpleVersion): Int {
    return if (major != other.major) major.compareTo(other.major) else minor.compareTo(other.minor)
  }
}

private fun parseVersion(rawText: String?): SimpleVersion? {
  var text = rawText
  if (text.isNullOrEmpty()) {
    return null
  }

  try {
    text = text.trim()
    val dash = text.lastIndexOf('-')
    if (dash >= 0) {
      // strip product code
      text = text.substring(dash + 1)
    }

    val dot = text.indexOf('.')
    if (dot >= 0) {
      return SimpleVersion(major = text.substring(0, dot).toInt(), minor = parseMinor(text.substring(dot + 1)))
    }
    return SimpleVersion(major = text.toInt(), minor = 0)
  }
  catch (ignored: NumberFormatException) {
  }
  return null
}

private fun parseMinor(text: String): Int {
  try {
    if ("*" == text || "SNAPSHOT" == text) {
      return Int.MAX_VALUE
    }

    val dot = text.indexOf('.')
    return (if (dot >= 0) text.substring(0, dot) else text).toInt()
  }
  catch (ignored: NumberFormatException) {
  }
  return 0
}

private class BytecodeTransformerAdapter(private val impl: BytecodeTransformer) : PathClassLoader.BytecodeTransformer {
  override fun isApplicable(className: String, loader: ClassLoader): Boolean {
    return impl.isApplicable(className, loader, null)
  }

  override fun transform(loader: ClassLoader, className: String, classBytes: ByteArray): ByteArray? {
    return impl.transform(loader, className, null, classBytes)
  }
}