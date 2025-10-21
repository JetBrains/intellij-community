package fleet.codecache

import fleet.bundles.PluginName
import fleet.bundles.PluginVersion
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.Serializable

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common"])
@Serializable
data class VersionsQuery(val query: String)

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common"])
fun versionsQuery(names: Set<String>, offset: Int, version: PluginVersion?): VersionsQuery {
  val joinedName = names.joinToString(separator = ",") { name -> "\"${name}\"" }

  val compatRange = version?.toLong()?.let { long ->
    "compatibility: {range: {gte: $long, lte: $long}}"
  }.orEmpty()

  return VersionsQuery("""
    query {
      updates(
        search: {
          filters: [{ field: "family", value: "fleet" }, { field: "xmlId", value: [$joinedName] }]
          max:     10
          $compatRange
          offset:  $offset
          collapseField: PLUGIN_ID
        }
      ) {
        total
        updates {
          xmlId
          version
        }
      }
    }
    """.trimIndent())
}

/**
 * Marketplace URL to resolve a resource of an uploaded Fleet plugin.
 *
 * Resources referenced by [resourceFilename] could be jars, icons, Fleet parts file, etc.
 */
fun resourceUrl(host: String, pluginName: PluginName, pluginVersion: PluginVersion, resourceFilename: String): String =
  encodedResourceUrl(host, pluginName.name, pluginVersion.versionString, resourceFilename)

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common"])
fun bundleUri(host: String, bundleName: String, bundleVersion: String): String =
  encodedResourceUrl(host, bundleName, bundleVersion, "extension.json")

private fun encodedResourceUrl(host: String, xmlId: String, version: String, resourceFilename: String): String =
  "$host/api/fleet/download?xmlId=${xmlId.encodeURLQueryComponentForMarketplace()}&version=${version.encodeURLQueryComponentForMarketplace()}&module=${resourceFilename.encodeURLQueryComponentForMarketplace()}"

private fun String.encodeURLQueryComponentForMarketplace() = encodeURLQueryComponent(spaceToPlus = false, encodeFull = true)

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common"])
fun searchUri(host: String): String =
  "$host/api/search/graphql"

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common"])
@Serializable
data class VersionsResponse(val data: VersionsData)

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common"])
@Serializable
data class VersionsData(val updates: VersionsList)

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common"])
@Serializable
data class VersionsList(
  val total: Int = 0,
  val updates: List<VersionInfo> = emptyList(),
)

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common"])
@Serializable
data class VersionInfo(
  val xmlId: String,
  val version: String,
)
