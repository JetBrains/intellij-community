package fleet.buildtool.bundles

import fleet.buildtool.codecache.ModulePacker
import fleet.buildtool.codecache.ModuleToPack
import fleet.buildtool.codecache.NativeLibraryExtractor
import fleet.buildtool.codecache.PackedJar
import fleet.buildtool.codecache.kotlinStdlibJarNamePattern
import fleet.buildtool.codecache.shadowing.ShadowedJarSpec
import fleet.bundles.LayerSelector
import org.slf4j.Logger
import java.nio.file.Path

fun packModuleJars(
  moduleJarsByLayer: Map<LayerSelector, Collection<Path>>,
  outputDirectory: Path,
  logger: Logger,
  pluginId: String,
): Map<LayerSelector, Collection<Path>> {

  val packer = ModulePacker(
    directory = outputDirectory,
    nativeLibraryExtractor = NativeLibraryExtractor.Noop, // TODO: Native ModulesExtractor
    version = null,
    logger = logger,
    shadowedJarSpecs = listOf(licenseClientShadowedJarSpec),
  )

  return moduleJarsByLayer.mapValues { (layerSelector, jars) ->
    val packedModule = packer.packModule(
      object : ModuleToPack {
        override val name: String = "$pluginId.${layerSelector.selector}"
        override val filesToPack: List<Path> = jars.toList()
      })
    packedModule.jarFiles.map { it.path }
  }
}


private val licenseClientShadowedJarSpec = ShadowedJarSpec(
  allowedConsumerModule = "SHIP.common",
  consumerJarPattern = Regex("fleet\\.common.*\\.jar"), // fleet.common-$version.jar in Gradle
  shadowedJarPattern = Regex("ls\\.client\\.api\\.jar"), // ls-client-api.jar in Gradle
  jpmsModuleName = "ls.client.api",
  needsScrambling = true,
)