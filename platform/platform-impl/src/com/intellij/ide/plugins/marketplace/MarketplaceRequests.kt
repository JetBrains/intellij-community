// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginInfoProvider
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import com.intellij.util.io.exists
import com.intellij.util.io.write
import com.intellij.util.ui.IoErrorText
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.Future
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

private val LOG = logger<MarketplaceRequests>()
private const val FULL_PLUGINS_XML_IDS_FILENAME = "pluginsXMLIds.json"

@ApiStatus.Internal
class MarketplaceRequests : PluginInfoProvider {
  companion object {

    private val objectMapper by lazy { ObjectMapper() }

    private val applicationInfo
      get() = ApplicationInfoImpl.getShadowInstanceImpl()

    private val PLUGIN_MANAGER_URL = applicationInfo.pluginManagerUrl.trimEnd('/')

    private val COMPATIBLE_UPDATE_URL = "${PLUGIN_MANAGER_URL}/api/search/compatibleUpdates"

    @JvmStatic
    val Instance
      get() = PluginInfoProvider.getInstance() as MarketplaceRequests

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

    @JvmStatic
    @JvmOverloads
    fun loadLastCompatiblePluginDescriptors(
      pluginIds: Set<PluginId>,
      buildNumber: BuildNumber? = null,
    ): List<PluginNode> {
      return getLastCompatiblePluginUpdate(pluginIds, buildNumber)
        .map { loadPluginDescriptor(it.pluginId, it, null) }
    }

    @JvmStatic
    @JvmOverloads
    fun getLastCompatiblePluginUpdate(
      ids: Set<PluginId>,
      buildNumber: BuildNumber? = null,
    ): List<IdeCompatibleUpdate> {
      try {
        if (ids.isEmpty()) {
          return emptyList()
        }

        val data = objectMapper.writeValueAsString(CompatibleUpdateRequest(ids, buildNumber))
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

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    internal fun loadPluginDescriptor(
      xmlId: String,
      ideCompatibleUpdate: IdeCompatibleUpdate,
      indicator: ProgressIndicator? = null,
    ): PluginNode {
      val updateMetadataFile = Paths.get(PathManager.getPluginsPath(), "meta")
      return readOrUpdateFile(
        updateMetadataFile.resolve(ideCompatibleUpdate.externalUpdateId + ".json"),
        "$PLUGIN_MANAGER_URL/files/${ideCompatibleUpdate.externalPluginId}/${ideCompatibleUpdate.externalUpdateId}/meta.json",
        indicator,
        IdeBundle.message("progress.downloading.plugins.meta", xmlId)
      ) {
        objectMapper.readValue(it, IntellijUpdateMetadata::class.java)
      }.toPluginNode()
    }

    @JvmStatic
    @JvmName("readOrUpdateFile")
    @Throws(IOException::class)
    internal fun <T> readOrUpdateFile(
      file: Path?,
      url: String,
      indicator: ProgressIndicator?,
      @Nls indicatorMessage: String,
      parser: (Reader) -> T
    ): T {
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

            synchronized(this) {
              request.saveToFile(file, indicator)
              connection.getHeaderField("ETag")?.let { saveETagForFile(file, it) }
            }
            return@connect Files.newBufferedReader(file).use(parser)
          }
          catch (e: HttpRequests.HttpStatusException) {
            LOG.warnWithDebug("Cannot load data from ${url} (statusCode=${e.statusCode})", e)
            throw e
          }
          catch (e: Exception) {
            LOG.warnWithDebug("Error reading Marketplace file: ${e} (file=${file} URL=${url})", e)
            if (file != null && LOG.isDebugEnabled) {
              LOG.debug("File content:\n${runCatching { Files.readString(file) }.getOrElse { IoErrorText.message(e) }}")
            }
            throw e
          }
        }
    }
  }

  private val IDE_BUILD_FOR_REQUEST = URLUtil.encodeURIComponent(applicationInfo.pluginsCompatibleBuild)

  private val MARKETPLACE_ORGANIZATIONS_URL = Urls.newFromEncoded("${PLUGIN_MANAGER_URL}/api/search/aggregation/organizations")
    .addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private val JETBRAINS_PLUGINS_URL = Urls.newFromEncoded(
    "${PLUGIN_MANAGER_URL}/api/search/plugins?organization=JetBrains&max=1000"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

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

  internal fun getFeatures(
    featureType: String,
    implementationName: String,
  ): List<FeatureImpl> {
    val param = mapOf(
      "featureType" to featureType,
      "implementationName" to implementationName,
      "build" to applicationInfo.pluginsCompatibleBuild,
    )
    return getFeatures(param)
  }

  @RequiresBackgroundThread
  @JvmOverloads
  @Throws(IOException::class)
  fun getMarketplacePlugins(indicator: ProgressIndicator? = null): Set<PluginId> {
    return readOrUpdateFile(
      Paths.get(PathManager.getPluginsPath(), FULL_PLUGINS_XML_IDS_FILENAME),
      "${PLUGIN_MANAGER_URL}/files/$FULL_PLUGINS_XML_IDS_FILENAME",
      indicator,
      IdeBundle.message("progress.downloading.available.plugins"),
      ::parseXmlIds,
    )
  }

  override fun loadPlugins(indicator: ProgressIndicator?): Future<Set<PluginId>> {
    return ApplicationManager.getApplication().executeOnPooledThread(Callable {
      try {
        getMarketplacePlugins(indicator)
      }
      catch (e: IOException) {
        logWarnOrPrintIfDebug("Cannot get plugins from Marketplace", e)
        emptySet()
      }
    })
  }

  override fun loadCachedPlugins(): Set<PluginId>? {
    val pluginXmlIdsFile = Paths.get(PathManager.getPluginsPath(), FULL_PLUGINS_XML_IDS_FILENAME)
    try {
      if (Files.size(pluginXmlIdsFile) > 0) {
        return Files.newBufferedReader(pluginXmlIdsFile).use(::parseXmlIds)
      }
    }
    catch (ignore: IOException) {
    }
    return null
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

  fun getBrokenPlugins(currentBuild: BuildNumber): Map<PluginId, Set<String>> {
    val brokenPlugins = try {
      readOrUpdateFile(
        Paths.get(PathManager.getPluginsPath(), "brokenPlugins.json"),
        "${PLUGIN_MANAGER_URL}/files/brokenPlugins.json",
        null,
        ""
      ) { objectMapper.readValue(it, object : TypeReference<List<MarketplaceBrokenPlugin>>() {}) }
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get broken plugins file from Marketplace", e)
      return emptyMap()
    }

    val brokenPluginsMap = HashMap<PluginId, MutableSet<String>>()
    brokenPlugins.forEach { record ->
      try {
        val parsedOriginalUntil = record.originalUntil?.trim()
        val parsedOriginalSince = record.originalSince?.trim()
        if (!parsedOriginalUntil.isNullOrEmpty() && !parsedOriginalSince.isNullOrEmpty()) {
          val originalUntil = BuildNumber.fromString(parsedOriginalUntil, record.id, null) ?: currentBuild
          val originalSince = BuildNumber.fromString(parsedOriginalSince, record.id, null) ?: currentBuild
          val until = BuildNumber.fromString(record.until) ?: currentBuild
          val since = BuildNumber.fromString(record.since) ?: currentBuild
          if (currentBuild in originalSince..originalUntil && currentBuild !in since..until) {
            brokenPluginsMap.computeIfAbsent(PluginId.getId(record.id)) { HashSet() }.add(record.version)
          }
        }
      }
      catch (e: Exception) {
        LOG.error("cannot parse ${record}", e)
      }
    }
    return brokenPluginsMap
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

  @Deprecated("Please use `PluginId`", replaceWith = ReplaceWith("getLastCompatiblePluginUpdate(PluginId.get(id), buildNumber, indicator)"))
  @RequiresBackgroundThread
  @JvmOverloads
  fun getLastCompatiblePluginUpdate(
    id: String,
    buildNumber: BuildNumber? = null,
    indicator: ProgressIndicator? = null,
  ): PluginNode? = getLastCompatiblePluginUpdate(PluginId.getId(id), buildNumber, indicator)

  @RequiresBackgroundThread
  @JvmOverloads
  fun getLastCompatiblePluginUpdate(
    pluginId: PluginId,
    buildNumber: BuildNumber? = null,
    indicator: ProgressIndicator? = null,
  ): PluginNode? {
    return getLastCompatiblePluginUpdate(setOf(pluginId), buildNumber).firstOrNull()
      ?.let { loadPluginDescriptor(pluginId.idString, it, indicator) }
  }

  fun getCompatibleUpdateByModule(module: String): PluginId? {
    try {
      val data = objectMapper.writeValueAsString(CompatibleUpdateForModuleRequest(module))

      return HttpRequests.post(
        Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm(),
        HttpRequests.JSON_CONTENT_TYPE,
      ).productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          it.write(data)
          objectMapper.readValue(it.inputStream, object : TypeReference<List<IdeCompatibleUpdate>>() {})
        }.firstOrNull()
        ?.pluginId
        ?.let { PluginId.getId(it) }
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get compatible update by module from Marketplace", e)
      return null
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
            .toCollection(HashSet())
        }
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get JetBrains plugins' IDs from Marketplace", e)
      null
    }
  }

  private fun parseXmlIds(reader: Reader) = objectMapper.readValue(reader, object : TypeReference<Set<PluginId>>() {})

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

private data class CompatibleUpdateRequest(
  val build: String,
  val pluginXMLIds: List<String>,
) {

  @JvmOverloads
  constructor(
    pluginIds: Set<PluginId>,
    buildNumber: BuildNumber? = null,
  ) : this(
    ApplicationInfoImpl.orFromPluginsCompatibleBuild(buildNumber),
    pluginIds.map { it.idString },
  )
}

private data class CompatibleUpdateForModuleRequest(
  val module: String,
  val build: String,
) {

  @JvmOverloads
  constructor(
    module: String,
    buildNumber: BuildNumber? = null,
  ) : this(
    module,
    ApplicationInfoImpl.orFromPluginsCompatibleBuild(buildNumber),
  )
}

private fun logWarnOrPrintIfDebug(message: String, throwable: Throwable) {
  if (LOG.isDebugEnabled) {
    LOG.debug(message, throwable)
  }
  else {
    LOG.warn("${message}: ${throwable.message}")
  }
}
