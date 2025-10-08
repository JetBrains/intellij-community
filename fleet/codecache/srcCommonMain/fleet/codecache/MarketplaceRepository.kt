package fleet.codecache

import fleet.bundles.PluginDescriptor
import fleet.bundles.PluginName
import fleet.bundles.PluginRepository
import fleet.bundles.PluginVersion
import fleet.util.logging.logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi

private object Marketplace {
  val logger = logger<Marketplace>()
}

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common", "fleet.plugins.pluginManagement.test"])
fun marketPlaceRepository(httpClient: suspend () -> HttpClient, host: String, defaultRepository: PluginRepository? = null): PluginRepository {
  return object : PluginRepository {
    override suspend fun getLatestVersions(names: Set<PluginName>, shipVersion: PluginVersion): Map<PluginName, PluginVersion> {
      Marketplace.logger.debug("Querying latest versions of '$names' compatible with '$shipVersion'...")
      tailrec suspend fun loop(
        out: HashMap<PluginName, PluginVersion> = HashMap(),
        offset: Int = 0,
      ): Map<PluginName, PluginVersion> {
        val query = versionsQuery(names.mapTo(HashSet()) { it.name }, offset, shipVersion)
        val queryStr = Json.encodeToString(VersionsQuery.serializer(), query)
        val versions = kotlin.runCatching {
          val url = searchUri(host)
          val body = httpClient().post(url) {
            setBody(TextContent(queryStr, ContentType.Application.Json))
          }.bodyAsText()
          val response = Json.decodeFromString(VersionsResponse.serializer(), body)
          Marketplace.logger.debug("URL:\n$url\nQuery:\n$queryStr\nResponse:\n$response")
          response.data.updates.updates.map { versionInfo ->
            PluginName(versionInfo.xmlId) to PluginVersion.fromString(versionInfo.version)
          }
        }.onFailure { x ->
          if (x is CancellationException) {
            throw x
          }
          Marketplace.logger.warn(x, "got error from versions query for $names, query = $query")
        }.getOrNull() ?: emptyList()

        out.putAll(versions)
        return when {
          versions.size < 10 -> out
          else -> loop(out, offset + versions.size)
        }
      }

      val versionMap = loop()
      Marketplace.logger.debug("Found latest versions '$versionMap'")
      return versionMap
    }

    override suspend fun getPlugin(pluginName: PluginName, pluginVersion: PluginVersion): PluginDescriptor? = run {
      // we should not download the descriptor from the internet if we already did it once, we have it in the trusted repo
      // this optimization should speed up the plugin management significantly
      defaultRepository?.getPlugin(pluginName, pluginVersion) ?: run {
        Marketplace.logger.debug("Querying marketplace $host for a plugin $pluginName")
        val uri = bundleUri(host, pluginName.name, pluginVersion.versionString)
        try {
          val bundleStr = httpClient().get(uri).bodyAsText()
          Marketplace.logger.debug("getPlugin($pluginName, $pluginVersion) => ${bundleStr.length} bytes")
          Json.decodeFromString(PluginDescriptor.serializer(), bundleStr)
        }
        catch (x: CancellationException) {
          throw x
        }
        catch (x: Throwable) {
          Marketplace.logger.debug(x, "got error from $uri")
          throw x
        }
      }
    }

    override fun presentableName(): String = "Marketplace PluginRepository `$host`"

    @OptIn(ExperimentalUuidApi::class)
    /**
     * Marketplace repository is always considered identical in context of cache invalidation, we want reproducibility (after the first resolution) instead of freshness.
     */
    override fun cacheKey(): String = "never changing Marketplace"
  }
}
