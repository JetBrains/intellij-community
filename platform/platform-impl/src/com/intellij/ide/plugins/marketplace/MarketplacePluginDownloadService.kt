// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.exists
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.FileHash
import org.jetbrains.annotations.ApiStatus
import java.io.*
import java.net.URLConnection
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream

private val LOG = Logger.getInstance(MarketplacePluginDownloadService::class.java)

private const val BLOCKMAP_ZIP_SUFFIX = ".blockmap.zip"

private const val BLOCKMAP_FILENAME = "blockmap.json"

private const val HASH_FILENAME_SUFFIX = ".hash.json"

private const val FILENAME = "filename="

private const val MAXIMUM_DOWNLOAD_PERCENT = 0.65 // 100% = 1.0

@ApiStatus.Internal
open class MarketplacePluginDownloadService {

  companion object {

    @JvmStatic
    @Throws(IOException::class)
    fun getPluginTempFile(): File {
      val pluginsTemp = File(PathManager.getPluginTempPath())
      if (!pluginsTemp.exists() && !pluginsTemp.mkdirs()) {
        throw IOException(IdeBundle.message("error.cannot.create.temp.dir", pluginsTemp))
      }
      return FileUtil.createTempFile(pluginsTemp, "plugin_", "_download", true, false)
    }

    @JvmStatic
    fun renameFileToZipRoot(zip: File): File {
      val newName = "${PluginInstaller.rootEntryName(zip.toPath())}.zip"
      val newZip = File("${zip.parent}/$newName")
      if (newZip.exists()) {
        FileUtil.delete(newZip)
      }
      FileUtil.rename(zip, newName)
      return newZip
    }
  }

  private val objectMapper by lazy { ObjectMapper() }

  @Throws(IOException::class)
  open fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator): File {
    val file = getPluginTempFile()
    return HttpRequests
      .request(pluginUrl)
      .setHeadersViaTuner()
      .gzip(false)
      .productNameAsUserAgent()
      .connect(
        HttpRequests.RequestProcessor { request: HttpRequests.Request ->
          request.saveToFile(file, indicator)
          val pluginFileUrl = getPluginFileUrl(request.connection)
          if (pluginFileUrl.endsWith(".zip")) {
            renameFileToZipRoot(file)
          }
          else {
            val contentDisposition: String? = request.connection.getHeaderField("Content-Disposition")
            val url = request.connection.url.toString()
            guessPluginFile(contentDisposition, url, file, pluginUrl)
          }
        })
  }

  @Throws(IOException::class)
  fun downloadPluginViaBlockMap(pluginUrl: String, prevPlugin: Path, indicator: ProgressIndicator): File {
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
        return downloadPlugin(pluginFileUrl, indicator)
      }

      val file = getPluginTempFile()
      val merger = PluginChunkMerger(prevPluginArchive.toFile(), oldBlockMap, newBlockMap, indicator)
      FileOutputStream(file).use { output -> merger.merge(output, PluginChunkDataSource(oldBlockMap, newBlockMap, pluginFileUrl)) }

      val curFileHash = FileInputStream(file).use { input -> FileHash(input, newPluginHash.algorithm) }
      if (curFileHash != newPluginHash) {
        LOG.info(IdeBundle.message("hashes.doesnt.match"))
        return downloadPlugin(pluginFileUrl, indicator)
      }

      return if (pluginFileUrl.endsWith(".zip")) {
        renameFileToZipRoot(file)
      }
      else {
        guessPluginFile(guessFileParameters.contentDisposition, guessFileParameters.url, file, pluginUrl)
      }
    }
    catch (e: Exception) {
      LOG.info(IdeBundle.message("error.download.plugin.via.blockmap"), e)
      return downloadPlugin(pluginFileUrl, indicator)
    }
  }

  @Throws(IOException::class)
  private fun getBlockMapFromZip(input: InputStream): BlockMap {
    return input.buffered().use { source ->
      ZipInputStream(source).use { zip ->
        var entry = zip.nextEntry
        while (entry.name != BLOCKMAP_FILENAME && entry.name != null) entry = zip.nextEntry
        if (entry.name == BLOCKMAP_FILENAME) {
          // there is must only one entry otherwise we can't properly
          // read entry because we don't know it size (entry.size returns -1)
          objectMapper.readValue(zip.readBytes(), BlockMap::class.java)
        }
        else {
          throw IOException("There is no entry $BLOCKMAP_FILENAME")
        }
      }
    }
  }
}

private fun guessPluginFile(contentDisposition: String?, url: String, file: File, pluginUrl: String): File {
  val fileName: String = guessFileName(contentDisposition, url, file, pluginUrl)
  val newFile = File(file.parentFile, fileName)
  FileUtil.rename(file, newFile)
  return newFile
}

@Throws(IOException::class)
private fun guessFileName(contentDisposition: String?, usedURL: String, file: File, pluginUrl: String): String {
  var fileName: String? = null
  LOG.debug("header: $contentDisposition")
  if (contentDisposition != null && contentDisposition.contains(FILENAME)) {
    val startIdx = contentDisposition.indexOf(FILENAME)
    val endIdx = contentDisposition.indexOf(';', startIdx)
    fileName = contentDisposition.substring(startIdx + FILENAME.length, if (endIdx > 0) endIdx else contentDisposition.length)
    if (StringUtil.startsWithChar(fileName, '\"') && StringUtil.endsWithChar(fileName, '\"')) {
      fileName = fileName.substring(1, fileName.length - 1)
    }
  }
  if (fileName == null) {
    // try to find a filename in an URL
    LOG.debug("url: $usedURL")
    fileName = usedURL.substring(usedURL.lastIndexOf('/') + 1)
    if (fileName.isEmpty() || fileName.contains("?")) {
      fileName = pluginUrl.substring(pluginUrl.lastIndexOf('/') + 1)
    }
  }
  if (!PathUtil.isValidFileName(fileName)) {
    LOG.debug("fileName: $fileName")
    FileUtil.delete(file)
    throw IOException("Invalid filename returned by a server")
  }
  return fileName
}

private fun downloadPercent(oldBlockMap: BlockMap, newBlockMap: BlockMap): Double {
  val oldSet = oldBlockMap.chunks.toSet()
  val newChunks = newBlockMap.chunks.filter { chunk -> !oldSet.contains(chunk) }
  return newChunks.sumBy { chunk -> chunk.length }.toDouble() /
         newBlockMap.chunks.sumBy { chunk -> chunk.length }.toDouble()
}

private fun getPluginFileUrl(connection: URLConnection): String {
  val url = connection.url
  val port = url.port
  return if (port == -1) {
    "${url.protocol}://${url.host}${url.path}"
  }
  else {
    "${url.protocol}://${url.host}:${port}${url.path}"
  }
}

private data class GuessFileParameters(val contentDisposition: String?, val url: String)

private fun getPluginFileUrlAndGuessFileParameters(pluginUrl: String): Pair<String, GuessFileParameters> {
  return HttpRequests
    .request(pluginUrl)
    .setHeadersViaTuner()
    .productNameAsUserAgent()
    .connect { request ->
      val connection = request.connection
      Pair(getPluginFileUrl(connection),
        GuessFileParameters(connection.getHeaderField("Content-Disposition"), connection.url.toString()))
    }
}

private fun getPrevPluginArchive(prevPlugin: Path): Path {
  val suffix = if (prevPlugin.endsWith(".jar")) "" else ".zip"
  return Paths.get(PathManager.getPluginTempPath()).resolve("${prevPlugin.fileName}$suffix")
}

