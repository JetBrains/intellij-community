package fleet.bundles

import fleet.util.Os
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class CoordinatesPlatform {
  WindowsX64,
  WindowsAarch64,
  LinuxX64,
  LinuxAarch64,
  MacOsX64,
  MacOsAarch64,
  Wasm
}

val Coordinates.platforms: List<CoordinatesPlatform>?
  get() = meta[KnownCoordinatesMeta.Platforms]?.let {
    runCatching {
      Json.decodeFromString(ListSerializer(CoordinatesPlatform.serializer()), it)
    }.getOrDefault(emptyList())
  }

private val currentPlatform by lazy {
  if (Os.INSTANCE.isWasm) CoordinatesPlatform.Wasm
  else when (Os.INSTANCE.type) {
    Os.Type.Windows -> if (Os.INSTANCE.isAarch64) CoordinatesPlatform.WindowsAarch64 else CoordinatesPlatform.WindowsX64
    Os.Type.Linux -> if (Os.INSTANCE.isAarch64) CoordinatesPlatform.LinuxAarch64 else CoordinatesPlatform.LinuxX64
    Os.Type.MacOS -> if (Os.INSTANCE.isAarch64) CoordinatesPlatform.MacOsAarch64 else CoordinatesPlatform.MacOsX64
    Os.Type.Unknown -> null
  }
}

fun Iterable<Coordinates>.filterCoordinatesByPlatform(platform: CoordinatesPlatform? = currentPlatform): List<Coordinates> = filter { coordinates ->
  coordinates.platforms?.contains(platform) != false
}
