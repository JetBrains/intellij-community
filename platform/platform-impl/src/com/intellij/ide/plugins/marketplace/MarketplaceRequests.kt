// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URLConnection
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

@ApiStatus.Internal
open class MarketplaceRequests {

  private val LOG = Logger.getInstance(MarketplaceRequests::class.java)

  companion object {
    private const val TAG_EXT = ".etag"

    private const val FULL_PLUGINS_XML_IDS_FILENAME = "pluginsXMLIds.json"

    private const val FILENAME = "filename="

    private val INSTANCE = MarketplaceRequests()

    @JvmStatic
    fun getInstance(): MarketplaceRequests {
      return INSTANCE
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
  }

  private val PLUGIN_MANAGER_URL = ApplicationInfoImpl.getShadowInstance().pluginManagerUrl.trimEnd('/')

  private val AVAILABLE_PLUGINS_XML_IDS_URL = "${PLUGIN_MANAGER_URL}/files/$FULL_PLUGINS_XML_IDS_FILENAME"

  private val IDE_BUILD_FOR_REQUEST = URLUtil.encodeURIComponent(getBuildForPluginRepositoryRequests())

  private val MARKETPLACE_ORGANIZATIONS_URL = Urls.newFromEncoded(
    "${PLUGIN_MANAGER_URL}/api/search/aggregation/organizations"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private val MARKETPLACE_TAGS_URL = Urls.newFromEncoded(
    "${PLUGIN_MANAGER_URL}/api/search/aggregation/tags"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private val COMPATIBLE_UPDATE_URL = "${PLUGIN_MANAGER_URL}/api/search/compatibleUpdates"

  private val objectMapper by lazy { ObjectMapper() }

  private fun getUpdatesMetadataFilesDirectory() = File(PathManager.getPluginsPath()).resolve("meta")

  internal fun getBrokenPluginsFile() = File(PathManager.getPluginsPath()).resolve("brokenPlugins.json")

  private fun getUpdateMetadataFile(update: IdeCompatibleUpdate) = getUpdatesMetadataFilesDirectory().resolve(
    update.externalUpdateId + ".json")

  private fun getUpdateMetadataUrl(update: IdeCompatibleUpdate) =
    "${PLUGIN_MANAGER_URL}/files/${update.externalPluginId}/${update.externalUpdateId}/meta.json"

  private fun createSearchUrl(query: String, count: Int): Url = Urls.newFromEncoded(
    "$PLUGIN_MANAGER_URL/api/search/plugins?$query&build=$IDE_BUILD_FOR_REQUEST&max=$count"
  )

  private fun createFeatureUrl(param: Map<String, String>) = Urls.newFromEncoded(
    "${PLUGIN_MANAGER_URL}/feature/getImplementations"
  ).addParameters(param)

  private val BROKEN_PLUGIN_PATH = "${PLUGIN_MANAGER_URL}/files/brokenPlugins.json"

  fun getFeatures(param: Map<String, String>): List<FeatureImpl> = try {
    if (param.isEmpty()) emptyList()
    else {
      HttpRequests
        .request(createFeatureUrl(param))
        .throwStatusCodeException(false)
        .productNameAsUserAgent()
        .connect {
          objectMapper.readValue(
            it.inputStream,
            object : TypeReference<List<FeatureImpl>>() {}
          )
        }
    }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get features from Marketplace", e)
    emptyList()
  }

  @Throws(IOException::class)
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

  @Throws(IOException::class)
  fun getMarketplaceCachedPlugins(): List<String>? {
    val pluginXmlIdsFile = File(PathManager.getPluginsPath(), FULL_PLUGINS_XML_IDS_FILENAME)
    return if (pluginXmlIdsFile.length() > 0) pluginXmlIdsFile.bufferedReader().use(::parseXmlIds) else null
  }

  fun getBuildForPluginRepositoryRequests(): String {
    val instance = ApplicationInfoImpl.getShadowInstance()
    val compatibleBuild = PluginManagerCore.getPluginsCompatibleBuild()
    return if (compatibleBuild != null) {
      BuildNumber.fromStringWithProductCode(
        compatibleBuild,
        instance.build.productCode
      )!!.asString()
    }
    else instance.apiVersion
  }

  @Throws(IOException::class)
  fun searchPlugins(query: String, count: Int): List<PluginNode> {
    val marketplaceSearchPluginData = HttpRequests.request(createSearchUrl(query, count))
      .throwStatusCodeException(false)
      .connect {
        objectMapper.readValue(
          it.inputStream,
          object : TypeReference<List<MarketplaceSearchPluginData>>() {}
        )
      }
    // Marketplace Search Service can produce objects without "externalUpdateId". It means that an update is not in the search index yet.
    return marketplaceSearchPluginData.filter { it.externalUpdateId != null }.map { it.toPluginNode() }
  }

  fun getAllPluginsVendors(): List<String> = try {
    HttpRequests
      .request(MARKETPLACE_ORGANIZATIONS_URL)
      .productNameAsUserAgent()
      .throwStatusCodeException(false)
      .connect {
        objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toList()
      }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get organizations from Marketplace", e)
    emptyList()
  }

  fun getBrokenPlugins(): List<MarketplaceBrokenPlugin> {
    return try {
      readOrUpdateFile(
        getBrokenPluginsFile(),
        BROKEN_PLUGIN_PATH,
        null,
        "",
        ::parseBrokenPlugins
      )
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get broken plugins file from Marketplace", e)
      emptyList()
    }
  }


  fun getAllPluginsTags(): List<String> = try {
    HttpRequests
      .request(MARKETPLACE_TAGS_URL)
      .productNameAsUserAgent()
      .throwStatusCodeException(false)
      .connect {
        objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toList()
      }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get tags from Marketplace", e)
    emptyList()
  }

  @Throws(IOException::class)
  fun loadPluginDescriptor(xmlId: String, ideCompatibleUpdate: IdeCompatibleUpdate, indicator: ProgressIndicator? = null): PluginNode {
    return readOrUpdateFile(
      getUpdateMetadataFile(ideCompatibleUpdate),
      getUpdateMetadataUrl(ideCompatibleUpdate),
      indicator,
      IdeBundle.message("progress.downloading.plugins.meta", xmlId),
      ::parseJsonPluginMeta
    ).toPluginNode()
  }

  @JvmOverloads
  fun loadLastCompatiblePluginDescriptors(ids: List<String>, buildNumber: BuildNumber? = null): List<PluginNode> {
    if (ids.isEmpty()) return emptyList()
    val data: List<IdeCompatibleUpdate> = getLastCompatiblePluginUpdate(ids, buildNumber)
    return data.map { loadPluginDescriptor(it.pluginId, it, null) }
  }


  fun loadPluginDetails(pluginNode: PluginNode): PluginNode {
    val externalPluginId = pluginNode.externalPluginId
    val externalUpdateId = pluginNode.externalUpdateId
    if (externalPluginId == null || externalUpdateId == null) return pluginNode
    val ideCompatibleUpdate = IdeCompatibleUpdate(externalUpdateId = externalUpdateId, externalPluginId = externalPluginId)
    return loadPluginDescriptor(pluginNode.pluginId.idString, ideCompatibleUpdate)
  }

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
      .tuner { connection -> connection.setUpETag(eTag) }
      .productNameAsUserAgent()
      .connect { request ->
        indicator?.checkCanceled()
        val connection = request.connection
        if (file != null && connection.isNotModified(file)) {
          return@connect file.bufferedReader().use(parser)
        }
        if (indicator != null) {
          indicator.checkCanceled()
          indicator.text2 = indicatorMessage
        }
        if (file != null) {
          synchronized(INSTANCE) {
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

  fun getLastCompatiblePluginUpdate(ids: List<String>, buildNumber: BuildNumber? = null): List<IdeCompatibleUpdate> = try {
    if (ids.isEmpty()) emptyList()
    else {
      val data = objectMapper.writeValueAsString(CompatibleUpdateRequest(PluginDownloader.getBuildNumberForDownload(buildNumber), ids))
      val url = Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm()
      HttpRequests
        .post(url, HttpRequests.JSON_CONTENT_TYPE)
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          it.write(data)
          objectMapper
            .readValue(
              it.inputStream,
              object : TypeReference<List<IdeCompatibleUpdate>>() {}
            )
        }
    }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get compatible updates from Marketplace", e)
    emptyList()
  }

  @JvmOverloads
  fun getLastCompatiblePluginUpdate(id: String, buildNumber: BuildNumber? = null, indicator: ProgressIndicator? = null): PluginNode? {
    val data = getLastCompatiblePluginUpdate(listOf(id), buildNumber).firstOrNull()
    return data?.let { loadPluginDescriptor(id, it, indicator) }
  }

  @JvmOverloads
  fun getCompatibleUpdatesByModule(module: String, buildNumber: BuildNumber? = null): List<IdeCompatibleUpdate> = try {
    val data = objectMapper.writeValueAsString(
      CompatibleUpdateForModuleRequest(PluginDownloader.getBuildNumberForDownload(buildNumber), module)
    )
    val url = Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm()
    HttpRequests
      .post(url, HttpRequests.JSON_CONTENT_TYPE)
      .productNameAsUserAgent()
      .throwStatusCodeException(false)
      .connect {
        it.write(data)
        objectMapper
          .readValue(
            it.inputStream,
            object : TypeReference<List<IdeCompatibleUpdate>>() {}
          )
      }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get compatible update by module from Marketplace", e)
    emptyList()
  }

  private fun parseBrokenPlugins(reader: Reader) = objectMapper.readValue(
    reader,
    object : TypeReference<List<MarketplaceBrokenPlugin>>() {}
  )

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

  @Throws(IOException::class)
  open fun download(pluginUrl: String, indicator: ProgressIndicator): File {
    val pluginsTemp = File(PathManager.getPluginTempPath())
    if (!pluginsTemp.exists() && !pluginsTemp.mkdirs()) {
      throw IOException(IdeBundle.message("error.cannot.create.temp.dir", pluginsTemp))
    }
    val file = FileUtil.createTempFile(pluginsTemp, "plugin_", "_download", true, false)
    return HttpRequests.request(pluginUrl).gzip(false).productNameAsUserAgent().connect(
      HttpRequests.RequestProcessor { request: HttpRequests.Request ->
        request.saveToFile(file, indicator)
        val fileName: String = guessFileName(request.connection, file, pluginUrl)
        val newFile = File(file.parentFile, fileName)
        FileUtil.rename(file, newFile)
        newFile
      })
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

  private fun URLConnection.setUpETag(eTag: String?) {
    eTag?.also { this.setRequestProperty("If-None-Match", it) }
  }

  private fun URLConnection.isNotModified(file: File?): Boolean =
    file != null && file.length() > 0 && this is HttpURLConnection && this.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED

  private data class CompatibleUpdateRequest(val build: String, val pluginXMLIds: List<String>)
  private data class CompatibleUpdateForModuleRequest(val build: String, val module: String)

  private fun logWarnOrPrintIfDebug(message: String, throwable: Throwable) {
    if (LOG.isDebugEnabled) {
      LOG.debug(message, throwable)
    }
    else {
      LOG.warn("$message:${throwable.message}")
    }
  }

}