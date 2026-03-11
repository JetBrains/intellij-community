package fleet.buildtool.bundles

import fleet.buildtool.fs.zip
import fleet.buildtool.sign.FleetSigner
import fleet.buildtool.sign.jetSignJsonContentType
import fleet.buildtool.scrambling.JarScrambler
import fleet.bundles.LayerSelector
import fleet.bundles.PluginDescriptor
import fleet.bundles.PluginName
import fleet.bundles.PluginSignature
import fleet.bundles.PluginVersion
import fleet.bundles.ShipVersionRange
import fleet.bundles.VersionRequirement.CompatibleWith
import fleet.bundles.encodeToSignableString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.slf4j.Logger
import java.io.InputStream
import java.lang.module.ModuleFinder
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.joinToString
import kotlin.collections.plus
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream

internal const val defaultIconMarketplaceFilepath = "pluginIcon.svg"
internal const val darkIconMarketplaceFilepath = "pluginIcon_dark.svg"

@OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
suspend fun generatePluginDescriptor(
  pluginId: String,
  pluginVersion: String,
  shipVersion: PluginVersion,
  projectPluginsPluginDescriptors: List<Path>,
  projectPluginResolvedPluginConfigurations: List<Path>,
  marketplaceUrl: String,

  defaultIcon: Path?,
  darkIcon: Path?,

  compatibleShipVersionRange: ShipVersionRange,
  metadata: Map<String, String>,
  supportedProducts: List<String>,

  runtimeClasspathByLayer: Map<LayerSelector, Set<Path>>,
  alreadyIncludedJarsByLayer: Map<LayerSelector, Set<Path>>,
  documentationDepsByModuleName: Map<String, Path>,
  resources: List<FleetResource>,

  shouldPackModuleJars: Boolean,

  logger: Logger,
  signer: FleetSigner,
  scrambler: JarScrambler,

  pluginPartsFileOutput: Path,
  pluginDescriptorFileOutput: Path,
  jarsUsedInDescriptor: Path,
  iconsUsedInDescriptor: Path,
  resourcesUsedInDescriptor: Path,
) {

  jarsUsedInDescriptor.deleteRecursively()
  jarsUsedInDescriptor.createDirectories()

  iconsUsedInDescriptor.deleteRecursively()
  iconsUsedInDescriptor.createDirectories()

  resourcesUsedInDescriptor.deleteRecursively()
  resourcesUsedInDescriptor.createDirectories()

  val temporaryDir = createTempDirectory("resolvedConfigurationCache")

  logger.info("[fleet-dependencies] Resolving Marketplace and project plugins dependencies for requirements map...")
  logger.warn("[fleet-dependencies] Descriptors: $projectPluginsPluginDescriptors ; $projectPluginResolvedPluginConfigurations")
  val resolvedDependencies = resolvePluginsDependencies(
    // we cannot use the resolved configuration built by [GenerateResolvedPluginsConfigurationTask] as it includes that plugin itself, it has a different purpose all together, see [GenerateResolvedPluginsConfigurationTask]'s description
    cacheDirectory = temporaryDir,
    projectPluginDescriptors = projectPluginsPluginDescriptors,
    projectPluginResolvedPluginConfigurations = projectPluginResolvedPluginConfigurations,
    shipVersion = shipVersion,
    logger = logger,
  )
  val dependencies = resolvedDependencies.bundlesToLoad.associate { descriptor ->
    descriptor.name to CompatibleWith(descriptor.version)
  }.filter { (name, _) -> name.name != "SHIP" }

  val pluginName = PluginName(pluginId)
  val pluginVersion = PluginVersion.fromString(pluginVersion)

  val outputModuleJarsByLayer =
    runtimeClasspathByLayer
      .filterJarsByLayer(alreadyIncludedJarsByLayer, temporaryDir.resolve("filteredJars"), logger)
      .let { moduleJars ->
        when (shouldPackModuleJars) {
          false -> moduleJars // do not pack and modify module jars. Consider it is already done on the previous steps (immutableJars task)
          true -> moduleJars
            .packModuleJars(
              outputDirectory = temporaryDir.resolve("packedJars"),
              logger = logger,
              pluginId = pluginId,
            )
            .scrambleModuleJars(
              fullClasspath = runtimeClasspathByLayer,
              outputDirectory = temporaryDir.resolve("scrambledJars"),
              logger = logger,
              scrambler = scrambler,
            )
        }
      }

  outputModuleJarsByLayer.flatMap { (_, paths) -> paths }
    .forEach {
      it.copyTo(jarsUsedInDescriptor.resolve(it.name), overwrite = true)
    }

  val documentationZipsDirectory = temporaryDir.resolve("documentationZips").also {
    // recreating directories so that build will be reproducible
    it.deleteRecursively()
    it.createDirectories()
  }
  val documentationResources = extractDocumentationResources(outputModuleJarsByLayer, documentationDepsByModuleName, logger, documentationZipsDirectory)
  val resources = resources + documentationResources

  val resourceFiles = resources.flatMap { it.files }
  val duplicatedNames = resourceFiles.groupBy { it.name }.filter { (_, resources) -> resources.size > 1 }.toMap()
  require(duplicatedNames.isEmpty()) { // this is because of marketplace flat representation
    "resource files must have unique names despite directory structures, but found:\n${duplicatedNames.entries.joinToString("\n") { (name, files) -> " - name '$name' used in $files" }}"
  }


  val json = Json(DefaultJson) {
    prettyPrint = true
  }

  val plugin = PluginDescriptor(
    formatVersion = 0,
    name = pluginName,
    version = pluginVersion,
    compatibleShipVersionRange = compatibleShipVersionRange,
    deps = dependencies,
    meta = generateDescriptorMetadata(
      pluginVersion = pluginVersion,
      marketplaceUrl = marketplaceUrl,
      pluginName = pluginName,
      originalMetadata = metadata,
      defaultIcon = defaultIcon,
      darkIcon = darkIcon,
      supportedProducts = supportedProducts,
      outputModuleJarsByLayer = outputModuleJarsByLayer,
      resources = resources,
      resourcesUsedInDescriptor = resourcesUsedInDescriptor,
      pluginPartsFileOutput = pluginPartsFileOutput,
      iconsUsedInDescriptor = iconsUsedInDescriptor,
      json = json,
      logger = logger,
    ),
    signature = null)
  val tmpSigningDir = createTempDirectory("plugin-descriptor-signing")
  val id = conventionalId(pluginName.name, pluginVersion)
  val signingResult = signer.gpgSign(
    data = mapOf(id to plugin.encodeToSignableString().encodeToByteArray()),
    options = mapOf(jetSignJsonContentType),
    temporaryDirectory = tmpSigningDir,
    logger = logger,
  )
  val signedPlugin = when (val signature = signingResult[id]) {
    null -> error("could not find id '$id' in singing result")
    else -> when (signature.size) {
      0 -> {
        logger.warn("WARNING: plugin descriptor not signed")
        plugin
      }
      else -> plugin.copy(signature = PluginSignature(signature))
    }
  }
  logger.info("Writing plugin descriptor to $pluginDescriptorFileOutput")
  pluginDescriptorFileOutput.outputStream().use { outputStream ->
    json.encodeToStream(PluginDescriptor.serializer(), signedPlugin, outputStream)
  }
}


internal fun Set<Path>.unwrapJarFiles() = flatMap {
  when {
    it.isDirectory() -> it.listDirectoryEntries("*.jar")
    else -> listOf(it)
  }
}

internal val emptyJarContents = listOf(
  "__index__",
  "META-INF/MANIFEST.mf"
)


private fun conventionalId(pluginId: String, pluginVersion: String) = conventionalId(pluginId, PluginVersion.fromString(pluginVersion))
private fun conventionalId(pluginId: String, pluginVersion: PluginVersion) = "${pluginId}-${pluginVersion.versionString}"
