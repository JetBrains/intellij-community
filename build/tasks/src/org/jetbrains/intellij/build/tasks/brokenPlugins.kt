// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.io.debug
import org.jetbrains.intellij.build.io.info
import org.jetbrains.intellij.build.io.warn
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.System.Logger
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.zip.GZIPInputStream

private const val MARKETPLACE_BROKEN_PLUGINS_URL = "https://plugins.jetbrains.com/files/brokenPlugins.json"

/**
 * Generate brokenPlugins.txt file using JetBrains Marketplace.
 */
fun buildBrokenPlugins(targetFile: Path, currentBuildString: String, isInDevelopmentMode: Boolean, logger: Logger) {
  val allBrokenPlugins = try {
    downloadFileFromMarketplace(logger)
  }
  catch (e: Exception) {
    if (isInDevelopmentMode) {
      logger.warn("Not able to get broken plugins info from JetBrains Marketplace: $e\nAssuming empty broken plugins list")
      return
    }
    else {
      throw e
    }
  }

  logger.debug { "Generate list of broken plugins for build:\n $currentBuildString" }
  val currentBuild = BuildNumber.fromString(currentBuildString, currentBuildString)!!
  val result = TreeMap<String, MutableSet<String>>()
  for (plugin in allBrokenPlugins) {
    val originalUntil = BuildNumber.fromString(plugin.originalUntil, currentBuildString) ?: currentBuild
    val originalSince = BuildNumber.fromString(plugin.originalSince, currentBuildString) ?: currentBuild
    val until = BuildNumber.fromString(plugin.until, currentBuildString) ?: currentBuild
    val since = BuildNumber.fromString(plugin.since, currentBuildString) ?: currentBuild
    if ((currentBuild in originalSince..originalUntil) && (currentBuild > until || currentBuild < since)) {
      result.computeIfAbsent(plugin.id) { TreeSet<String>() }.add(plugin.version)
    }
  }
  storeBrokenPlugin(result, targetFile)
  logger.debug { "Broken plugin list was generated (count=${result.size}, file=$targetFile)" }
}

private fun downloadFileFromMarketplace(logger: Logger): List<MarketplaceBrokenPlugin> {
  val jsonFormat = Json { ignoreUnknownKeys = true }
  var attemptNumber = 0
  while (true) {
    logger.info("Load broken plugin list from $MARKETPLACE_BROKEN_PLUGINS_URL")
    val httpClient = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.ALWAYS)
      .build()

    val request = HttpRequest.newBuilder(URI(MARKETPLACE_BROKEN_PLUGINS_URL))
      .header("Accept", "application/json")
      .header("Accept-Encoding", "gzip")
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
    val encoding = response.headers().firstValue("Content-Encoding").orElse("")
    val byteOut = ByteArrayOutputStream()
    (if (encoding == "gzip") GZIPInputStream(response.body()) else response.body()).use {
      it.transferTo(byteOut)
    }
    val content = byteOut.toByteArray().toString(Charsets.UTF_8)
    val responseCode = response.statusCode()
    if (responseCode == HttpURLConnection.HTTP_OK) {
      return jsonFormat.decodeFromString(ListSerializer(MarketplaceBrokenPlugin.serializer()), content)
    }

    val error = RuntimeException("$responseCode: $content")
    // server error - retry
    if (attemptNumber > 3 || responseCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
      throw error
    }
    else {
      attemptNumber++
    }
  }
}

private fun storeBrokenPlugin(brokenPlugin: Map<String, Set<String>>, targetFile: Path) {
  Files.createDirectories(targetFile.parent)
  DataOutputStream(BufferedOutputStream(Files.newOutputStream(targetFile), 32_000)).use { out ->
    out.write(1)
    out.writeInt(brokenPlugin.size)
    for (entry in brokenPlugin.entries) {
      out.writeUTF(entry.key)
      out.writeShort(entry.value.size)
      entry.value.forEach(out::writeUTF)
    }
  }
}

@Serializable
private data class MarketplaceBrokenPlugin(
  var id: String,
  var version: String,
  var until: String?,
  var since: String?,
  var originalSince: String?,
  var originalUntil: String?
)

private class BuildNumber(private val productCode: String, private val components: IntArray) : Comparable<BuildNumber> {
  companion object {
    private const val STAR = "*"
    private const val SNAPSHOT = "SNAPSHOT"
    private const val SNAPSHOT_VALUE = Integer.MAX_VALUE

    private fun isPlaceholder(value: String) = "__BUILD_NUMBER__" == value || "__BUILD__" == value

    fun fromString(version: String?, current: String): BuildNumber? {
      if (version == null) {
        return null
      }
      val v = version.trim { it <= ' ' }
      return if (v.isEmpty()) null else fromString(version = v, pluginName = null, productCodeIfAbsentInVersion = null, current = current)
    }

    fun fromString(version: String, pluginName: String?, productCodeIfAbsentInVersion: String?, current: String?): BuildNumber? {
      var code = version
      val productSeparator = code.indexOf('-')
      val productCode: String
      if (productSeparator > 0) {
        productCode = code.substring(0, productSeparator)
        code = code.substring(productSeparator + 1)
      }
      else {
        productCode = productCodeIfAbsentInVersion ?: ""
      }

      if (SNAPSHOT === code || isPlaceholder(code)) {
        return BuildNumber(productCode, fromString(current, current!!)!!.components)
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
          val comp = parseBuildNumber(version, stringComponent, pluginName)
          intComponentsList[i] = comp
          if (comp == SNAPSHOT_VALUE && (i + 1) != n) {
            intComponentsList = intComponentsList.copyOf(i + 1)
            break
          }
        }
        return BuildNumber(productCode, intComponentsList)
      }
      else {
        val buildNumber = parseBuildNumber(version, code, pluginName)
        if (buildNumber <= 2000) {
          // it's probably a baseline, not a build number
          return BuildNumber(productCode, intArrayOf(buildNumber, 0))
        }

        val baselineVersion = getBaseLineForHistoricBuilds(buildNumber)
        return BuildNumber(productCode, intArrayOf(baselineVersion, buildNumber))
      }
    }

    private fun parseBuildNumber(version: String, code: String, pluginName: String?): Int {
      if (SNAPSHOT == code || isPlaceholder(code) || STAR == code) {
        return SNAPSHOT_VALUE
      }
      return code.toIntOrNull() ?: throw RuntimeException("Invalid version number: $version; plugin name: $pluginName")
    }

    // http://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html
    private fun getBaseLineForHistoricBuilds(bn: Int): Int {
      if (bn >= 10000) return 88 // Maia, 9x builds
      if (bn >= 9500) return 85 // 8.1 builds
      if (bn >= 9100) return 81 // 8.0.x builds
      if (bn >= 8000) return 80 // 8.0, including pre-release builds
      if (bn >= 7500) return 75 // 7.0.2+
      if (bn >= 7200) return 72 // 7.0 final
      if (bn >= 6900) return 69 // 7.0 pre-M2
      if (bn >= 6500) return 65 // 7.0 pre-M1
      if (bn >= 6000) return 60 // 6.0.2+
      if (bn >= 5000) return 55 // 6.0 branch, including all 6.0 EAP builds
      if (bn >= 4000) return 50 // 5.1 branch
      return 40
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

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    val that = other as BuildNumber
    if (productCode != that.productCode) {
      return false
    }
    return components.contentEquals(that.components)
  }

  override fun hashCode() = 31 * productCode.hashCode() + components.contentHashCode()
}