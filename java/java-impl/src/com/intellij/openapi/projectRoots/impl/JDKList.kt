// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.HttpRequests
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream

data class JDKVendor(
  val vendor: String
  //TODO: add JDK type (e.g. Adopt OpenJDK / Adopt OpenJ9) (feed update required?)
)

data class JDKDownloadItem(
  val vendor: JDKVendor,
  val version: String,
  //TODO: add order value for comparison (we'd like 13, 11, 9, 8) order
  //TODO: include vendor specific version too (e.g. Zulu 13.12.123)
  //TODO: add vendor specific flavour (OpenJ9, JavaFX)
  val arch: String,
  val fileType: String,
  val url: String,
  val size: Long,
  val sha256: String
) {
  val installFolderName get() = url.split("/").last() //TODO: use feed for it
}

class JDKList private constructor(
  val feedError: String? = null,
  val items: List<JDKDownloadItem> = listOf()
) {
  companion object {
    fun error(error: String) = JDKList(feedError = error)
    fun ok(items: List<JDKDownloadItem>) = JDKList(items = items)
  }
}

object JDKListDownloader {
  private val LOG = logger<JDKListDownloader>()

  private val feedUrl: String
    get() {
      val registry = runCatching { Registry.get("jdk.downloader.url").asString() }.getOrNull()
      if (!registry.isNullOrBlank()) return registry

      //TODO: let's use CDN URL in once it'd be established
      return "https://buildserver.labs.intellij.net/guestAuth/repository/download/ijplatform_master_Service_GenerateJDKsJson/lasest.lastSuccessful/feed.zip!/jdks.json.xz"
    }

  fun downloadModel(progress: ProgressIndicator?, feedUrl : String = this.feedUrl): JDKList {
    //we download XZ packed version of the data (several KBs packed, several dozen KBs unpacked) and process it in-memory
    val rawData = try {
      HttpRequests
        .request(feedUrl)
        .connectTimeout(5_000)
        .readTimeout(5_000)
        .forceHttps(true)
        .throwStatusCodeException(true)
        .readBytes(progress)
        .unXZ()
    } catch (t: Throwable) {
      LOG.warn("Failed to download and process the JDKs list from $feedUrl. ${t.message}", t)
      return JDKList.error("Failed to download and process the JDKs list from $feedUrl")
    }

    try {
      val tree = ObjectMapper().readTree(rawData) as? ObjectNode ?: error("Unexpected JSON data")
      val items = tree["jdks"] as? ArrayNode ?: error("`jdks` element is missing")

      val expectedOS = when {
        SystemInfo.isWindows -> "windows"
        SystemInfo.isMac -> "mac"
        SystemInfo.isLinux -> "linux"
        else -> error("Unsupported OS")
      }

      val result = mutableListOf<JDKDownloadItem>()
      for (item in items.filterIsInstance<ObjectNode>()) {
        val vendor = item["vendor"]?.asText() ?: continue
        val version = item["jdk_version"]?.asText() ?: continue
        val packages = item["packages"] as? ArrayNode ?: continue
        val pkg = packages.filterIsInstance<ObjectNode>().singleOrNull { it["os"]?.asText() == expectedOS } ?: continue
        val arch = pkg["arch"]?.asText() ?: continue
        val fileType = pkg["package"]?.asText() ?: continue
        val url = pkg["url"]?.asText() ?: continue
        val size = pkg["size"]?.asLong() ?: continue
        val sha256 = pkg["sha256"]?.asText() ?: continue

        result += JDKDownloadItem(vendor = JDKVendor(vendor),
                                  version = version,
                                  arch = arch,
                                  fileType = fileType,
                                  url = url,
                                  size = size,
                                  sha256 = sha256)
      }

      return JDKList.ok(result)
    } catch (t: Throwable) {
      LOG.warn("Failed to parse downloaded JDKs list from $feedUrl. ${t.message}", t)
      return JDKList.error("Failed to parse downloaded JDKs list")
    }
  }
}

private fun ByteArray.unXZ() = ByteArrayInputStream(this).use { input ->
  XZInputStream(input).use { it.readBytes() }
}
