package fleet.buildtool.codecache

import fleet.buildtool.platform.Platform
import fleet.bundles.Coordinates
import fleet.bundles.PluginDescriptor
import fleet.bundles.PluginName
import fleet.bundles.PluginVersion
import fleet.bundles.metaAsCoordinates
import java.nio.file.Path
import kotlin.io.path.name

fun urlForPluginResource(baseUrl: String, plugin: PluginId, resourceFilename: String): String = "$baseUrl/api/fleet/download?xmlId=${plugin.name.name}&version=${plugin.version.versionString}&module=$resourceFilename"

data class PluginId(
  val name: PluginName,
  val version: PluginVersion,
)

sealed class PluginPart {
  data class Bundled(val jar: HashedJar) : PluginPart() {
    override fun toCoordinates(baseUrl: String, pluginId: PluginId): Coordinates = Coordinates.Remote(
      url = urlForPluginResource(baseUrl, pluginId, jar.file.name),
      hash = jar.hash,
    )

    override val codeCacheFile: Path = jar.file
    override val relativePathInCodeCache: Path = Path.of(jar.hash, codeCacheFile.fileName.toString())
  }

  abstract fun toCoordinates(baseUrl: String, pluginId: PluginId): Coordinates
  abstract val codeCacheFile: Path

  abstract val relativePathInCodeCache: Path
}

fun PluginDescriptor.fsdaemonCoordinates(platform: Platform): Coordinates? = metaAsCoordinates(fsdaemonCoordinatesMetadataAttributeOf(platform))
fun fsdaemonCoordinatesMetadataAttributeOf(platform: Platform): String = when (platform) {
  Platform.Linux.LinuxAarch64 -> "fsd-binaries-linux_aarch64"
  Platform.Linux.LinuxX64 -> "fsd-binaries-linux_x64"
  Platform.MacOs.MacOsAarch64 -> "fsd-binaries-macos_aarch64"
  Platform.MacOs.MacOsX64 -> "fsd-binaries-macos_x64"
  Platform.Windows.WindowsAarch64 -> "fsd-binaries-windows_aarch64"
  Platform.Windows.WindowsX64 -> "fsd-binaries-windows_x64"
}
