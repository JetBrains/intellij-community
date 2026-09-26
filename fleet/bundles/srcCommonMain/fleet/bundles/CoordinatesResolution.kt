package fleet.bundles

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

data class ResolvedPluginLayer(val modulePath: Set<ModuleOnDisc>,
                               val modules: Set<String>,
                               val resources: Set<ResourceBundle>)

data class ModuleOnDisc(val path: String,
                        val serializedModuleDescriptor: String?)

fun interface ResourceBundle {
  suspend fun readResource(key: String): ByteArray?
}