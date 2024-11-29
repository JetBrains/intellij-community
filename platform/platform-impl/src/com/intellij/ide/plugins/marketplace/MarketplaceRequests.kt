// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginInfoProvider
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.auth.PluginRepositoryAuthService
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls
import com.intellij.ide.plugins.newui.Tags
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.marketplaceIdeCodes
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.TimeoutCachedValue
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.RequestBuilder
import com.intellij.util.io.computeDetached
import com.intellij.util.io.write
import com.intellij.util.ui.IoErrorText
import com.intellij.util.withQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLConnection
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<MarketplaceRequests>()

private val PLUGIN_NAMES_IN_COMMUNITY_EDITION: Map<String, String> = mapOf(
  "com.intellij.database" to "Database Tools and SQL"
)

private val objectMapper: ObjectMapper by lazy { ObjectMapper() }

@OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
@ApiStatus.Internal
class MarketplaceRequests(private val coroutineScope: CoroutineScope) : PluginInfoProvider {
  companion object {
    @JvmStatic
    fun getInstance(): MarketplaceRequests = PluginInfoProvider.getInstance() as MarketplaceRequests

    @Suppress("HttpUrlsUsage")
    @JvmStatic
    fun parsePluginList(input: InputStream): List<PluginNode> {
      try {
        val handler = RepositoryContentHandler()

        val spf = SAXParserFactory.newDefaultInstance()
        spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        spf.setFeature("http://xml.org/sax/features/external-general-entities", false)
        spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)

        val parser = spf.newSAXParser()

        parser.parse(InputSource(input), handler)
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
      throwExceptions: Boolean = false
    ): List<PluginNode> {
      return getLastCompatiblePluginUpdate(pluginIds, buildNumber, throwExceptions)
        .map { loadPluginDescriptor(it.pluginId, it, null) }
    }

    @RequiresBackgroundThread
    @RequiresReadLockAbsence
    @JvmStatic
    @JvmOverloads
    fun getLastCompatiblePluginUpdate(
      ids: Set<PluginId>,
      buildNumber: BuildNumber? = null,
      throwExceptions: Boolean = false
    ): List<IdeCompatibleUpdate> {
      try {
        if (ids.isEmpty()) {
          return emptyList()
        }

        var url = URI(MarketplaceUrls.getSearchCompatibleUpdatesUrl())
        val os = URLEncoder.encode(SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION, CharsetToolkit.UTF8)
        val uid = PermanentInstallationID.get()
        val machineId = MachineIdManager.getAnonymizedMachineId("JetBrainsUpdates")
          .takeIf { PropertiesComponent.getInstance().getBoolean(UpdateChecker.MACHINE_ID_DISABLED_PROPERTY, false) }

        val query = buildString {
          append("build=${ApplicationInfoImpl.orFromPluginCompatibleBuild(buildNumber)}")
          append("&os=$os")
          append("&uuid=$uid")
          if (machineId != null) {
            append("&machineId=$machineId")
          }
        }

        val urlString = url.withQuery(query).toString()

        val data = objectMapper.writeValueAsString(CompatibleUpdateRequest(ids, buildNumber))
        return HttpRequests.post(urlString, HttpRequests.JSON_CONTENT_TYPE).run {
          productNameAsUserAgent()
          throwStatusCodeException(throwExceptions)
          connect {
            it.write(data)
            objectMapper.readValue(it.inputStream, object : TypeReference<List<IdeCompatibleUpdate>>() {})
          }
        }
      }
      catch (e: Exception) {
        LOG.infoOrDebug("Can not get compatible updates from Marketplace", e)
        if (throwExceptions) {
          throw e
        }
        return emptyList()
      }
    }

    @RequiresBackgroundThread
    @RequiresReadLockAbsence
    @JvmStatic
    @JvmOverloads
    fun getNearestUpdate(
      ids: Set<PluginId>,
      buildNumber: BuildNumber? = null,
      throwExceptions: Boolean = false
    ): List<NearestUpdate> {
      try {
        if (ids.isEmpty()) {
          return emptyList()
        }

        val data = objectMapper.writeValueAsString(CompatibleUpdateRequest(ids, buildNumber))
        return HttpRequests.post(MarketplaceUrls.getSearchNearestUpdate(), HttpRequests.JSON_CONTENT_TYPE).run {
          productNameAsUserAgent()
          throwStatusCodeException(throwExceptions)
          connect {
            it.write(data)
            val allBytes = String(it.inputStream.readAllBytes())
            objectMapper.readValue(allBytes, object : TypeReference<List<NearestUpdate>>() {})
          }
        }
      }
      catch (e: Exception) {
        LOG.infoOrDebug("Can not get compatible updates from Marketplace", e)
        if (throwExceptions) {
          throw e
        }
        return emptyList()
      }
    }

    @RequiresBackgroundThread
    @RequiresReadLockAbsence
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun loadPluginDescriptor(
      xmlId: String,
      ideCompatibleUpdate: IdeCompatibleUpdate,
      indicator: ProgressIndicator? = null,
    ): PluginNode {
      val updateMetadataFile = Paths.get(PathManager.getPluginTempPath(), "meta")
      return readOrUpdateFile(
        updateMetadataFile.resolve(ideCompatibleUpdate.externalUpdateId + ".json"),
        MarketplaceUrls.getUpdateMetaUrl(ideCompatibleUpdate.externalPluginId, ideCompatibleUpdate.externalUpdateId),
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
          if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
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

  private val mutex: Mutex = Mutex()

  val marketplaceTagsSupplier: Supplier<Set<String>> = TimeoutCachedValue(1, TimeUnit.HOURS) {
    getAllPluginsTags()
  }

  val marketplaceVendorsSupplier: Supplier<Set<String>> = TimeoutCachedValue(1, TimeUnit.HOURS) {
    getAllPluginsVendors()
  }

  @Throws(IOException::class)
  internal suspend fun getFeatures(param: Map<String, String>): List<FeatureImpl> {
    if (param.isEmpty()) {
      return emptyList()
    }

    try {
      return computeDetached {
        HttpRequests
          .request(MarketplaceUrls.getFeatureImplUrl(param))
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
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Can not get features from Marketplace", e)
      return emptyList()
    }
  }

  @Throws(IOException::class)
  internal suspend fun getFeatures(
    featureType: String,
    implementationName: String,
  ): List<FeatureImpl> {
    val param = mapOf(
      "featureType" to featureType,
      "implementationName" to implementationName,
      "build" to ApplicationInfoImpl.getShadowInstanceImpl().pluginCompatibleBuild,
    )
    return getFeatures(param)
  }

  @RequiresBackgroundThread
  @JvmOverloads
  fun getMarketplacePlugins(indicator: ProgressIndicator? = null): Set<PluginId> {
    try {
      return readOrUpdateFile(
        Path.of(PathManager.getPluginTempPath(), MarketplaceUrls.FULL_PLUGINS_XML_IDS_FILENAME),
        MarketplaceUrls.getPluginsXmlIdsUrl(),
        indicator,
        IdeBundle.message("progress.downloading.available.plugins"),
        ::parseXmlIds,
      )
    }
    catch (e: IOException) {
      LOG.infoOrDebug("Cannot get plugins from Marketplace", e)
      return emptySet()
    }
  }

  override fun loadPlugins(indicator: ProgressIndicator?): Future<Set<PluginId>> {
    return ApplicationManager.getApplication().executeOnPooledThread(Callable {
      getMarketplacePlugins(indicator)
    })
  }

  override fun loadCachedPlugins(): Set<PluginId>? {
    val pluginXmlIdsFile = Paths.get(PathManager.getPluginTempPath(), MarketplaceUrls.FULL_PLUGINS_XML_IDS_FILENAME)
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
    return searchPlugins(query, count, false)
  }

  @Throws(IOException::class)
  fun searchPlugins(query: String, count: Int, includeUpgradeToCommercialIde: Boolean): List<PluginNode> {
    val activeProductCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode
    val suggestedIdeCode = PluginAdvertiserService.getSuggestedCommercialIdeCode(activeProductCode)

    val includeIncompatible = includeUpgradeToCommercialIde && suggestedIdeCode != null

    val marketplaceSearchPluginData = HttpRequests
      .request(MarketplaceUrls.getSearchPluginsUrl(query, count, includeIncompatible))
      .setHeadersViaTuner()
      .throwStatusCodeException(false)
      .connect {
        objectMapper.readValue(
          it.inputStream,
          object : TypeReference<List<MarketplaceSearchPluginData>>() {}
        )
      }
    // Marketplace Search Service can produce objects without "externalUpdateId". It means that an update is not in the search index yet.
    return marketplaceSearchPluginData
      .mapNotNull {
        val pluginNode = it.toPluginNode()

        if (it.externalUpdateId != null) return@mapNotNull pluginNode
        val nearestUpdate = it.nearestUpdate
        if (nearestUpdate == null) return@mapNotNull null
        if (nearestUpdate.compatible) return@mapNotNull pluginNode

        // filter out plugins which version is not compatible with the current IDE version,
        // but they have versions compatible with Community
        if (includeIncompatible
            && !nearestUpdate.supports(activeProductCode)
            && nearestUpdate.supports(suggestedIdeCode)) {

          pluginNode.suggestedCommercialIde = suggestedIdeCode
          pluginNode.tags = getTagsForUi(pluginNode).distinct()
          pluginNode.name = getPluginNameForUi(pluginNode)

          return@mapNotNull pluginNode
        }

        null
      }
  }

  private fun getPluginNameForUi(pluginNode: PluginNode): String {
    if (pluginNode.suggestedCommercialIde != null) {
      // convert name for Database plugin in Community Edition
      return PLUGIN_NAMES_IN_COMMUNITY_EDITION[pluginNode.pluginId.idString] ?: pluginNode.name
    }

    return pluginNode.name
  }

  private fun getTagsForUi(pluginNode: PluginNode): Collection<String> {
    if (pluginNode.suggestedCommercialIde != null) {
      // drop Paid in a Community edition if it is Ultimate-only plugin
      val newTags = (pluginNode.tags ?: emptyList()).toMutableList()

      if (PlatformUtils.isIdeaCommunity()) {
        newTags -= Tags.Paid.name
        newTags += Tags.Ultimate.name
      }
      else if (PlatformUtils.isPyCharmCommunity()) {
        newTags -= Tags.Paid.name
        newTags += Tags.Pro.name
      }

      return newTags
    }

    return pluginNode.tags ?: mutableListOf()
  }

  private fun NearestUpdate.supports(productCode: String?): Boolean {
    if (productCode == null) return false
    val product = marketplaceIdeCodes[productCode] ?: return false

    return products.contains(product)
  }

  private fun getAllPluginsVendors(): Set<String> {
    try {
      return HttpRequests
        .request(MarketplaceUrls.getSearchAggregationUrl("organizations"))
        .setHeadersViaTuner()
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toSet()
        }
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Can not get organizations from Marketplace", e)
      return emptySet()
    }
  }

  fun getBrokenPlugins(currentBuild: BuildNumber): Map<PluginId, Set<String>> {
    val brokenPlugins = try {
      readOrUpdateFile(
        Paths.get(PathManager.getPluginTempPath(), "brokenPlugins.json"),
        MarketplaceUrls.getBrokenPluginsJsonUrl(),
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

  private fun getAllPluginsTags(): Set<String> {
    try {
      return HttpRequests
        .request(MarketplaceUrls.getSearchAggregationUrl("tags"))
        .setHeadersViaTuner()
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toSet()
        }
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Can not get tags from Marketplace", e)
      return emptySet()
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
        suggestedCommercialIde = pluginNode.suggestedCommercialIde
        installSource = pluginNode.installSource
        tags = getTagsForUi(this).distinct()
        name = getPluginNameForUi(pluginNode)
      }
    }
    catch (e: IOException) {
      LOG.warn(e)
      return null
    }
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  internal fun loadPluginMetadata(pluginNode: PluginNode): IntellijPluginMetadata? {
    val externalPluginId = pluginNode.externalPluginId ?: return null
    return loadPluginMetadata(externalPluginId)
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  internal fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata? {
    try {
      return readOrUpdateFile(
        Paths.get(PathManager.getPluginTempPath(), "${externalPluginId}-meta.json"),
        MarketplaceUrls.getPluginMetaUrl(externalPluginId),
        null,
        ""
      ) { objectMapper.readValue(it, object : TypeReference<IntellijPluginMetadata>() {}) }
    }
    catch (e: Exception) {
      LOG.warn(e)
      return null
    }
  }

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
        MarketplaceUrls.getSearchCompatibleUpdatesUrl(),
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

  private var jetbrainsPluginsIds: Set<PluginId>? = null // guarded by mutex

  @RequiresBackgroundThread
  private fun loadJetBrainsMarketplacePlugins(indicator: ProgressIndicator? = null) {
    if (jetbrainsPluginsIds != null) return

    try {
      jetbrainsPluginsIds = readOrUpdateFile(
        Path.of(PathManager.getPluginTempPath(), MarketplaceUrls.JB_PLUGINS_XML_IDS_FILENAME),
        MarketplaceUrls.getJBPluginsXmlIdsUrl(),
        indicator,
        IdeBundle.message("progress.downloading.available.plugins"),
        ::parseXmlIds,
      )
    }
    catch (e: Throwable) {
      LOG.infoOrDebug("Cannot get the list of JetBrains plugins from Marketplace", e)
    }
  }

  private fun schedulePluginIdsUpdate() {
    coroutineScope.launch {
      delay(30.seconds)

      mutex.withLock {
        loadJetBrainsMarketplacePlugins()
        loadExtensionsForIdes()
      }
    }
  }

  fun loadCachedJBPlugins(): Set<PluginId>? {
    val pluginXmlIdsFile = Path.of(PathManager.getPluginTempPath(), MarketplaceUrls.JB_PLUGINS_XML_IDS_FILENAME)
    try {
      if (Files.size(pluginXmlIdsFile) > 0) {
        return Files.newInputStream(pluginXmlIdsFile).use(::parseXmlIds)
      }
    } catch (_: IOException) { }

    // can't find/read jb plugins xml ids cache file, schedule reload
    schedulePluginIdsUpdate()
    return null
  }

  @Volatile
  private var extensionsFromServer: Map<String, List<String>>? = null
  @Volatile
  private var extensionsFromBackup: Map<String, List<String>>? = null

  val extensionsForIdes: Map<String, List<String>>?
    get() {
      if (extensionsFromServer != null) return extensionsFromServer
      if (extensionsFromBackup != null) return extensionsFromBackup

      try {
        val extensionsBackupFile = Path.of(PathManager.getTempPath(), MarketplaceUrls.EXTENSIONS_BACKUP_FILENAME)
        if (Files.exists(extensionsBackupFile)) {
          extensionsFromBackup = objectMapper.readValue(extensionsBackupFile.toFile(),
                                                        object : TypeReference<Map<String, List<String>>>() {})
        }
      }
      catch (e: Exception) {
        LOG.infoOrDebug("Cannot read extensions from local cache file", e)
        extensionsFromBackup = emptyMap()
      }

      schedulePluginIdsUpdate()
      return extensionsFromBackup
    }

  private fun loadExtensionsForIdes() {
    if (extensionsFromServer != null) {
      return
    }

    try {
      HttpRequests
        .request(MarketplaceUrls.getIdeExtensionsJsonUrl())
        .productNameAsUserAgent()
        .setHeadersViaTuner()
        .throwStatusCodeException(false)
        .connect {
          val newExtensions = deserializeExtensionsForIdes(it.inputStream)

          if (newExtensions != null) {
            val extensionsBackupFile = Path.of(PathManager.getTempPath(), MarketplaceUrls.EXTENSIONS_BACKUP_FILENAME)
            try {
              objectMapper.writeValue(extensionsBackupFile.toFile(), newExtensions)
            }
            catch (e: Exception) {
              LOG.infoOrDebug("Cannot save supported extensions from Marketplace", e)
            }
          }
        }
    }
    catch (e: Exception) {
      LOG.infoOrDebug("Cannot get supported extensions from Marketplace", e)
      extensionsFromServer = emptyMap()
    }
  }

  @VisibleForTesting
  fun deserializeExtensionsForIdes(stream: InputStream): Map<String, List<String>>? {
    extensionsFromServer = objectMapper.readValue(stream, object : TypeReference<Map<String, List<String>>>() {})
    return extensionsFromServer
  }

  private fun parseXmlIds(input: InputStream): Set<PluginId> {
    return objectMapper.readValue(input, object : TypeReference<Set<PluginId>>() {})
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  fun loadPluginReviews(pluginNode: PluginNode, page: Int): List<PluginReviewComment>? {
    try {
      return HttpRequests
        .request(MarketplaceUrls.getPluginReviewsUrl(pluginNode.pluginId, page))
        .setHeadersViaTuner()
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          objectMapper.readValue(it.inputStream, object : TypeReference<List<PluginReviewComment>>() {})
        }
    }
    catch (e: IOException) {
      LOG.warn(e)
      return null
    }
  }
}

/**
 * NB!: any previous tuners set by {@link RequestBuilder#tuner} will be overwritten by this call
 */
fun RequestBuilder.setHeadersViaTuner(): RequestBuilder {
  return if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
    serviceOrNull<PluginRepositoryAuthService>()
      ?.connectionTuner
      ?.let(::tuner) ?: this
  }
  else {
    this
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
    ApplicationInfoImpl.orFromPluginCompatibleBuild(buildNumber),
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
    ApplicationInfoImpl.orFromPluginCompatibleBuild(buildNumber),
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
