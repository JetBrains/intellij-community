// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginInfoProvider
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.auth.PluginRepositoryAuthService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.io.*
import com.intellij.util.ui.IoErrorText
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
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

private val objectMapper by lazy { ObjectMapper() }
private val pluginManagerUrl by lazy(LazyThreadSafetyMode.PUBLICATION) { ApplicationInfoImpl.getShadowInstance().pluginManagerUrl.trimEnd('/') }
private val compatibleUpdateUrl: String
  get() = "${pluginManagerUrl}/api/search/compatibleUpdates"

@ApiStatus.Internal
class MarketplaceRequests : PluginInfoProvider {
  companion object {
    @JvmStatic
    fun getInstance(): MarketplaceRequests = PluginInfoProvider.getInstance() as MarketplaceRequests

    @JvmStatic
    fun parsePluginList(input: InputStream): List<PluginNode> {
      try {
        val handler = RepositoryContentHandler()
        SAXParserFactory.newDefaultInstance().newSAXParser().parse(InputSource(input), handler)
        return handler.pluginsList
      }
      catch (e: Exception) {
        when (e) {
          is ParserConfigurationException, is SAXException, is RuntimeException -> throw IOException(e)
          else -> throw e
        }
      }
    }

    @RequiresBackgroundThread
    @RequiresReadLockAbsence
    @JvmStatic
    @JvmOverloads
    fun loadLastCompatiblePluginDescriptors(
      pluginIds: Set<PluginId>,
      buildNumber: BuildNumber? = null,
    ): List<PluginNode> {
      return getLastCompatiblePluginUpdate(pluginIds, buildNumber)
        .map { loadPluginDescriptor(it.pluginId, it, null) }
    }

    @RequiresBackgroundThread
    @RequiresReadLockAbsence
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
          .post(Urls.newFromEncoded(compatibleUpdateUrl).toExternalForm(), HttpRequests.JSON_CONTENT_TYPE)
          .productNameAsUserAgent()
          .throwStatusCodeException(false)
          .connect {
            it.write(data)
            objectMapper.readValue(it.inputStream, object : TypeReference<List<IdeCompatibleUpdate>>() {})
          }
      }
      catch (e: Exception) {
        LOG.infoOrDebug("Can not get compatible updates from Marketplace", e)
        return emptyList()
      }
    }

    @RequiresBackgroundThread
    @RequiresReadLockAbsence
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    internal fun loadPluginDescriptor(
      xmlId: String,
      ideCompatibleUpdate: IdeCompatibleUpdate,
      indicator: ProgressIndicator? = null,
    ): PluginNode {
      val updateMetadataFile = Paths.get(PathManager.getPluginTempPath(), "meta")
      return readOrUpdateFile(
        updateMetadataFile.resolve(ideCompatibleUpdate.externalUpdateId + ".json"),
        "$pluginManagerUrl/files/${ideCompatibleUpdate.externalPluginId}/${ideCompatibleUpdate.externalUpdateId}/meta.json",
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
      parser: (InputStream) -> T
    ): T {
      val eTag = if (file == null) null else loadETagForFile(file)
      return HttpRequests
        .request(url)
        .tuner { connection ->
          if (eTag != null) {
            connection.setRequestProperty("If-None-Match", eTag)
          }
          if (ApplicationManager.getApplication() != null) {
            serviceOrNull<PluginRepositoryAuthService>()
              ?.connectionTuner
              ?.tune(connection)
          }
        }
        .productNameAsUserAgent()
        .connect { request ->
          try {
            indicator?.checkCanceled()
            val connection = request.connection
            if (file != null && isNotModified(connection, file)) {
              return@connect Files.newInputStream(file).use(parser)
            }

            if (indicator != null) {
              indicator.checkCanceled()
              indicator.text2 = indicatorMessage
            }
            if (file == null) {
              return@connect request.inputStream.use(parser)
            }

            synchronized(this) {
              request.saveToFile(file, indicator)
              connection.getHeaderField("ETag")?.let { saveETagForFile(file, it) }
            }
            return@connect Files.newInputStream(file).use(parser)
          }
          catch (e: HttpRequests.HttpStatusException) {
            LOG.infoWithDebug("Cannot load data from ${url} (statusCode=${e.statusCode})", e)
            throw e
          }
          catch (e: Exception) {
            LOG.infoWithDebug("Error reading Marketplace file: ${e} (file=${file} URL=${url})", e)
            if (file != null && LOG.isDebugEnabled) {
              LOG.debug("File content:\n${runCatching { Files.readString(file) }.getOrElse { IoErrorText.message(e) }}")
            }
            throw e
          }
        }
    }
  }

  private val IDE_BUILD_FOR_REQUEST = URLUtil.encodeURIComponent(ApplicationInfoImpl.getShadowInstanceImpl().pluginsCompatibleBuild)

  private val MARKETPLACE_ORGANIZATIONS_URL = Urls.newFromEncoded("${pluginManagerUrl}/api/search/aggregation/organizations")
    .addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private val JETBRAINS_PLUGINS_URL = Urls.newFromEncoded(
    "${pluginManagerUrl}/api/search/plugins?organization=JetBrains&max=1000"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private val IDE_EXTENSIONS_URL = Urls.newFromEncoded("${pluginManagerUrl}/files/IDE/extensions.json")
    .addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private fun createSearchUrl(query: String, count: Int): Url {
    return Urls.newFromEncoded("$pluginManagerUrl/api/search/plugins?$query&build=$IDE_BUILD_FOR_REQUEST&max=$count")
  }

  private fun createFeatureUrl(param: Map<String, String>): Url {
    return Urls.newFromEncoded("${pluginManagerUrl}/feature/getImplementations").addParameters(param)
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
        .setHeadersViaTuner()
        .connect {
          objectMapper.readValue(
            it.inputStream,
            object : TypeReference<List<FeatureImpl>>() {}
          )
        }
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Can not get features from Marketplace", e)
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
      "build" to ApplicationInfoImpl.getShadowInstanceImpl().pluginsCompatibleBuild,
    )
    return getFeatures(param)
  }

  @RequiresBackgroundThread
  @JvmOverloads
  @Throws(IOException::class)
  fun getMarketplacePlugins(indicator: ProgressIndicator? = null): Set<PluginId> {
    return readOrUpdateFile(
      Path.of(PathManager.getPluginTempPath(), FULL_PLUGINS_XML_IDS_FILENAME),
      "${pluginManagerUrl}/files/$FULL_PLUGINS_XML_IDS_FILENAME",
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
        LOG.infoOrDebug("Cannot get plugins from Marketplace", e)
        emptySet()
      }
    })
  }

  override fun loadCachedPlugins(): Set<PluginId>? {
    val pluginXmlIdsFile = Paths.get(PathManager.getPluginTempPath(), FULL_PLUGINS_XML_IDS_FILENAME)
    try {
      if (Files.size(pluginXmlIdsFile) > 0) {
        return Files.newInputStream(pluginXmlIdsFile).use(::parseXmlIds)
      }
    }
    catch (ignore: IOException) {
    }
    return null
  }

  @Throws(IOException::class)
  fun searchPlugins(query: String, count: Int): List<PluginNode> {
    val marketplaceSearchPluginData = HttpRequests
      .request(createSearchUrl(query, count))
      .setHeadersViaTuner()
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
        .setHeadersViaTuner()
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toList()
        }
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Can not get organizations from Marketplace", e)
      return emptyList()
    }
  }

  fun getBrokenPlugins(currentBuild: BuildNumber): Map<PluginId, Set<String>> {
    val brokenPlugins = try {
      readOrUpdateFile(
        Paths.get(PathManager.getPluginTempPath(), "brokenPlugins.json"),
        "${pluginManagerUrl}/files/brokenPlugins.json",
        null,
        ""
      ) { objectMapper.readValue(it, object : TypeReference<List<MarketplaceBrokenPlugin>>() {}) }
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Can not get broken plugins file from Marketplace", e)
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
          "${pluginManagerUrl}/api/search/aggregation/tags"
        ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST)))
        .setHeadersViaTuner()
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toList()
        }
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Can not get tags from Marketplace", e)
      return emptyList()
    }
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  @JvmOverloads
  fun loadPluginDetails(
    pluginNode: PluginNode,
    indicator: ProgressIndicator? = null,
  ): PluginNode? {
    val externalPluginId = pluginNode.externalPluginId ?: return pluginNode
    val externalUpdateId = pluginNode.externalUpdateId ?: return pluginNode

    try {
      return loadPluginDescriptor(
        pluginNode.pluginId.idString,
        IdeCompatibleUpdate(externalUpdateId = externalUpdateId, externalPluginId = externalPluginId),
        indicator,
      ).apply {
        // these three fields are not present in `IntellijUpdateMetadata`, but present in `MarketplaceSearchPluginData`
        rating = pluginNode.rating
        downloads = pluginNode.downloads
        date = pluginNode.date
      }
    }
    catch (e: IOException) {
      LOG.error(e)
      return null
    }
  }

  @Deprecated("Please use `PluginId`", replaceWith = ReplaceWith("getLastCompatiblePluginUpdate(PluginId.get(id), buildNumber, indicator)"))
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  @JvmOverloads
  fun getLastCompatiblePluginUpdate(
    id: String,
    buildNumber: BuildNumber? = null,
    indicator: ProgressIndicator? = null,
  ): PluginNode? = getLastCompatiblePluginUpdate(PluginId.getId(id), buildNumber, indicator)

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
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
        Urls.newFromEncoded(compatibleUpdateUrl).toExternalForm(),
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
      LOG.infoOrDebug("Can not get compatible update by module from Marketplace", e)
      return null
    }
  }

  var jetBrainsPluginsIds: Set<String>? = null
    private set

  fun loadJetBrainsPluginsIds() {
    if (jetBrainsPluginsIds != null) {
      return
    }

    try {
      HttpRequests
        .request(JETBRAINS_PLUGINS_URL)
        .productNameAsUserAgent()
        .setHeadersViaTuner()
        .throwStatusCodeException(false)
        .connect {
          deserializeJetBrainsPluginsIds(it.inputStream)
        }
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Can not get JetBrains plugins' IDs from Marketplace", e)
      jetBrainsPluginsIds = null
    }
  }

  @VisibleForTesting
  fun deserializeJetBrainsPluginsIds(stream: InputStream) {
    jetBrainsPluginsIds = objectMapper.readValue(stream, object : TypeReference<List<MarketplaceSearchPluginData>>() {})
      .asSequence()
      .map(MarketplaceSearchPluginData::id)
      .toCollection(HashSet())
  }

  var extensionsForIdes: Map<String, List<String>>? = null
    private set

  fun loadExtensionsForIdes() {
    if (extensionsForIdes != null) {
      return
    }

    try {
      HttpRequests
        .request(IDE_EXTENSIONS_URL)
        .productNameAsUserAgent()
        .setHeadersViaTuner()
        .throwStatusCodeException(false)
        .connect {
          deserializeExtensionsForIdes(it.inputStream)
        }
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Can not get supported extensions from Marketplace", e)
      extensionsForIdes = null
    }
  }

  @VisibleForTesting
  fun deserializeExtensionsForIdes(stream: InputStream) {
    extensionsForIdes = objectMapper.readValue(stream, object : TypeReference<Map<String, List<String>>>() {})
  }

  private fun parseXmlIds(input: InputStream) = objectMapper.readValue(input, object : TypeReference<Set<PluginId>>() {})
}

/**
 * NB!: any previous tuners set by {@link RequestBuilder#tuner} will be overwritten by this call
 */
fun RequestBuilder.setHeadersViaTuner(): RequestBuilder =
  if (ApplicationManager.getApplication() != null) {
    serviceOrNull<PluginRepositoryAuthService>()
      ?.let {
        tuner(it.connectionTuner)
      } ?: this
  } else this

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

private fun Logger.infoOrDebug(
  message: String,
  throwable: Throwable,
) {
  if (isDebugEnabled) {
    debug(message, throwable)
  }
  else {
    info("$message: ${throwable.message}")
  }
}
