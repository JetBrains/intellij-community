// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.*
import com.intellij.util.ui.IoErrorText
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

private val LOG = logger<MarketplaceRequests>()
private const val FULL_PLUGINS_XML_IDS_FILENAME = "pluginsXMLIds.json"

@ApiStatus.Internal
open class MarketplaceRequests {
  companion object {
    private val INSTANCE = MarketplaceRequests()

    @JvmStatic
    fun getInstance(): MarketplaceRequests = INSTANCE

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

  private val IDE_BUILD_FOR_REQUEST = URLUtil.encodeURIComponent(getBuildForPluginRepositoryRequests())

  private val MARKETPLACE_ORGANIZATIONS_URL = Urls.newFromEncoded("${PLUGIN_MANAGER_URL}/api/search/aggregation/organizations")
    .addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private val JETBRAINS_PLUGINS_URL = Urls.newFromEncoded(
    "${PLUGIN_MANAGER_URL}/api/search/plugins?organization=JetBrains&max=1000"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private val COMPATIBLE_UPDATE_URL = "${PLUGIN_MANAGER_URL}/api/search/compatibleUpdates"

  private val objectMapper by lazy { ObjectMapper() }

  private fun getUpdatesMetadataFilesDirectory(): Path = Paths.get(PathManager.getPluginsPath(), "meta")

  internal fun getBrokenPluginsFile(): Path = Paths.get(PathManager.getPluginsPath(), "brokenPlugins.json")

  private fun getUpdateMetadataFile(update: IdeCompatibleUpdate): Path {
    return getUpdatesMetadataFilesDirectory().resolve(update.externalUpdateId + ".json")
  }

  private fun getUpdateMetadataUrl(update: IdeCompatibleUpdate): String {
    return "${PLUGIN_MANAGER_URL}/files/${update.externalPluginId}/${update.externalUpdateId}/meta.json"
  }

  private fun createSearchUrl(query: String, count: Int): Url {
    return Urls.newFromEncoded("$PLUGIN_MANAGER_URL/api/search/plugins?$query&build=$IDE_BUILD_FOR_REQUEST&max=$count")
  }

  private fun createFeatureUrl(param: Map<String, String>): Url {
    return Urls.newFromEncoded("${PLUGIN_MANAGER_URL}/feature/getImplementations").addParameters(param)
  }

  fun getFeatures(param: Map<String, String>): List<FeatureImpl> {
    if (param.isEmpty()) {
      return emptyList()
    }

    try {
      return HttpRequests
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
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get features from Marketplace", e)
      return emptyList()
    }
  }

  @Throws(IOException::class)
  fun getMarketplacePlugins(indicator: ProgressIndicator?): List<String> {
    val pluginXmlIdsFile = Paths.get(PathManager.getPluginsPath(), FULL_PLUGINS_XML_IDS_FILENAME)
    return readOrUpdateFile(
      pluginXmlIdsFile,
      "${PLUGIN_MANAGER_URL}/files/$FULL_PLUGINS_XML_IDS_FILENAME",
      indicator,
      IdeBundle.message("progress.downloading.available.plugins"),
      ::parseXmlIds
    )
  }

  @Throws(IOException::class)
  fun getMarketplaceCachedPlugins(): List<String>? {
    val pluginXmlIdsFile = Paths.get(PathManager.getPluginsPath(), FULL_PLUGINS_XML_IDS_FILENAME)
    try {
      if (Files.size(pluginXmlIdsFile) > 0) {
        return Files.newBufferedReader(pluginXmlIdsFile).use(::parseXmlIds)
      }
    }
    catch (ignore: NoSuchFileException) {
    }
    return null
  }

  fun getBuildForPluginRepositoryRequests(): String {
    val appInfo = ApplicationInfoImpl.getShadowInstance()
    val compatibleBuild = PluginManagerCore.getPluginsCompatibleBuild() ?: appInfo.apiVersion
    return BuildNumber.fromStringWithProductCode(compatibleBuild, appInfo.build.productCode)!!.asString()
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

  fun getAllPluginsVendors(): List<String> {
    try {
      return HttpRequests
        .request(MARKETPLACE_ORGANIZATIONS_URL)
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toList()
        }
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get organizations from Marketplace", e)
      return emptyList()
    }
  }

  fun getBrokenPlugins(): List<MarketplaceBrokenPlugin> {
    try {
      return readOrUpdateFile(
        getBrokenPluginsFile(),
        "${PLUGIN_MANAGER_URL}/files/brokenPlugins.json",
        null,
        ""
      ) { objectMapper.readValue(it, object : TypeReference<List<MarketplaceBrokenPlugin>>() {}) }
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get broken plugins file from Marketplace", e)
      return emptyList()
    }
  }

  fun getAllPluginsTags(): List<String> {
    try {
      return HttpRequests
        .request(Urls.newFromEncoded(
            "${PLUGIN_MANAGER_URL}/api/search/aggregation/tags"
          ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST)))
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toList()
        }
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get tags from Marketplace", e)
      return emptyList()
    }
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
    if (ids.isEmpty()) {
      return emptyList()
    }
    return getLastCompatiblePluginUpdate(ids, buildNumber).map { loadPluginDescriptor(it.pluginId, it, null) }
  }

  fun loadPluginDetails(pluginNode: PluginNode): PluginNode {
    val externalPluginId = pluginNode.externalPluginId
    val externalUpdateId = pluginNode.externalUpdateId
    if (externalPluginId == null || externalUpdateId == null) return pluginNode
    val ideCompatibleUpdate = IdeCompatibleUpdate(externalUpdateId = externalUpdateId, externalPluginId = externalPluginId)
    return loadPluginDescriptor(pluginNode.pluginId.idString, ideCompatibleUpdate).apply {
      // these three fields are not present in `IntellijUpdateMetadata`, but present in `MarketplaceSearchPluginData`
      rating = pluginNode.rating
      downloads = pluginNode.downloads
      date = pluginNode.date
    }
  }

  @Throws(IOException::class)
  fun <T> readOrUpdateFile(file: Path?,
                           url: String,
                           indicator: ProgressIndicator?,
                           @Nls indicatorMessage: String,
                           parser: (Reader) -> T): T {
    val eTag = if (file == null) null else loadETagForFile(file)
    return HttpRequests
      .request(url)
      .tuner { connection ->
        if (eTag != null) {
          connection.setRequestProperty("If-None-Match", eTag)
        }
      }
      .productNameAsUserAgent()
      .connect { request ->
        try {
          indicator?.checkCanceled()
          val connection = request.connection
          if (file != null && isNotModified(connection, file)) {
            return@connect Files.newBufferedReader(file).use(parser)
          }

          if (indicator != null) {
            indicator.checkCanceled()
            indicator.text2 = indicatorMessage
          }
          if (file == null) {
            return@connect request.reader.use(parser)
          }

          synchronized(INSTANCE) {
            request.saveToFile(file, indicator)
            connection.getHeaderField("ETag")?.let { saveETagForFile(file, it) }
          }
          return@connect Files.newBufferedReader(file).use(parser)
        }
        catch (e: HttpRequests.HttpStatusException) {
          LOG.warn("Cannot load data from $url (statusCode=${e.statusCode})", e)
          throw e
        }
        catch (e: Exception) {
          LOG.warn("Error reading Marketplace file: url=${url} file=${file?.fileName}", e)
          if (file != null && LOG.isDebugEnabled) {
            LOG.debug("File content:\n${try { Files.readString(file) } catch (e: Exception) { IoErrorText.message(e) } }")
          }
          throw e
        }
      }
  }

  fun getLastCompatiblePluginUpdate(ids: List<String>, buildNumber: BuildNumber? = null): List<IdeCompatibleUpdate> {
    try {
      if (ids.isEmpty()) {
        return emptyList()
      }

      val data = objectMapper.writeValueAsString(CompatibleUpdateRequest(PluginDownloader.getBuildNumberForDownload(buildNumber), ids))
      return HttpRequests
        .post(Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm(), HttpRequests.JSON_CONTENT_TYPE)
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          it.write(data)
          objectMapper.readValue(it.inputStream, object : TypeReference<List<IdeCompatibleUpdate>>() {})
        }
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get compatible updates from Marketplace", e)
      return emptyList()
    }
  }

  @JvmOverloads
  fun getLastCompatiblePluginUpdate(id: String, buildNumber: BuildNumber? = null, indicator: ProgressIndicator? = null): PluginNode? {
    val data = getLastCompatiblePluginUpdate(listOf(id), buildNumber).firstOrNull()
    return data?.let { loadPluginDescriptor(id, it, indicator) }
  }

  @JvmOverloads
  fun getCompatibleUpdatesByModule(module: String, buildNumber: BuildNumber? = null): List<IdeCompatibleUpdate> {
    try {
      val data = objectMapper.writeValueAsString(
        CompatibleUpdateForModuleRequest(PluginDownloader.getBuildNumberForDownload(buildNumber), module))
      val url = Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm()
      return HttpRequests
        .post(url, HttpRequests.JSON_CONTENT_TYPE)
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          it.write(data)
          objectMapper.readValue(it.inputStream, object : TypeReference<List<IdeCompatibleUpdate>>() {})
        }
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get compatible update by module from Marketplace", e)
      return emptyList()
    }
  }

  var jetBrainsPluginsIds: Set<String>? = null
    private set

  fun loadJetBrainsPluginsIds() {
    if (jetBrainsPluginsIds != null) {
      return
    }

    jetBrainsPluginsIds = try {
      HttpRequests
        .request(JETBRAINS_PLUGINS_URL)
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          objectMapper.readValue(it.inputStream, object : TypeReference<List<MarketplaceSearchPluginData>>() {})
            .asSequence()
            .map(MarketplaceSearchPluginData::id)
            .toSet()
        }
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get JetBrains plugins' IDs from Marketplace", e)
      null
    }
  }

  private fun parseXmlIds(reader: Reader) = objectMapper.readValue(reader, object : TypeReference<List<String>>() {})

  private fun parseJsonPluginMeta(reader: Reader) = objectMapper.readValue(reader, IntellijUpdateMetadata::class.java)

  @Throws(IOException::class)
  open fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator): File {
    return MarketplacePluginDownloadService.downloadPlugin(pluginUrl, indicator)
  }

  @Throws(IOException::class)
  open fun downloadPluginViaBlockMap(pluginUrl: String, prevPlugin: Path, indicator: ProgressIndicator): File {
    return MarketplacePluginDownloadService.downloadPluginViaBlockMap(pluginUrl, prevPlugin, indicator)
  }
}

private fun loadETagForFile(file: Path): String {
  val eTagFile = getETagFile(file)
  try {
    val lines = Files.readAllLines(eTagFile)
    if (lines.size == 1) {
      return lines[0]
    }

    LOG.warn("Can't load ETag from '" + eTagFile + "'. Unexpected number of lines: " + lines.size)
    Files.deleteIfExists(eTagFile)
  }
  catch (ignore: NoSuchFileException) {
  }
  catch (e: IOException) {
    LOG.warn("Can't load ETag from '$eTagFile'", e)
  }
  return ""
}

@Suppress("SpellCheckingInspection")
private fun getETagFile(file: Path): Path = file.parent.resolve("${file.fileName}.etag")

private fun saveETagForFile(file: Path, eTag: String) {
  val eTagFile = getETagFile(file)
  try {
    eTagFile.write(eTag)
  }
  catch (e: IOException) {
    LOG.warn("Can't save ETag to '$eTagFile'", e)
  }
}

private fun isNotModified(urlConnection: URLConnection, file: Path?): Boolean {
  return file != null && file.exists() && Files.size(file) > 0 &&
         urlConnection is HttpURLConnection && urlConnection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED
}

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
