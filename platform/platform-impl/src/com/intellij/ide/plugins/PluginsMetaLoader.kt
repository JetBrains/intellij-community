// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.IdeCompatibleUpdate
import com.intellij.openapi.updateSettings.IntellijUpdateMetadata
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

@ApiStatus.Internal
object PluginsMetaLoader {

  private val LOG = Logger.getInstance(PluginsMetaLoader::class.java)

  private const val TAG_EXT = ".etag"

  private const val FULL_PLUGINS_XML_IDS_FILENAME = "pluginsXMLIds.json"

  private val AVAILABLE_PLUGINS_XML_IDS_URL = "${ApplicationInfoImpl.getShadowInstance().pluginManagerUrl}/files/$FULL_PLUGINS_XML_IDS_FILENAME"

  private val COMPATIBLE_UPDATE_URL = "${ApplicationInfoImpl.getShadowInstance().pluginManagerUrl.trimEnd('/')}/api/search/compatibleUpdates"

  private val objectMapper = ObjectMapper()

  private fun getUpdatesMetadataFilesDirectory() = File(PathManager.getPluginsPath()).resolve("meta")

  private fun getUpdateMetadataFile(update: IdeCompatibleUpdate) = getUpdatesMetadataFilesDirectory().resolve(
    update.externalUpdateId + ".json")

  private fun getUpdateMetadataUrl(update: IdeCompatibleUpdate) =
    "${ApplicationInfoImpl.getShadowInstance().pluginManagerUrl}/files/${update.externalPluginId}/${update.externalUpdateId}/meta.json"

  fun getMarketplacePlugins(indicator: ProgressIndicator?): List<String> {
    val pluginXmlIdsFile = File(PathManager.getPluginsPath(), FULL_PLUGINS_XML_IDS_FILENAME)
    return readOrUpdateFile(
      pluginXmlIdsFile,
      AVAILABLE_PLUGINS_XML_IDS_URL,
      indicator,
      IdeBundle.message("progress.downloading.available.plugins"),
      ::parseXmlIds
    )
  }

  fun loadPluginDescriptor(xmlId: String, ideCompatibleUpdate: IdeCompatibleUpdate, indicator: ProgressIndicator?): PluginNode {
    return readOrUpdateFile(
      getUpdateMetadataFile(ideCompatibleUpdate),
      getUpdateMetadataUrl(ideCompatibleUpdate),
      indicator,
      IdeBundle.message("progress.downloading.plugins.meta", xmlId),
      ::parseJsonPluginMeta
    ).toPluginNode()
  }

  @JvmStatic
  @Throws(IOException::class)
  fun <T> readOrUpdateFile(
    file: File?,
    url: String,
    indicator: ProgressIndicator?,
    indicatorMessage: String,
    parser: (Reader) -> T
  ): T {
    val eTag = if (file != null) loadEtagForFile(file) else null
    return HttpRequests
      .request(url)
      .tuner { connection -> eTag?.also { connection.setRequestProperty("If-None-Match", it) } }
      .productNameAsUserAgent()
      .connect { request ->
        indicator?.checkCanceled()
        val connection = request.connection
        if (file != null && file.length() > 0 && connection is HttpURLConnection && connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
          return@connect file.bufferedReader().use(parser)
        }
        if (indicator != null) {
          indicator.checkCanceled()
          indicator.text2 = indicatorMessage
        }
        if (file != null) {
          synchronized(PluginsMetaLoader) {
            request.saveToFile(file, indicator)
            connection.getHeaderField("ETag")?.let { saveETagForFile(file, it) }
          }
          return@connect file.bufferedReader().use(parser)
        }
        else {
          return@connect request.reader.use(parser)
        }
      }
  }

  fun getLastCompatiblePluginUpdate(ids: List<String>, buildNumber: BuildNumber?): List<IdeCompatibleUpdate> {
    val data = objectMapper.writeValueAsString(CompatibleUpdateRequest(PluginDownloader.getBuildNumberForDownload(buildNumber), ids))
    val url = Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm()
    return HttpRequests
      .post(url, HttpRequests.JSON_CONTENT_TYPE)
      .productNameAsUserAgent()
      .connect {
        it.write(data)
        objectMapper
          .readValue(
            it.inputStream,
            object : TypeReference<List<IdeCompatibleUpdate>>() {}
          )
      }
  }

  @JvmStatic
  fun parsePluginList(reader: Reader): List<PluginNode> {
    try {
      val parser = SAXParserFactory.newInstance().newSAXParser()
      val handler = RepositoryContentHandler()
      parser.parse(InputSource(reader), handler)
      return handler.pluginsList
    }
    catch (e: Exception) {
      when (e) {
        is ParserConfigurationException, is SAXException, is RuntimeException -> throw IOException(e)
        else -> throw e
      }
    }
  }

  private fun parseXmlIds(reader: Reader) = objectMapper.readValue(reader, object : TypeReference<List<String>>() {})

  private fun parseJsonPluginMeta(reader: Reader) = objectMapper.readValue(reader, IntellijUpdateMetadata::class.java)

  private fun getETagFile(file: File): File = file.resolveSibling(file.name + TAG_EXT)

  private fun loadEtagForFile(file: File): String? {
    val eTagFile = getETagFile(file)
    if (eTagFile.exists()) {
      try {
        val lines = eTagFile.readLines()
        if (lines.size != 1) {
          LOG.warn("Can't load ETag from '" + eTagFile.absolutePath + "'. Unexpected number of lines: " + lines.size)
          FileUtil.delete(eTagFile)
        }
        else {
          return lines[0]
        }
      }
      catch (e: IOException) {
        LOG.warn("Can't load ETag from '" + eTagFile.absolutePath + "'", e)
      }
    }
    return ""
  }

  private fun saveETagForFile(file: File, eTag: String) {
    val eTagFile = getETagFile(file)
    try {
      eTagFile.writeText(eTag)
    }
    catch (e: IOException) {
      LOG.warn("Can't save ETag to '" + eTagFile.absolutePath + "'", e)
    }
  }

  private data class CompatibleUpdateRequest(val build: String, val pluginXMLIds: List<String>)

}