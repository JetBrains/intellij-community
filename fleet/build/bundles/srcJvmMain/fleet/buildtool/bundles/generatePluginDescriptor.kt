package fleet.buildtool.bundles

import fleet.buildtool.codecache.HashedJar
import fleet.buildtool.codecache.findModuleDescriptor
import fleet.buildtool.fs.sha256
import fleet.buildtool.fs.zip
import fleet.buildtool.sign.FleetSigner
import fleet.buildtool.sign.jetSignJsonContentType
import fleet.bundles.Coordinates
import fleet.bundles.CoordinatesPlatform
import fleet.bundles.KnownCoordinatesMeta
import fleet.bundles.KnownMeta
import fleet.bundles.LayerSelector
import fleet.bundles.ModuleCoordinates
import fleet.bundles.PluginDescriptor
import fleet.bundles.PluginLayer
import fleet.bundles.PluginName
import fleet.bundles.PluginParts
import fleet.bundles.PluginSignature
import fleet.bundles.PluginVersion
import fleet.bundles.ShipVersionRange
import fleet.bundles.VersionRequirement.CompatibleWith
import fleet.bundles.eliminateIntersections
import fleet.bundles.encodeToSignableString
import fleet.codecache.CodeCacheHasher
import fleet.codecache.resourceUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.slf4j.Logger
import java.io.InputStream
import java.lang.module.ModuleFinder
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.joinToString
import kotlin.collections.plus
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

internal const val defaultIconMarketplaceFilepath = "pluginIcon.svg"
internal const val darkIconMarketplaceFilepath = "pluginIcon_dark.svg"

@OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
fun generatePluginDescriptor(
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
  resources: List<FleetResource>,

  logger: Logger,
  signer: FleetSigner,

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

  val dependencies = runBlocking(Dispatchers.IO) {
    logger.info("[fleet-dependencies] Resolving Marketplace and project plugins dependencies for requirements map...")
    val dependencies = resolvePluginsDependencies(
      // we cannot use the resolved configuration built by [GenerateResolvedPluginsConfigurationTask] as it includes that plugin itself, it has a different purpose all together, see [GenerateResolvedPluginsConfigurationTask]'s description
      cacheDirectory = temporaryDir,
      projectPluginDescriptors = projectPluginsPluginDescriptors,
      projectPluginResolvedPluginConfigurations = projectPluginResolvedPluginConfigurations,
      shipVersion = shipVersion,
      logger = logger,
    )
    dependencies.bundlesToLoad.associate { descriptor ->
      descriptor.name to CompatibleWith(descriptor.version)
    }.filter { (name, _) -> name.name != "SHIP" }
  }

  val pluginName = PluginName(pluginId)
  val pluginVersion = PluginVersion.fromString(pluginVersion)
  val moduleJarsByLayer = runtimeClasspathByLayer.mapNotNull { (layerSelector, jarsCollection) ->
    val jars = jarsCollection.unwrapJarFiles()
    require(jars.all { it.extension == "jar" }) {
      "must have only jar files in runtime classpath for layer '${layerSelector.selector}', but got: ${jars}"
    }

    if (jars.isEmpty()) return@mapNotNull null

    layerSelector to filterConflictingJars(
      alreadyIncludedJars = alreadyIncludedJarsByLayer[layerSelector] ?: emptySet(),
      jars = jars,
      logger = logger,
    )
  }.toMap()

  logger.info("Copying jars used in descriptor to '$jarsUsedInDescriptor'")
  val outputModuleJarsByLayer = moduleJarsByLayer.mapValues { (layerSelector, moduleJars) ->
    moduleJars.map { jar ->
      copyJarToOutputDirectory(jar, layerSelector, jarsUsedInDescriptor, logger)
    }
  }

  val documentationZipsDirectory = temporaryDir.resolve("documentationZips").also {
    // recreating directories so that build will be reproducible
    it.deleteRecursively()
    it.createDirectories()
  }
  val documentationResources = outputModuleJarsByLayer.mapNotNull { (layerSelector, jars) ->
    readDocumentationResourcesContent(jars)?.let { docs ->
      packResourcesToZip(docs.asSequence().map { (name, bytes) -> name to bytes.inputStream() },
                         documentationZipsDirectory,
                         layerSelector.selector)
    }
  }
  val resources = resources + documentationResources

  val resourceFiles = resources.flatMap { it.files }
  val duplicatedNames = resourceFiles.groupBy { it.name }.filter { (_, resources) -> resources.size > 1 }.toMap()
  require(duplicatedNames.isEmpty()) { // this is because of marketplace flat representation
    "resource files must have unique names despite directory structures, but found:\n${duplicatedNames.entries.joinToString("\n") { (name, files) -> " - name '$name' used in $files" }}"
  }

  val parts = PluginParts(layers = outputModuleJarsByLayer.map { (layerSelector, moduleJars) ->
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

  val json = Json(DefaultJson) {
    prettyPrint = true
  }

  logger.info("Writing plugin parts to $pluginPartsFileOutput")
  pluginPartsFileOutput.outputStream().use { outputStream ->
    json.encodeToStream(PluginParts.serializer(), parts, outputStream)
  }

  val partsCoordinates =
    pluginPartsFileOutput.toCoordinates(pluginName, pluginVersion, marketplaceUrl, remoteName = partsJsonFilename)?.coordinates
    ?: error("failed to create `partsCoordinates`, $pluginPartsFileOutput must exist")

  logger.info("Writing icons used in descriptor to $iconsUsedInDescriptor")
  // setting these marketplace filepath is not mandatory, but having the same filename in `iconsDirectory` and in `toCoordinates#remoteName` is mandatory
  val defaultIcon = defaultIcon?.copyTo(target = iconsUsedInDescriptor.resolve(defaultIconMarketplaceFilepath), overwrite = false)
  val darkIcon = darkIcon?.copyTo(target = iconsUsedInDescriptor.resolve(darkIconMarketplaceFilepath), overwrite = false)
  val iconCoordinates = defaultIcon?.toCoordinates(pluginName, pluginVersion, marketplaceUrl, remoteName = defaultIcon.name)?.coordinates
  val iconDarkCoordinates = darkIcon?.toCoordinates(pluginName, pluginVersion, marketplaceUrl, remoteName = darkIcon.name)?.coordinates

  val plugin = PluginDescriptor(
    formatVersion = 0,
    name = pluginName,
    version = pluginVersion,
    compatibleShipVersionRange = compatibleShipVersionRange,
    deps = dependencies,
    meta = metadata + listOfNotNull(
      KnownMeta.PartsCoordinates to Json.encodeToString(Coordinates.serializer(), partsCoordinates),
      iconCoordinates?.let {
        KnownMeta.DefaultIconCoordinates to Json.encodeToString(Coordinates.serializer(),
                                                                it)
      },
      iconDarkCoordinates?.let {
        KnownMeta.DarkIconCoordinates to Json.encodeToString(Coordinates.serializer(),
                                                             it)
      },
      KnownMeta.SupportedProducts to supportedProducts.joinToString(","),
    ).toMap(),
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

private fun packResourcesToZip(
  resources: Sequence<Pair<String, InputStream>>,
  zipsDirectory: Path,
  layer: String,
): FleetResource {
  val zipFileName = zipsDirectory.resolve("${layer}.zip")
  zip(zipFileName, resources)
  resources.forEach { (_, stream) -> stream.close() }
  return FleetResource(setOf(zipFileName), layer, null)
}

private fun readDocumentationResourcesContent(jars: Collection<Path>): List<Pair<String, ByteArray>>? = jars.flatMap { jar ->
    // TODO: this is a hack, in theory we should not pack the documentation json files in the jars, but we don't want to repack for performance reasons, instead this resource zip building should ideally be moved at module packaging level and exposes to other subprojects via consumable configuration
    ZipFile(jar.toFile()).use { zip ->
      zip.entries().asSequence()
        .filter { it.name.endsWith(JSON_DOCUMENTATION_FILENAME_EXTENSION) }
        // Zip entries are required to have '/' as a file separator on any platform (see. 4.4.17.1 in a spec: https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT)
        .onEach { require(!it.name.contains('/')) { "Documentation should be located in a root of a module" } }
        .map { entry -> entry.name to zip.getInputStream(entry).readBytes() }
        .toList()
    }
  }.takeIf { it.isNotEmpty() }

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

private fun filterConflictingJars(
  alreadyIncludedJars: Iterable<Path>,
  jars: Iterable<Path>,
  logger: Logger,
): Set<Path> {
  val alreadyProvidedModuleFinder = ModuleFinder.of(
    *alreadyIncludedJars.toList().toTypedArray(),
  )

  val (ok, conflicting) = jars.associateWith { jar ->
    ModuleFinder.of(jar).findAll().single().descriptor().name()
  }.entries.partition { (jar, moduleName) ->
    logger.debug("Processing module '{}' from '{}'", moduleName, jar)
    val alreadyExisting = when (moduleName) {
      "annotations", "org.jetbrains.annotations" -> true // already provided by Kotlin?
      // TODO: is it actually an ok assumption?
      "kotlin.stdlib.jdk8", "kotlin.stdlib.jdk7" -> alreadyProvidedModuleFinder.find("kotlin.stdlib").isPresent || alreadyProvidedModuleFinder.find(
        moduleName).isPresent

      else -> alreadyProvidedModuleFinder.find(moduleName).isPresent
    }
    !alreadyExisting
  }
  val filteredByModuleNameJars = ok.map { (jar, _) -> jar }
  val conflictingModules = conflicting.map { (_, moduleName) -> moduleName }

  if (conflictingModules.isNotEmpty()) { // TODO: better logging including maybe which dep brought the module
    //  no need to provide `exclude` blocks or equivalent as it will be done transparently for the plugin developer
    //  indeed only granular jars will be stripped out, not the dependency itself, so this code has the same value of an
    //  exclude statement, but done automatically
    logger.warn("WARNING: found ${conflictingModules.size} module(s) already provided by either a plugin on which your plugin depends, or Fleet itself." + " They will *not* be added to your plugin module path causing them to be potentially resolved to a different version at runtime." + " List of module name(s): ${
      conflictingModules.joinToString(", ")
    }")
  }

  val filteredJars = filteredByModuleNameJars.filterNot(::isEmptyJar)

  return filteredJars.toSet()
}

private const val entityDescriptorFileHeuristic: String = "entityTypes.txt"

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

private fun copyJarToOutputDirectory(jar: Path, layerSelector: LayerSelector, outputDirectory: Path, logger: Logger): Path {
  val moduleName = ModuleFinder.of(jar).findAll().single().descriptor().name()
  val target = outputDirectory.resolve("$moduleName.jar")
  logger.info("[${layerSelector.selector}] copying '$jar' to '$target'")
  try {
    jar.copyTo(target = target, overwrite = false)
  }
  catch (e: FileAlreadyExistsException) {
    when {
      sha256(target.readBytes()) == sha256(jar.readBytes()) -> {} // TODO: could we ensure this never has to be called? Technically such jar should be added once in the common layer instead of in frontend and workspace layers for example
      else -> error("two or more layers of this plugin refer to a different jar called '${jar.name}'")
    }
  }
  return target
}

private const val FLEET_KERNEL_PLUGIN_SERVICE: String = "fleet.kernel.plugins.Plugin"

/**
 * There is a matching constant in `SchemaDocumentationWorker` in `fleet-schema-plugin`. Please keep them in sync.
 */
const val JSON_DOCUMENTATION_FILENAME_EXTENSION: String = ".documentation.json"

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

private fun isEmptyJar(jar: Path): Boolean {
  ZipInputStream(jar.inputStream().buffered()).use { zipInputStream ->
    while (true) {
      val entry = zipInputStream.nextEntry ?: break
      if (!entry.isDirectory && emptyJarContents.none { entry.name.endsWith(it, ignoreCase = true) }) {
        return false // Found real content - return fast
      }
    }
    return true
  }
}


private fun conventionalId(pluginId: String, pluginVersion: String) = conventionalId(pluginId, PluginVersion.fromString(pluginVersion))
private fun conventionalId(pluginId: String, pluginVersion: PluginVersion) = "${pluginId}-${pluginVersion.versionString}"
