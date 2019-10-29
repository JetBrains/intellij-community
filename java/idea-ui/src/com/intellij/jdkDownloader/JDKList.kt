// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.text.VersionComparatorUtil
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.lang.RuntimeException

/** describes vendor + product part of the UI **/
data class JDKProduct(
  private val vendor: String,
  private val product: String?,
  private val flavour: String?
) : Comparable<JDKProduct> {
  private fun String?.compareToIgnoreCase(other: String?): Int {
    if (this == other) return 0
    if (this == null && other != null) return -1
    if (this != null && other == null) return 1
    if (this != null && other != null) return this.compareTo(other, ignoreCase = true)
    return 0
  }

  override fun compareTo(other: JDKProduct): Int {
    var cmp = this.vendor.compareToIgnoreCase(other.vendor)
    if (cmp != 0) return cmp
    cmp = this.product.compareToIgnoreCase(other.product)
    if (cmp != 0) return cmp
    return this.flavour.compareToIgnoreCase(other.flavour)
  }

  val getPackagePresentationText : String get() = buildString {
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
data class JDKItem(
  val product: JDKProduct,

  val isDefaultItem: Boolean = false,

  private val jdkMajorVersion: Int,
  private val jdkVersion: String,
  private val jdkVendorVersion: String?,
  private val vendorVersion: String?,

  val arch: String,
  val packageType: String,
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
) : Comparable<JDKItem> {

  override fun compareTo(other: JDKItem): Int {
    var cmp = -this.jdkMajorVersion.compareTo(other.jdkMajorVersion)
    if (cmp != 0) return cmp
    cmp = -VersionComparatorUtil.compare(this.jdkVersion, other.jdkVersion)
    if (cmp != 0) return cmp
    cmp = VersionComparatorUtil.compare(this.jdkVendorVersion, other.jdkVendorVersion)
    if (cmp != 0) return cmp
    return VersionComparatorUtil.compare(this.vendorVersion, other.vendorVersion)
  }

  val getVersionPresentationText : String get() = buildString {
    append(jdkVersion)
    append(" (")
    append(StringUtil.formatFileSize(archiveSize))
    append(")")
  }

  val getFullPresentationText : String get() = product.getPackagePresentationText + " " + getVersionPresentationText
}

object JDKListDownloader {
  private val feedUrl: String
    get() {
      val registry = runCatching { Registry.get("jdk.downloader.url").asString() }.getOrNull()
      if (!registry.isNullOrBlank()) return registry
      return "http://download.jetbrains.com/jdk/feed/v1/jdks.json.xz"
    }

  fun downloadModel(progress: ProgressIndicator?, feedUrl: String = JDKListDownloader.feedUrl): List<JDKItem> {
    //we download XZ packed version of the data (several KBs packed, several dozen KBs unpacked) and process it in-memory
    val rawData = try {
      //timeouts are handled inside
      HttpRequests
        .request(feedUrl)
        .productNameAsUserAgent()
        .readBytes(progress)
        .unXZ()
    }
    catch (t: IOException) {
      throw RuntimeException("Failed to download and process the JDKs list from $feedUrl. ${t.message}", t)
    }

    try {
      val tree = ObjectMapper().readTree(rawData) as? ObjectNode ?: error("Unexpected JSON data")
      val items = tree["jdks"] as? ArrayNode ?: error("`jdks` element is missing")

      val expectedOS = when {
        SystemInfo.isWindows -> "windows"
        SystemInfo.isMac -> "macOS"
        SystemInfo.isLinux -> "linux"
        else -> error("Unsupported OS")
      }

      val result = mutableListOf<JDKItem>()
      for (item in items.filterIsInstance<ObjectNode>()) {
        val packages = item["packages"] as? ArrayNode ?: continue
        val pkg = packages.filterIsInstance<ObjectNode>().singleOrNull { it["os"]?.asText() == expectedOS } ?: continue

        val product = JDKProduct(
          vendor = item["vendor"]?.asText() ?: continue,
          product = item["product"]?.asText(),
          flavour = item["flavour"]?.asText()
        )

        result += JDKItem(product = product,
                          isDefaultItem = item["default"]?.asBoolean() ?: false,

                          jdkMajorVersion = item["jdk_version_major"]?.asInt() ?: continue,
                          jdkVersion = item["jdk_version"]?.asText() ?: continue,
                          jdkVendorVersion = item["jdk_vendor_version"]?.asText(),
                          vendorVersion = item["vendor_version"]?.asText(),

                          arch = pkg["arch"]?.asText() ?: continue,
                          packageType = pkg["package_type"]?.asText() ?: continue,
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

      return result
    }
    catch (t: Throwable) {
      throw RuntimeException("Failed to parse downloaded JDKs list from $feedUrl. ${t.message}", t)
    }
  }

  private fun ByteArray.unXZ() = ByteArrayInputStream(this).use { input ->
    XZInputStream(input).use { it.readBytes() }
  }
}
