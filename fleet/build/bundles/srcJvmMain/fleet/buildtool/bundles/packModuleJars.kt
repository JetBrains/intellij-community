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

fun Map<LayerSelector, Collection<Path>>.packModuleJars(
  outputDirectory: Path,
  logger: Logger,
  pluginId: String,
): Map<LayerSelector, Collection<Path>> {


  val packer = ModulePacker(
    directory = outputDirectory,
    nativeLibraryExtractor = NativeLibraryExtractor.Noop,
    version = null,
    logger = logger,
    shadowedJarSpecs = listOf(licenseClientShadowedJarSpec),
    scrambledJarSpecs = listOf(), // We will handle scrambled jars in a separate step
  )

  return this.mapValues { (layerSelector, jars) ->
    val moduleName = "$pluginId.${layerSelector.selector}"
    packModule(moduleName, jars, packer)
  }
}

fun packModule(
  moduleName: String,
  jars: Collection<Path>,
  packer: ModulePacker,
): Collection<Path> {
  val packedModule = packer.packModule(
    object : ModuleToPack {
      override val name: String = moduleName
      override val filesToPack: List<Path> = jars.toList()
    })
  return packedModule.jarFiles.map { it.path }
}

suspend fun Map<LayerSelector, Collection<Path>>.scrambleModuleJars(
  fullClasspath: Map<LayerSelector, Collection<Path>>,
  outputDirectory: Path,
  logger: Logger,
  scrambler: JarScrambler,
): Map<LayerSelector, Collection<Path>> {
  return this.mapValues { (layerSelector, jars) ->
    val (toScramble, nonScrambledJars) = jars.partition(fleetCommonScrambleJarSpec::needsScrambling)
    val fullClassPath = fullClasspath[layerSelector] ?: emptyList()
    val scrambledJars = when {
      toScramble.isNotEmpty() -> {
        val scrambledJarsOutputDirectory = outputDirectory.resolve("scrambled")
        scrambledJarsOutputDirectory.createDirectories()
        scrambler.scramble(
          classpath = fullClassPath.toList(),
          jarsToScramble = toScramble,
          outputDirectory = scrambledJarsOutputDirectory,
          logger = logger,
          passthroughJars = emptyList(),
        )
        scrambledJarsOutputDirectory.listDirectoryEntries()
      }
      else -> listOf()
    }

    nonScrambledJars + scrambledJars
  }
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