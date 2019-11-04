// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.Decompressor
import com.intellij.util.io.HttpRequests
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.NonNls
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.lang.RuntimeException

/** describes vendor + product part of the UI **/
data class JdkProduct(
  private val vendor: String,
  private val product: String?,
  private val flavour: String?
) : Comparable<JdkProduct> {

  private fun String?.compareToIgnoreCase(other: String?): Int {
    if (this == other) return 0
    if (this == null && other != null) return -1
    if (this != null && other == null) return 1
    if (this != null && other != null) return this.compareTo(other, ignoreCase = true)
    return 0
  }

  override fun compareTo(other: JdkProduct): Int {
    var cmp = this.vendor.compareToIgnoreCase(other.vendor)
    if (cmp != 0) return cmp
    cmp = this.product.compareToIgnoreCase(other.product)
    if (cmp != 0) return cmp
    return this.flavour.compareToIgnoreCase(other.flavour)
  }

  val packagePresentationText: String
    get() = buildString {
      append(vendor)
      if (product != null) {
        append(" ")
        append(product)
      }

      if (flavour != null) {
        append(" (")
        append(flavour)
        append(")")
      }
    }
}

/** describes an item behind the version as well as download info **/
data class JdkItem(
  val product: JdkProduct,

  val isDefaultItem: Boolean = false,

  private val jdkMajorVersion: Int,
  private val jdkVersion: String,
  private val jdkVendorVersion: String?,
  private val vendorVersion: String?,

  val arch: String,
  val packageType: JdkPackageType,
  val url: String,
  val sha256: String,

  val archiveSize: Long,
  val unpackedSize: Long,

  // normally archive container a root folder inside, or several for macOS bundles
  // we need to know how many to skip
  val unpackCutDirs: Int,
  // we should only extract items tarting from the given prefix (e.g. masOS bundle)
  val unpackPrefixFilter: String,

  val archiveFileName: String,
  val installFolderName: String
) : Comparable<JdkItem> {

  override fun compareTo(other: JdkItem): Int {
    var cmp = this.jdkMajorVersion.compareTo(other.jdkMajorVersion)
    if (cmp != 0) return -cmp
    cmp = VersionComparatorUtil.compare(this.jdkVersion, other.jdkVersion)
    if (cmp != 0) return -cmp
    cmp = VersionComparatorUtil.compare(this.jdkVendorVersion, other.jdkVendorVersion)
    if (cmp != 0) return cmp
    return VersionComparatorUtil.compare(this.vendorVersion, other.vendorVersion)
  }

  val versionPresentationText: String
    get() = buildString {
      append(jdkVersion)
      append(" (")
      append(StringUtil.formatFileSize(archiveSize))
      append(")")
    }

  val fullPresentationText: String
    get() = product.packagePresentationText + " " + versionPresentationText
}

enum class JdkPackageType(@NonNls val type: String) {
  @Suppress("unused")
  ZIP("zip") {
    override fun openDecompressor(archiveFile: File) = Decompressor.Zip(archiveFile)
  },

  @Suppress("SpellCheckingInspection", "unused")
  TAR_GZ("targz") {
    override fun openDecompressor(archiveFile: File) = Decompressor.Tar(archiveFile)
  };

  abstract fun openDecompressor(archiveFile: File): Decompressor

  companion object {
    fun findType(jsonText: String): JdkPackageType? = values().firstOrNull { it.type.equals(jsonText, ignoreCase = true) }
  }
}

data class JdkPredicate(
  private val ideBuildNumber: BuildNumber,
  private val expectedOS: String
) {
  fun testJdkProduct(product: ObjectNode): Boolean {
    val filterNode = product["filter"]
    return testPredicate(filterNode) == true
  }

  fun testJdkPackage(pkg: ObjectNode): Boolean {
    if (pkg["os"]?.asText() != expectedOS) return false
    if (pkg["package_type"]?.asText()?.let(JdkPackageType.Companion::findType) == null) return false
    return testPredicate(pkg["filter"]) == true
  }

  /**
   * tests the predicate from the `filter` or `default` elements an JDK product
   * against current IDE instance
   *
   * returns `null` if there was something unknown detected in the filter
   *
   * It supports the following predicates with `type` equal to `build_number_range`, `and`, `or`, `not`, e.g.
   *         { "type": "build_number_range", "since": "192.34", "until": "194.123" }
   * or
   *         { "type": "or"|"and", "items": [ {same as before}, ...] }
   * or
   *         { "type": "not", "item": { same as before } }
   */
  fun testPredicate(filter: JsonNode?): Boolean? {
    //no filter means predicate is true
    if (filter == null) return true

    // used in "default" element
    if (filter.isBoolean) return filter.asBoolean()

    if (filter !is ObjectNode) return null

    val type = filter["type"]?.asText() ?: return null
    if (type == "or") {
      return foldSubPredicates(filter, false, Boolean::or)
    }

    if (type == "and") {
      return foldSubPredicates(filter, true, Boolean::and)
    }

    if (type == "not") {
      val subResult = testPredicate(filter["item"]) ?: return null
      return !subResult
    }

    if (type == "build_number_range") {
      val fromBuild = filter["since"]?.asText()
      val untilBuild = filter["until"]?.asText()

      if (fromBuild == null && untilBuild == null) return true

      if (fromBuild != null) {
        val fromBuildSafe = BuildNumber.fromStringOrNull(fromBuild) ?: return null
        if (fromBuildSafe > ideBuildNumber) return false
      }

      if (untilBuild != null) {
        val untilBuildSafe = BuildNumber.fromStringOrNull(untilBuild) ?: return null
        if (ideBuildNumber > untilBuildSafe) return false
      }

      return true
    }

    return null
  }

  private fun foldSubPredicates(filter: ObjectNode,
                                emptyResult: Boolean,
                                op: (acc: Boolean, Boolean) -> Boolean): Boolean? {
    val items = filter["items"] as? ArrayNode ?: return null
    if (items.isEmpty) return false
    return items.fold(emptyResult) { acc, subFilter ->
      val subResult = testPredicate(subFilter) ?: return null
      op(acc, subResult)
    }
  }
}

object JdkListDownloader {
  private val feedUrl: String
    get() {
      val registry = runCatching { Registry.get("jdk.downloader.url").asString() }.getOrNull()
      if (!registry.isNullOrBlank()) return registry
      return "https://download.jetbrains.com/jdk/feed/v1/jdks.json.xz"
    }

  private fun downloadJdkList(feedUrl: String, progress: ProgressIndicator?): ByteArray {
    //timeouts are handled inside
    return HttpRequests
      .request(feedUrl)
      .productNameAsUserAgent()
      .readBytes(progress)
      .unXZ()
  }

  fun parseJdkList(tree: ObjectNode, filters: JdkPredicate): List<JdkItem> {
    val items = tree["jdks"] as? ArrayNode ?: error("`jdks` element is missing")

    val result = mutableListOf<JdkItem>()
    for (item in items.filterIsInstance<ObjectNode>()) {
      // check this package is OK to show for that instance of the IDE
      if (!filters.testJdkProduct(item)) continue

      val packages = item["packages"] as? ArrayNode ?: continue
      // take the first matching package
      val pkg = packages.filterIsInstance<ObjectNode>().firstOrNull(filters::testJdkPackage) ?: continue

      val product = JdkProduct(
        vendor = item["vendor"]?.asText() ?: continue,
        product = item["product"]?.asText(),
        flavour = item["flavour"]?.asText()
      )

      result += JdkItem(product = product,
                        isDefaultItem = item["default"]?.let { filters.testPredicate(it) == true } ?: false,

                        jdkMajorVersion = item["jdk_version_major"]?.asInt() ?: continue,
                        jdkVersion = item["jdk_version"]?.asText() ?: continue,
                        jdkVendorVersion = item["jdk_vendor_version"]?.asText(),
                        vendorVersion = item["vendor_version"]?.asText(),

                        arch = pkg["arch"]?.asText() ?: continue,
                        packageType = pkg["package_type"]?.asText()?.let(JdkPackageType.Companion::findType) ?: continue,
                        url = pkg["url"]?.asText() ?: continue,
                        sha256 = pkg["sha256"]?.asText() ?: continue,
                        archiveSize = pkg["archive_size"]?.asLong() ?: continue,
                        archiveFileName = pkg["archive_file_name"]?.asText() ?: continue,
                        unpackCutDirs = pkg["unpack_cut_dirs"]?.asInt() ?: continue,
                        unpackPrefixFilter = pkg["unpack_prefix_filter"]?.asText() ?: continue,

                        unpackedSize = pkg["unpacked_size"]?.asLong() ?: continue,
                        installFolderName = pkg["install_folder_name"]?.asText() ?: continue
      )
    }

    return result.toList()
  }

  fun downloadModel(progress: ProgressIndicator?, feedUrl: String = JdkListDownloader.feedUrl): List<JdkItem> {
    // download XZ packed version of the data (several KBs packed, several dozen KBs unpacked) and process it in-memory
    val rawData = try {
      downloadJdkList(feedUrl, progress)
    }
    catch (t: IOException) {
      throw RuntimeException("Failed to download and process the JDKs list from $feedUrl. ${t.message}", t)
    }

    val json = try {
      ObjectMapper().readTree(rawData) as? ObjectNode ?: error("Unexpected JSON data")
    }
    catch (t: Throwable) {
      throw RuntimeException("Failed to parse downloaded JDKs list from $feedUrl. ${t.message}", t)
    }

    try {
      val expectedOS = when {
        SystemInfo.isWindows -> "windows"
        SystemInfo.isMac -> "macOS"
        SystemInfo.isLinux -> "linux"
        else -> error("Unsupported OS")
      }
      return parseJdkList(json, JdkPredicate(ApplicationInfoImpl.getShadowInstance().build, expectedOS))
    }
    catch (t: Throwable) {
      throw RuntimeException("Failed to process downloaded JDKs list from $feedUrl. ${t.message}", t)
    }
  }

  private fun ByteArray.unXZ() = ByteArrayInputStream(this).use { input ->
    XZInputStream(input).use { it.readBytes() }
  }
}
