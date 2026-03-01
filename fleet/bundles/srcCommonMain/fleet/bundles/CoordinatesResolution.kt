package fleet.bundles

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.jvm.JvmInline

class ResolutionException(coordinates: Coordinates, cause: Throwable? = null) : Exception("Can't resolve $coordinates", cause)

/**
 * Responsible for downloading jars, checking their hashes, and caching
 * @throws ResolutionException
 */
interface CoordinatesResolution {
  suspend fun resolveFile(coordinates: Coordinates): ResolvedFile
  suspend fun resolvePluginPartsCoordinates(coordinates: Coordinates): PluginParts
  suspend fun resolveModule(coordinates: ModuleCoordinates): ModuleOnDisc
  suspend fun resolveResource(coordinates: Coordinates): ResourceBundle
}

@JvmInline
value class ResolvedFile(val path: String)

suspend fun CoordinatesResolution.resolve(layer: PluginLayer): ResolvedPluginLayer = coroutineScope {
  ResolvedPluginLayer(modules = layer.modules,
                      modulePath = layer.modulePath.map { moduleCoords ->
                        async {
                          resolveModule(moduleCoords)
                        }
                      }.awaitAll().toSet(),
                      resources = layer.resources.filterCoordinatesByPlatform().map {
                        async {
                          resolveResource(it)
                        }
                      }.awaitAll().toSet())
}

suspend fun CoordinatesResolution.resolveParts(descriptor: PluginDescriptor): PluginParts {
  val coordinates = requireNotNull(descriptor.partsCoordinates) {
    "missing parts coordinates in descriptor: $descriptor"
  }
  return resolvePluginPartsCoordinates(coordinates)
}

data class ResolvedPluginLayer(val modulePath: Set<ModuleOnDisc>,
                               val modules: Set<String>,
                               val resources: Set<ResourceBundle>)

data class ModuleOnDisc(val path: String,
                        val serializedModuleDescriptor: String?)

fun interface ResourceBundle {
  operator fun get(key: String): ByteArray?
}