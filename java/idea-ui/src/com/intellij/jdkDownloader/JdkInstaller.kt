// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.absoluteValue

data class JdkInstallRequest(
  val item: JdkItem,
  val targetDir: File
)

object JdkInstaller {
  private val LOG = logger<JdkInstaller>()

  fun validateInstallDir(selectedPath: String): Pair<File?, String?> {
    if (selectedPath.isBlank()) return null to "Target path is empty"

    val targetDir = runCatching { File(FileUtil.expandUserHome(selectedPath)) }.getOrElse { t ->
      LOG.warn("Failed to resolve user path: $selectedPath. ${t.message}", t)
      return null to (t.message ?: "Failed to resolve path")
    }

    if (targetDir.isFile) return null to "Target path is an existing file"
    if (targetDir.isDirectory && targetDir.listFiles()?.isNotEmpty() == true) {
      return null to "Target path is an existing non-empty directory"
    }

    return targetDir to null
  }

  fun installJdk(request: JdkInstallRequest, indicator: ProgressIndicator?) {
    val item = request.item
    indicator?.text = "Installing ${item.fullPresentationText}..."

    val targetDir = request.targetDir
    val url = Urls.parse(item.url, false) ?: error("Cannot parse download URL: ${item.url}")
    if (!url.scheme.equals("https", ignoreCase = true)) error("URL must use https:// protocol, but was: $url")

    indicator?.text2 = "Downloading"
    val downloadFile = File(PathManager.getTempPath(), "jdk-${item.archiveFileName}")
    try {
      try {
        HttpRequests.request(item.url)
          .productNameAsUserAgent()
          .connect { processor -> processor.saveToFile(downloadFile, indicator) }

      }
      catch (t: IOException) {
        throw RuntimeException("Failed to download JDK from $url. ${t.message}", t)
      }

      val sizeDiff = downloadFile.length() - item.archiveSize
      if (sizeDiff != 0L) {
        throw RuntimeException("Downloaded JDK distribution has incorrect size, difference is ${sizeDiff.absoluteValue} bytes")
      }

      val actualHashCode = Files.asByteSource(downloadFile).hash(Hashing.sha256()).toString()
      if (!actualHashCode.equals(item.sha256, ignoreCase = true)) {
        throw RuntimeException("SHA-256 checksums does not match. Actual value is $actualHashCode, expected ${item.sha256}")
      }

      indicator?.isIndeterminate = true
      indicator?.text2 = "Unpacking"

      val decompressor = item.packageType.openDecompressor(downloadFile)
      //handle cancellation via postProcessor (instead of inheritance)
      decompressor.postprocessor { indicator?.checkCanceled() }

      val fullMatchPath = item.unpackPrefixFilter.trim('/')
      if (!fullMatchPath.isBlank()) {
        decompressor.removePrefixPath(fullMatchPath)
      }
      decompressor.extract(targetDir)
    }
    catch (t: Throwable) {
      //if we were cancelled in the middle or failed, let's clean up
      FileUtil.delete(targetDir)
      if (t is ProcessCanceledException) throw t
      if (t is IOException) throw RuntimeException("Failed to extract JDK package", t)
      throw t
    }
    finally {
      FileUtil.delete(downloadFile)
    }
  }

  /**
   * executed synchronously to prepare Jdk installation process, that would run in the future
   */
  fun prepareJdkInstallation(jdkItem: JdkItem, targetPath: String): JdkInstallRequest {
    val (home, error) = validateInstallDir(targetPath)
    if (home == null || error != null) throw RuntimeException(error ?: "Invalid Target Directory")

    FileUtil.createDirectory(home)
    if (!home.isDirectory) {
      throw IOException("Failed to create home directory: $home")
    }

    val markerFile = File(home, "intellij-downloader-info.json")
    markerFile.writeText("Download started on ${Date()}\n$jdkItem")

    return JdkInstallRequest(jdkItem, home)
  }
}

