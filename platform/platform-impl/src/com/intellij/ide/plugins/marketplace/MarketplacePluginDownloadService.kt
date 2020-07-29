// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLConnection
import java.nio.file.Path
import java.nio.file.Paths

internal class MarketplacePluginDownloadService{
  companion object {
    private val LOG = Logger.getInstance(MarketplacePluginDownloadService::class.java)

    private const val BLOCKMAP_FILENAME = "blockmap.json"

    private const val HASH_FILENAME = "hash.txt"

    private const val FILENAME = "filename="

    private const val MAXIMUM_DOWNLOAD_PERCENT = 1.0

    private val objectMapper by lazy { ObjectMapper() }

    @Throws(IOException::class)
    fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator): File {
      val file = getPluginTempFile()
      return HttpRequests.request(pluginUrl).gzip(false).productNameAsUserAgent().connect(
        HttpRequests.RequestProcessor { request: HttpRequests.Request ->
          request.saveToFile(file, indicator)
          val pluginFileUrl = getPluginFileUrl(pluginUrl)
          if (pluginFileUrl.endsWith(".zip")) {
            renameFileToZipRoot(file)
          }
          else {
            guessPluginFile(request.connection, file, pluginUrl)
          }
        })
    }

    @Throws(IOException::class)
    fun downloadPluginViaBlockMap(pluginUrl: String, prevPlugin: Path, indicator: ProgressIndicator): File {

      val prevPluginArchive = getPrevPluginArchive(prevPlugin)
      if (!prevPluginArchive.exists()) {
        throw IOException(IdeBundle.message("error.file.not.found.message", prevPluginArchive.toString()))
      }
      val oldBlockMap = FileInputStream(prevPluginArchive.toFile()).use { input -> BlockMap(input) }

      // working with demo
      val curPluginUrl = pluginUrl.replace("plugins.jetbrains.com", "plugin-blockmap-patches.dev.marketplace.intellij.net")

      val pluginFileUrl = getPluginFileUrl(curPluginUrl)
      val blockMapFileUrl = pluginFileUrl.replaceAfterLast("/", BLOCKMAP_FILENAME)
      val pluginHashFileUrl = pluginFileUrl.replaceAfterLast("/", HASH_FILENAME)

      val newBlockMap = HttpRequests.request(blockMapFileUrl).productNameAsUserAgent().connect { request ->
        request.inputStream.reader().buffered().use { input ->
          objectMapper.readValue(input.readText(), BlockMap::class.java)
        }
      }
      LOG.debug("Plugin's blockmap file downloaded")
      val newPluginHash = HttpRequests.request(pluginHashFileUrl).productNameAsUserAgent().connect { request ->
        request.inputStream.reader().buffered().use { input ->
          objectMapper.readValue(input.readText(), FileHash::class.java)
        }
      }
      LOG.debug("Plugin's hash file downloaded")

      val downloadPercent = downloadPercent(oldBlockMap, newBlockMap)
      LOG.debug("Plugin's download percent is = %.2f".format(downloadPercent * 100))
      if (downloadPercent > MAXIMUM_DOWNLOAD_PERCENT) {
        throw IOException(IdeBundle.message("too.large.download.size"))
      }

      val file = getPluginTempFile()
      val merger = PluginChunkMerger(prevPluginArchive.toFile(), oldBlockMap, newBlockMap, indicator)
      FileOutputStream(file).use { output -> merger.merge(output, PluginChunkDataSource(oldBlockMap, newBlockMap, pluginFileUrl)) }

      val curFileHash = FileInputStream(file).use { input -> FileHash(input, newPluginHash.algorithm) }
      if (curFileHash != newPluginHash) {
        throw IOException(IdeBundle.message("hashes.doesnt.match"))
      }

      if (pluginFileUrl.endsWith(".zip")) {
        return renameFileToZipRoot(file)
      }
      else {
        val connection = HttpRequests.request(curPluginUrl).productNameAsUserAgent().connect { request -> request.connection }
        return guessPluginFile(connection, file, pluginUrl)
      }
    }

    private fun guessPluginFile(connection: URLConnection, file: File, pluginUrl: String): File {
      val fileName: String = guessFileName(connection, file, pluginUrl)
      val newFile = File(file.parentFile, fileName)
      FileUtil.rename(file, newFile)
      return newFile
    }

    @Throws(IOException::class)
    private fun guessFileName(connection: URLConnection, file: File, pluginUrl: String): String {
      var fileName: String? = null
      val contentDisposition = connection.getHeaderField("Content-Disposition")
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
        val usedURL = connection.url.toString()
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

    private fun renameFileToZipRoot(zip: File): File {
      val newName = "${PluginInstaller.rootEntryName(zip)}.zip"
      val newZip = File("${zip.parent}/$newName")
      if (newZip.exists()) {
        FileUtil.delete(newZip)
      }
      FileUtil.rename(zip, newName)
      return newZip
    }

    private fun downloadPercent(oldBlockMap: BlockMap, newBlockMap: BlockMap): Double {
      val oldSet = oldBlockMap.chunks.toSet()
      val newChunks = newBlockMap.chunks.filter { chunk -> !oldSet.contains(chunk) }
      return newChunks.sumBy { chunk -> chunk.length }.toDouble() /
             newBlockMap.chunks.sumBy { chunk -> chunk.length }.toDouble()
    }

    private fun getPluginFileUrl(pluginUrl: String): String {
      return HttpRequests.request(pluginUrl).productNameAsUserAgent().connect { request ->
        val url = request.connection.url
        "${url.protocol}://${url.host}${url.path}"
      }
    }

    private fun getPrevPluginArchive(prevPlugin: Path): Path {
      val suffix = if (prevPlugin.endsWith(".jar")) "" else ".zip"
      return Paths.get("${PathManager.getPluginTempPath()}\\${prevPlugin.fileName}$suffix")
    }

    @Throws(IOException::class)
    private fun getPluginTempFile(): File {
      val pluginsTemp = File(PathManager.getPluginTempPath())
      if (!pluginsTemp.exists() && !pluginsTemp.mkdirs()) {
        throw IOException(IdeBundle.message("error.cannot.create.temp.dir", pluginsTemp))
      }
      return FileUtil.createTempFile(pluginsTemp, "plugin_", "_download", true, false)
    }
  }
}
