// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.BootstrapBundle
import com.intellij.ide.BytecodeTransformer
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.platform.ide.bootstrap.StartupErrorReporter
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.system.OS
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private const val MARKETPLACE_PLUGIN_DIR: String = "marketplace"
private const val MARKETPLACE_BOOTSTRAP_JAR: String = "marketplace-bootstrap.jar"

private fun findMarketplaceBootDir(pluginDir: Path): Path =
  pluginDir.resolve(MARKETPLACE_PLUGIN_DIR).resolve("lib/boot")

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
    catch (_: IOException) { }
    if (ideVersion == null && OS.CURRENT == OS.macOS) {
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
      catch (_: IOException) { }
      @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
      return ideVersion!!.isCompatible(sinceVersion, untilVersion)
    }
  }
  catch (_: Throwable) { }
  return true
}

/**
 * Initializes the marketplace by adding necessary classloaders and resolving required files.
 * If the marketplace is not compatible, or the required files are not found, the method returns without performing any further action.
 *
 * Currently used in Rider.
 */
internal fun initMarketplace() {
  val distDir = Path.of(PathManager.getHomePath())
  val preinstalledPluginDir = distDir.resolve("plugins")

  var pluginDir = preinstalledPluginDir
  var marketPlaceBootDir = findMarketplaceBootDir(pluginDir)
  var mpBoot = marketPlaceBootDir.resolve(MARKETPLACE_BOOTSTRAP_JAR)
  // enough to check for existence, as a preinstalled plugin is always compatible
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
  val classLoader = AppMode::class.java.classLoader as? PathClassLoader
                    ?: throw RuntimeException("You must run JVM with -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader")
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
    val message = BootstrapBundle.message("bootstrap.error.message.marketplace", path)
    StartupErrorReporter.showError(BootstrapBundle.message("bootstrap.error.title.marketplace"), Exception(message, e))
  }
}

private class SimpleVersion(private val major: Int, private val minor: Int) : Comparable<SimpleVersion> {
  fun isCompatible(since: SimpleVersion?, until: SimpleVersion?): Boolean =
    (since == null || this >= since) && (until == null || this <= until)  // assume compatible when no bounds are specified

  override fun compareTo(other: SimpleVersion): Int =
    if (major != other.major) major.compareTo(other.major) else minor.compareTo(other.minor)

  override fun toString(): String = "${major}/${minor}"
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
      return SimpleVersion(major = text.take(dot).toInt(), minor = parseMinor(text.substring(dot + 1)))
    }
    return SimpleVersion(major = text.toInt(), minor = 0)
  }
  catch (_: NumberFormatException) { }
  return null
}

private fun parseMinor(text: String): Int {
  try {
    if ("*" == text || "SNAPSHOT" == text) {
      return Int.MAX_VALUE
    }

    val dot = text.indexOf('.')
    return (if (dot >= 0) text.take(dot) else text).toInt()
  }
  catch (_: NumberFormatException) { }
  return 0
}

private class BytecodeTransformerAdapter(private val impl: BytecodeTransformer) : PathClassLoader.BytecodeTransformer {
  override fun isApplicable(className: String, loader: ClassLoader): Boolean =
    impl.isApplicable(className, loader, null)

  override fun transform(loader: ClassLoader, className: String, classBytes: ByteArray): ByteArray? =
    impl.transform(loader, className, null, classBytes)
}
