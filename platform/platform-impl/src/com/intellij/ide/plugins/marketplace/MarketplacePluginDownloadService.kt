// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.PathUtil
import com.intellij.util.io.HttpRequests
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.FileHash
import org.jetbrains.annotations.ApiStatus
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLConnection
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.*

@ApiStatus.Internal
open class MarketplacePluginDownloadService {
  companion object {
    private val LOG = Logger.getInstance(MarketplacePluginDownloadService::class.java)

    private const val BLOCKMAP_ZIP_SUFFIX = ".blockmap.zip"
    private const val BLOCKMAP_FILENAME = "blockmap.json"
    private const val HASH_FILENAME_SUFFIX = ".hash.json"
    private const val FILENAME = "filename="
    private const val MAXIMUM_DOWNLOAD_PERCENT = 0.65 // 100% = 1.0

    @JvmStatic
    @Throws(IOException::class)
    fun getPluginTempFile(): Path =
      createTempFile(PathManager.getStartupScriptDir().createDirectories(), "plugin_", "_download")

    @JvmStatic
    @Throws(IOException::class)
    fun renameFileToZipRoot(zip: Path): Path {
      val newName = "${PluginInstaller.rootEntryName(zip)}.zip"
      val newZip = zip.resolveSibling(newName)
      zip.moveTo(newZip, overwrite = true)
      return newZip
    }
  }

  private val objectMapper by lazy { ObjectMapper() }

  @Throws(IOException::class)
  open fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator?): Path {
    val file = getPluginTempFile()
    return HttpRequests.request(pluginUrl)
      .setHeadersViaTuner()
      .gzip(false)
      .productNameAsUserAgent()
      .connect(HttpRequests.RequestProcessor { request ->
        request.saveToFile(file, indicator)
        val pluginFileUrl = getPluginFileUrl(request.connection)
        if (pluginFileUrl.endsWith(".zip")) {
          renameFileToZipRoot(file)
        }
        else {
          val contentDisposition: String? = request.connection.getHeaderField("Content-Disposition")
          val url = request.connection.url.toString()
          guessPluginFilenameAndRenameDownloadedFile(contentDisposition, url, file, pluginUrl)
        }
      })
  }

  @Throws(IOException::class)
  fun downloadPluginViaBlockMap(pluginUrl: String, prevPlugin: Path, indicator: ProgressIndicator?): Path {
    val prevPluginArchive = getPrevPluginArchive(prevPlugin)
    if (!prevPluginArchive.exists()) {
      LOG.info(IdeBundle.message("error.file.not.found.message", prevPluginArchive.toString()))
      return downloadPlugin(pluginUrl, indicator)
    }

    val (pluginFileUrl, guessFileParameters) = getPluginFileUrlAndGuessFileParameters(pluginUrl)
    val blockMapFileUrl = "$pluginFileUrl$BLOCKMAP_ZIP_SUFFIX"
    val pluginHashFileUrl = "$pluginFileUrl$HASH_FILENAME_SUFFIX"
    try {
      val newBlockMap = HttpRequests
        .request(blockMapFileUrl)
        .setHeadersViaTuner()
        .productNameAsUserAgent()
        .connect { request ->
          request.inputStream.use { input ->
            getBlockMapFromZip(input)
          }
        }
      LOG.info("Plugin's blockmap file downloaded")
      val newPluginHash = HttpRequests
        .request(pluginHashFileUrl)
        .setHeadersViaTuner()
        .productNameAsUserAgent()
        .connect { request ->
          request.inputStream.reader().buffered().use { input ->
            objectMapper.readValue(input.readText(), FileHash::class.java)
          }
        }
      LOG.info("Plugin's hash file downloaded")

      val oldBlockMap = FileInputStream(prevPluginArchive.toFile()).use { input ->
        BlockMap(input, newBlockMap.algorithm, newBlockMap.minSize, newBlockMap.maxSize, newBlockMap.normalSize)
      }

      val downloadPercent = downloadPercent(oldBlockMap, newBlockMap)
      LOG.info("Plugin's download percent is = %.2f".format(downloadPercent * 100))
      if (downloadPercent > MAXIMUM_DOWNLOAD_PERCENT) {
        LOG.info(IdeBundle.message("too.large.download.size"))
        return downloadPlugin(pluginUrl, indicator)
      }

      val file = getPluginTempFile()
      val merger = PluginChunkMerger(prevPluginArchive.toFile(), oldBlockMap, newBlockMap, indicator)
      file.outputStream().use { output -> merger.merge(output, PluginChunkDataSource(oldBlockMap, newBlockMap, pluginFileUrl)) }

      val curFileHash = file.inputStream().use { input -> FileHash(input, newPluginHash.algorithm) }
      if (curFileHash != newPluginHash) {
        LOG.info(IdeBundle.message("hashes.doesnt.match"))
        return downloadPlugin(pluginUrl, indicator)
      }

      return if (pluginFileUrl.endsWith(".zip")) {
        renameFileToZipRoot(file)
      }
      else {
        guessPluginFilenameAndRenameDownloadedFile(guessFileParameters.contentDisposition, guessFileParameters.url, file, pluginUrl)
      }
    }
    catch (e: HttpRequests.HttpStatusException) {
      return processBlockmapDownloadProblem(e, pluginUrl, indicator, printStackTrace = false)
    }
    catch (e: Exception) {
      return processBlockmapDownloadProblem(e, pluginUrl, indicator, printStackTrace = true)
    }
  }

  private fun processBlockmapDownloadProblem(exception: Exception, pluginUrl: String, indicator: ProgressIndicator?, printStackTrace: Boolean): Path {
    val message = IdeBundle.message("error.download.plugin.via.blockmap", pluginUrl)
    if (printStackTrace) {
      LOG.info(message, exception)
    }
    else {
      LOG.info("$message: ${exception.message}")
    }
    return downloadPlugin(pluginUrl, indicator)
  }

  @Throws(IOException::class)
  private fun getBlockMapFromZip(input: InputStream): BlockMap = input.buffered().use { source ->
    ZipInputStream(source).use { zip ->
      var entry = zip.nextEntry
      while (entry.name != BLOCKMAP_FILENAME && entry.name != null) entry = zip.nextEntry
      if (entry.name == BLOCKMAP_FILENAME) {
        // there must be only one entry otherwise we can't properly read it because we don't know its size (entry.size returns -1)
        objectMapper.readValue(zip.readBytes(), BlockMap::class.java)
      }
      else {
        throw IOException("There is no entry $BLOCKMAP_FILENAME")
      }
    }
  }

  private fun guessPluginFilenameAndRenameDownloadedFile(contentDisposition: String?, url: String, file: Path, pluginUrl: String): Path {
    val fileName = guessFileName(contentDisposition, url, file, pluginUrl)
    val newFile = file.resolveSibling(fileName)
    file.moveTo(newFile)
    return newFile
  }

  @Throws(IOException::class)
  private fun guessFileName(contentDisposition: String?, usedURL: String, file: Path, pluginUrl: String): String {
    var fileName: String? = null
    LOG.debug("header: $contentDisposition")
    if (contentDisposition != null && contentDisposition.contains(FILENAME)) {
      val startIdx = contentDisposition.indexOf(FILENAME)
      val endIdx = contentDisposition.indexOf(';', startIdx)
      fileName = contentDisposition.substring(startIdx + FILENAME.length, if (endIdx > 0) endIdx else contentDisposition.length)
      if (fileName.startsWith('\"') && fileName.endsWith('\"')) {
        fileName = fileName.substring(1, fileName.length - 1)
      }
    }
    if (fileName == null) {
      // try to find a filename in a URL
      LOG.debug("url: $usedURL")
      fileName = usedURL.substring(usedURL.lastIndexOf('/') + 1)
      if (fileName.isEmpty() || fileName.contains("?")) {
        fileName = pluginUrl.substring(pluginUrl.lastIndexOf('/') + 1)
      }
    }
    if (!PathUtil.isValidFileName(fileName)) {
      LOG.debug("fileName: $fileName")
      file.deleteIfExists()
      throw IOException("Invalid filename returned by a server")
    }
    return fileName
  }

  private fun downloadPercent(oldBlockMap: BlockMap, newBlockMap: BlockMap): Double {
    val oldSet = oldBlockMap.chunks.toSet()
    val newChunks = newBlockMap.chunks.filter { chunk -> !oldSet.contains(chunk) }
    return newChunks.sumOf { chunk -> chunk.length }.toDouble() /
           newBlockMap.chunks.sumOf { chunk -> chunk.length }.toDouble()
  }

  private fun getPluginFileUrlAndGuessFileParameters(pluginUrl: String): Pair<String, GuessFileParameters> =
    HttpRequests.request(pluginUrl)
      .setHeadersViaTuner()
      .productNameAsUserAgent()
      .connect { request ->
        val connection = request.connection
        getPluginFileUrl(connection) to GuessFileParameters(connection.getHeaderField("Content-Disposition"), connection.url.toString())
      }

  private fun getPluginFileUrl(connection: URLConnection): String = connection.url.let { url ->
    if (url.port == -1) "${url.protocol}://${url.host}${url.path}"
    else "${url.protocol}://${url.host}:${url.port}${url.path}"
  }

  private fun getPrevPluginArchive(prevPlugin: Path): Path {
    val suffix = if (prevPlugin.endsWith(".jar")) "" else ".zip"
    return PathManager.getStartupScriptDir().resolve("${prevPlugin.fileName}$suffix")
  }

  private data class GuessFileParameters(val contentDisposition: String?, val url: String)
}
