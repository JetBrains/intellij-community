package fleet.buildtool.bundles

import fleet.buildtool.codecache.ModulePacker
import fleet.buildtool.codecache.ModuleToPack
import fleet.buildtool.codecache.specs.NativeLibraryExtractor
import fleet.buildtool.codecache.specs.MoveFileSpec
import fleet.bundles.LayerSelector
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.collections.toList

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
    shadowedJarSpecs = listOf(),
    moveFileSpecs = listOf(FleetPluginResourceMoveSpec)
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

internal object FleetPluginResourceMoveSpec: MoveFileSpec {
  override val source: String = FLEET_KERNEL_PLUGIN_SERVICE
  override val destination: String = "META-INF/services/$FLEET_KERNEL_PLUGIN_SERVICE"
}