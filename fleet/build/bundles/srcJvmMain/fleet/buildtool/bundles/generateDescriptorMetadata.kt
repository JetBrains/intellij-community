package fleet.buildtool.bundles

import fleet.buildtool.codecache.HashedJar
import fleet.buildtool.codecache.findModuleDescriptor
import fleet.bundles.Coordinates
import fleet.bundles.CoordinatesPlatform
import fleet.bundles.KnownCoordinatesMeta
import fleet.bundles.KnownMeta
import fleet.bundles.LayerSelector
import fleet.bundles.ModuleCoordinates
import fleet.bundles.PluginLayer
import fleet.bundles.PluginName
import fleet.bundles.PluginParts
import fleet.bundles.PluginVersion
import fleet.bundles.eliminateIntersections
import fleet.codecache.CodeCacheHasher
import fleet.codecache.resourceUrl
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.slf4j.Logger
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
fun generateDescriptorMetadata(
  pluginVersion: PluginVersion,
  pluginName: PluginName,
  originalMetadata: Map<String, String>,
  marketplaceUrl: String,
  defaultIcon: Path?,
  darkIcon: Path?,
  supportedProducts: List<String>,
  outputModuleJarsByLayer: Map<LayerSelector, Collection<Path>>,
  resources: List<FleetResource>,
  resourcesUsedInDescriptor: Path,
  pluginPartsFileOutput: Path,
  iconsUsedInDescriptor: Path,
  json: Json,
  logger: Logger,
): Map<String, String> {

  val partsCoordinates = resolvePartsCoordinates(
    pluginName = pluginName,
    pluginVersion = pluginVersion,
    outputModuleJarsByLayer = outputModuleJarsByLayer,
    resources = resources,
    resourcesUsedInDescriptor = resourcesUsedInDescriptor,
    marketplaceUrl = marketplaceUrl,
    pluginPartsFileOutput = pluginPartsFileOutput,
    json = json,
    logger = logger,
  )

  logger.info("Writing icons used in descriptor to $iconsUsedInDescriptor")
  // setting these marketplace filepath is not mandatory, but having the same filename in `iconsDirectory` and in `toCoordinates#remoteName` is mandatory
  val defaultIcon = defaultIcon?.copyTo(target = iconsUsedInDescriptor.resolve(defaultIconMarketplaceFilepath), overwrite = false)
  val darkIcon = darkIcon?.copyTo(target = iconsUsedInDescriptor.resolve(darkIconMarketplaceFilepath), overwrite = false)
  val iconCoordinates = defaultIcon?.toCoordinates(pluginName, pluginVersion, marketplaceUrl, remoteName = defaultIcon.name)?.coordinates
  val iconDarkCoordinates = darkIcon?.toCoordinates(pluginName, pluginVersion, marketplaceUrl, remoteName = darkIcon.name)?.coordinates

  return originalMetadata + listOfNotNull(
    KnownMeta.PartsCoordinates to Json.encodeToString(Coordinates.serializer(), partsCoordinates),
    iconCoordinates?.let { KnownMeta.DefaultIconCoordinates to Json.encodeToString(Coordinates.serializer(), it) },
    iconDarkCoordinates?.let { KnownMeta.DarkIconCoordinates to Json.encodeToString(Coordinates.serializer(), it) },
    KnownMeta.SupportedProducts to supportedProducts.joinToString(","),
  ).toMap()
}

fun resolvePartsCoordinates(
  pluginName: PluginName,
  pluginVersion: PluginVersion,
  outputModuleJarsByLayer: Map<LayerSelector, Collection<Path>>,
  resources: List<FleetResource>,
  resourcesUsedInDescriptor: Path,
  marketplaceUrl: String,
  pluginPartsFileOutput: Path,
  json: Json,
  logger: Logger,
): Coordinates {
  val parts = generatePluginParts(pluginName, pluginVersion, outputModuleJarsByLayer, resources, resourcesUsedInDescriptor, marketplaceUrl)
  logger.info("Writing plugin parts to $pluginPartsFileOutput")
  pluginPartsFileOutput.outputStream().use { outputStream ->
    json.encodeToStream(PluginParts.serializer(), parts, outputStream)
  }

  return pluginPartsFileOutput.toCoordinates(pluginName, pluginVersion, marketplaceUrl, remoteName = partsJsonFilename)?.coordinates
    ?: error("failed to create `partsCoordinates`, $pluginPartsFileOutput must exist")
}

private fun generatePluginParts(
  pluginName: PluginName,
  pluginVersion: PluginVersion,
  outputModuleJarsByLayer: Map<LayerSelector, Collection<Path>>,
  resources: List<FleetResource>,
  resourcesUsedInDescriptor: Path,
  marketplaceUrl: String,
) = PluginParts(layers = outputModuleJarsByLayer.map { (layerSelector, moduleJars) ->
  val resourceFileToMetadata = resources.filter { it.layer == layerSelector.selector }.flatMap { resource ->
    val metadata = when (val p = resource.platforms) {
      null -> emptyMap()
      else -> mapOf(KnownCoordinatesMeta.Platforms to Json.encodeToString(ListSerializer(CoordinatesPlatform.serializer()), p))
    }
    resource.files.map { file -> file to metadata }
  }.toMap()
  val resourcesCoordinates = resourceFileToMetadata.entries.mapNotNull { (file, metadata) ->
    file.toCoordinates(pluginName,
                       pluginVersion,
                       marketplaceUrl,
                       file.name,
                       metadata = metadata)?.coordinates // TODO: maybe we should warn about non existing resources file?
  }.toSet()
  resourceFileToMetadata.keys.forEach { file ->
    file.copyTo(resourcesUsedInDescriptor.resolve(file.name), overwrite = false)
  }

  layerSelector to PluginLayer(modulePath = moduleJars.mapNotNull { it.toCoordinates(pluginName, pluginVersion, marketplaceUrl, it.name) }
    .toSet(), modules = moduleJars.filter { jar ->
    moduleIsRelevantToFleetRuntime(jar)
  }.map { jar ->
    findModuleDescriptor(jar).name()
  }.toSet(), resources = resourcesCoordinates)
}.toMap()).eliminateIntersections()

private const val entityDescriptorFileHeuristic: String = "entityTypes.txt"

private const val FLEET_KERNEL_PLUGIN_SERVICE: String = "fleet.kernel.plugins.Plugin"

private fun Path.toCoordinates(
  pluginName: PluginName,
  pluginVersion: PluginVersion,
  marketplaceUrl: String,
  remoteName: String,
  metadata: Map<String, String> = emptyMap(),
): ModuleCoordinates? {
  if (!exists()) {
    return null
  }

  val filepath = this
  val hash = CodeCacheHasher().hash(filepath)
  val moduleDescriptor = when (filepath.extension) {
    "jar" -> {
      val targetJdkVersionFeature = 21
      HashedJar.fromFile(
        file = filepath,
        hash = hash,
        jdkVersionFeature = targetJdkVersionFeature,
      ).moduleDescriptor
    }
    else -> null
  }

  val fileUrl = resourceUrl(marketplaceUrl, pluginName, pluginVersion, remoteName)
  val coord = Coordinates.Remote(url = fileUrl, hash = hash, meta = metadata)
  return ModuleCoordinates(coordinates = coord, serializedModuleDescriptor = moduleDescriptor)
}

/**
 * Returns [true] if [jar]'s module is "relevant" to Fleet's runtime, it could be:
 *  1. for plugin's loading reasons
 *  2. for RhizomeDB entity registration reasons
 *  3. for documentation reasons
 */
private fun moduleIsRelevantToFleetRuntime(jar: Path): Boolean {
  fun ZipEntry.isDocumentationEntry(): Boolean = !isDirectory && name.endsWith(JSON_DOCUMENTATION_FILENAME_EXTENSION)
  fun ZipEntry.isRhizomeEntry(): Boolean = !isDirectory && name == entityDescriptorFileHeuristic

  val descriptor = findModuleDescriptor(jar)
  return when { // modules that the Fleet runtime have interest upon, which are:
    descriptor.provides().any { it.service() == FLEET_KERNEL_PLUGIN_SERVICE } -> true // modules that provides fleet.kernel.plugins.Plugin
    ZipFile(jar.toFile()).use { zip ->
      zip.entries().asSequence().any { it.isDocumentationEntry() || it.isRhizomeEntry() }
    } -> true /* modules of jar that exposes documentation, or that exposes some RhizomeDB entities */
    else -> false
  }
}
