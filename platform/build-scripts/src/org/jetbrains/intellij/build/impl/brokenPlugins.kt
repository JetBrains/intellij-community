// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.downloadAsText
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*

private const val MARKETPLACE_BROKEN_PLUGINS_URL = "https://plugins.jetbrains.com/files/brokenPlugins.json"

/**
 * Generate the 'brokenPlugins.txt' file using JetBrains Marketplace.
 */
suspend fun buildBrokenPlugins(currentBuildString: String, isInDevelopmentMode: Boolean): ByteArray? {
  val span = Span.current()

  val allBrokenPlugins = try {
    val content = downloadAsText(MARKETPLACE_BROKEN_PLUGINS_URL)
    @Suppress("JSON_FORMAT_REDUNDANT")
    Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(MarketplaceBrokenPlugin.serializer()), content)
  }
  catch (e: Exception) {
    if (isInDevelopmentMode) {
      span.recordException(RuntimeException(
        "Not able to get broken plugins info from JetBrains Marketplace. Assuming empty broken plugins list",
        e
      ))
      return null
    }
    else {
      throw e
    }
  }

  val currentBuild = BuildNumber.fromString(currentBuildString, currentBuildString)!!
  val brokenPlugins = TreeMap<String, MutableSet<String>>()
  for (plugin in allBrokenPlugins) {
    val originalUntil = BuildNumber.fromString(plugin.originalUntil, currentBuildString) ?: currentBuild
    val originalSince = BuildNumber.fromString(plugin.originalSince, currentBuildString) ?: currentBuild
    val until = BuildNumber.fromString(plugin.until, currentBuildString) ?: currentBuild
    val since = BuildNumber.fromString(plugin.since, currentBuildString) ?: currentBuild
    if ((currentBuild in originalSince..originalUntil) && (currentBuild > until || currentBuild < since)) {
      brokenPlugins.computeIfAbsent(plugin.id) { TreeSet<String>() }.add(plugin.version)
    }
  }

  span.setAttribute("pluginCount", brokenPlugins.size.toLong())
  val byteOut = ByteArrayOutputStream()
  DataOutputStream(byteOut).use { out ->
    out.write(2)
    out.writeUTF(currentBuildString)
    out.writeInt(brokenPlugins.size)
    for (entry in brokenPlugins.entries) {
      out.writeUTF(entry.key)
      out.writeShort(entry.value.size)
      entry.value.forEach(out::writeUTF)
    }
  }
  return byteOut.toByteArray()
}

@Serializable
private data class MarketplaceBrokenPlugin(
  @JvmField var id: String,
  @JvmField var version: String,
  @JvmField var until: String?,
  @JvmField var since: String?,
  @JvmField var originalSince: String?,
  @JvmField var originalUntil: String?
)

private class BuildNumber(private val productCode: String, private val components: IntArray) : Comparable<BuildNumber> {
  companion object {
    private const val STAR = "*"
    private const val SNAPSHOT = "SNAPSHOT"
    private const val SNAPSHOT_VALUE = Integer.MAX_VALUE

    fun fromString(versionString: String?, current: String): BuildNumber? {
      val version = versionString?.trim { it <= ' ' }?.takeIf { it.isNotEmpty() } ?: return null
      var code = version
      val productSeparator = code.indexOf('-')
      val productCode: String
      if (productSeparator > 0) {
        productCode = code.substring(0, productSeparator)
        code = code.substring(productSeparator + 1)
      }
      else {
        productCode = ""
      }

      if (SNAPSHOT === code || isPlaceholder(code)) {
        return BuildNumber(productCode, fromString(current, current)!!.components)
      }

      val baselineVersionSeparator = code.indexOf('.')
      if (baselineVersionSeparator > 0) {
        val baselineVersionString = code.substring(0, baselineVersionSeparator)
        if (baselineVersionString.trim { it <= ' ' }.isEmpty()) {
          return null
        }

        val stringComponents = code.split(("\\.").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var intComponentsList = IntArray(stringComponents.size)
        val n = stringComponents.size
        for (i in 0 until n) {
          val stringComponent = stringComponents[i]
          val comp = parseBuildNumber(version, stringComponent)
          intComponentsList[i] = comp
          if (comp == SNAPSHOT_VALUE && (i + 1) != n) {
            intComponentsList = intComponentsList.copyOf(i + 1)
            break
          }
        }
        return BuildNumber(productCode, intComponentsList)
      }
      else {
        val buildNumber = parseBuildNumber(version, code)
        if (buildNumber <= 2000) {
          // it's probably a baseline, not a build number
          return BuildNumber(productCode, intArrayOf(buildNumber, 0))
        }

        val baselineVersion = getBaseLineForHistoricBuilds(buildNumber)
        return BuildNumber(productCode, intArrayOf(baselineVersion, buildNumber))
      }
    }

    private fun isPlaceholder(value: String) = "__BUILD_NUMBER__" == value || "__BUILD__" == value

    private fun parseBuildNumber(version: String, code: String): Int =
      if (SNAPSHOT == code || isPlaceholder(code) || STAR == code) SNAPSHOT_VALUE
      else code.toIntOrNull() ?: throw RuntimeException("Invalid version number: $version")

    // https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
    private fun getBaseLineForHistoricBuilds(bn: Int): Int = when {
      bn >= 10000 -> 88 // Maia, 9x builds
      bn >= 9500 -> 85 // 8.1 builds
      bn >= 9100 -> 81 // 8.0.x builds
      bn >= 8000 -> 80 // 8.0, including pre-release builds
      bn >= 7500 -> 75 // 7.0.2+
      bn >= 7200 -> 72 // 7.0 final
      bn >= 6900 -> 69 // 7.0 pre-M2
      bn >= 6500 -> 65 // 7.0 pre-M1
      bn >= 6000 -> 60 // 6.0.2+
      bn >= 5000 -> 55 // 6.0 branch, including all 6.0 EAP builds
      bn >= 4000 -> 50 // 5.1 branch
      else -> 40
    }
  }

  override fun compareTo(other: BuildNumber): Int {
    val c1 = components
    val c2 = other.components
    for (i in 0 until c1.size.coerceAtMost(c2.size)) {
      if (c1[i] == c2[i] && c1[i] == SNAPSHOT_VALUE) {
        return 0
      }
      if (c1[i] == SNAPSHOT_VALUE) {
        return 1
      }
      if (c2[i] == SNAPSHOT_VALUE) {
        return -1
      }
      val result = c1[i] - c2[i]
      if (result != 0) {
        return result
      }
    }
    return c1.size - c2.size
  }

  override fun equals(other: Any?): Boolean =
    this === other || other is BuildNumber && productCode == other.productCode && components.contentEquals(other.components)

  override fun hashCode() = 31 * productCode.hashCode() + components.contentHashCode()
}
