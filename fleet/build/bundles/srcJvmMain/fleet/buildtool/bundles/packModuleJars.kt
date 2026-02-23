package fleet.buildtool.bundles

import fleet.buildtool.codecache.ModulePacker
import fleet.buildtool.codecache.ModuleToPack
import fleet.buildtool.codecache.specs.NativeLibraryExtractor
import fleet.buildtool.codecache.specs.ScrambledJarSpec
import fleet.buildtool.codecache.specs.ShadowedJarSpec
import fleet.buildtool.scrambling.JarScrambler
import fleet.bundles.LayerSelector
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.collections.toList
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries

suspend fun Map<LayerSelector, Collection<Path>>.packModuleJars(
  shouldPackModuleJars: Boolean,
  fullClassPath: Map<LayerSelector, Collection<Path>>,
  outputDirectory: Path,
  logger: Logger,
  pluginId: String,
  scrambler: JarScrambler,
): Map<LayerSelector, Collection<Path>> {

  return when {
    !shouldPackModuleJars -> this // Do nothing, return the current map
    else -> {
      val packer = ModulePacker(
        directory = outputDirectory,
        nativeLibraryExtractor = NativeLibraryExtractor.Noop,
        version = null,
        logger = logger,
        shadowedJarSpecs = listOf(licenseClientShadowedJarSpec),
        scrambledJarSpecs = listOf(fleetCommonScrambleJarSpec),
      )

      this.mapValues { (layerSelector, jars) ->
        val moduleName = "$pluginId.${layerSelector.selector}"
        val fullClassPathForLayer = fullClassPath[layerSelector] ?: emptyList()
        packModule(moduleName, jars, packer, fullClassPathForLayer, outputDirectory, logger, scrambler)
      }
    }
  }
}

suspend fun packModule(
  moduleName: String,
  jars: Collection<Path>,
  packer: ModulePacker,
  fullClassPath: Collection<Path>,
  outputDirectory: Path,
  logger: Logger,
  scrambler: JarScrambler,
): Collection<Path> {
  val packedModule = packer.packModule(
    object : ModuleToPack {
      override val name: String = moduleName
      override val filesToPack: List<Path> = jars.toList()
    })

  val (toScramble, nonScrambledJars) = packedModule.jarFiles.partition { it.needsScrambling }
  val scrambledJars = when {
    toScramble.isNotEmpty() -> {
      val scrambledJarsOutputDirectory = outputDirectory.resolve("scrambled")
      scrambledJarsOutputDirectory.createDirectories()
      scrambler.scramble(
        classpath = fullClassPath.toList(),
        jarsToScramble = toScramble.map { it.path },
        outputDirectory = scrambledJarsOutputDirectory,
        logger = logger,
        passthroughJars = emptyList(),
      )
      scrambledJarsOutputDirectory.listDirectoryEntries()
    }
    else -> listOf()
  }

  return nonScrambledJars.map { it.path } + scrambledJars
}

private val fleetCommonScrambleJarSpec = ScrambledJarSpec(
  jarToScramblePattern = Regex("fleet\\.common.*\\.jar"),
)
private val licenseClientShadowedJarSpec = ShadowedJarSpec(
  allowedConsumerModule = "SHIP.common",
  consumerJarPattern = fleetCommonScrambleJarSpec.jarToScramblePattern,
  shadowedJarPattern = Regex("ls\\.client\\.api\\.jar"),
  jpmsModuleName = "ls.client.api",
)