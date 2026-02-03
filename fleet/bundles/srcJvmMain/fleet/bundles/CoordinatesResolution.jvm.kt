package fleet.bundles

import fleet.multiplatform.shims.multiplatformIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.readText

class CoordinatesResolutionImpl(private val fileResolver: suspend (Coordinates) -> Path) : CoordinatesResolution {
  companion object {
    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }
  }

  override suspend fun resolveFile(coordinates: Coordinates): ResolvedFile {
    return withContext(Dispatchers.multiplatformIO) {
      ResolvedFile(fileResolver(coordinates).pathString)
    }
  }

  override suspend fun resolvePluginPartsCoordinates(coordinates: Coordinates): PluginParts {
    val parts = withContext(Dispatchers.multiplatformIO) {
      fileResolver(coordinates).readText()
    }
    return json.decodeFromString(PluginParts.serializer(), parts).eliminateIntersections()
  }

  override suspend fun resolveModule(coordinates: ModuleCoordinates): ModuleOnDisc {
    return withContext(Dispatchers.multiplatformIO) {
      ModuleOnDisc(path = fileResolver(coordinates.coordinates).pathString,
                   serializedModuleDescriptor = coordinates.serializedModuleDescriptor)
    }
  }

  override suspend fun resolveResource(coordinates: Coordinates): ResourceBundle {
    val pathString = resolveFile(coordinates).path
    val resources = Path.of(pathString)
      .readBytes()
      .let(::unzip)
    return ResourceBundle { resources[it.removePrefix("/")] }
  }
}

fun unzip(zipBytes: ByteArray): Map<String, ByteArray> {
  return ZipInputStream(zipBytes.inputStream()).use { zip ->
    generateSequence { zip.nextEntry }.filter { !it.isDirectory }.associate { it.name.removePrefix("/") to zip.readBytes() }
  }
}

