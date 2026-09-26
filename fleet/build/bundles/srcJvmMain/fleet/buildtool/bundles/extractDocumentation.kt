package fleet.buildtool.bundles

import fleet.buildtool.fs.zip
import fleet.bundles.LayerSelector
import org.slf4j.Logger
import java.io.InputStream
import java.lang.module.ModuleFinder
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.inputStream
import kotlin.io.path.name

internal fun extractDocumentationResources(
  moduleJarsByLayer: Map<LayerSelector, Collection<Path>>,
  documentationDepsByModuleName: Map<String, Path>,
  logger: Logger,
  outputDirectory: Path,
) = when {
  documentationDepsByModuleName.isNotEmpty() -> extractDocumentationResourcesFromModuleMap(
    moduleJarsByLayer = moduleJarsByLayer,
    documentationDepsByModuleName = documentationDepsByModuleName,
    logger = logger,
    outputDirectory = outputDirectory,
  )
  else -> extractDocumentationResourcesFromJars(moduleJarsByLayer, outputDirectory)
}

internal fun extractDocumentationResourcesFromJars(
  moduleJarsByLayer: Map<LayerSelector, Collection<Path>>,
  outputDirectory: Path,
): List<FleetResource> = moduleJarsByLayer.mapNotNull { (layerSelector, jars) ->
  readDocumentationResourcesContent(jars)?.let { docs ->
    packResourcesToZip(docs.asSequence().map { (name, bytes) -> name to bytes.inputStream() },
                       outputDirectory,
                       layerSelector.selector)
  }
}

internal fun extractDocumentationResourcesFromModuleMap(
  moduleJarsByLayer: Map<LayerSelector, Collection<Path>>,
  documentationDepsByModuleName: Map<String, Path>,
  logger: Logger,
  outputDirectory: Path,
): List<FleetResource> {
  val moduleNamesByLayer = moduleJarsByLayer.mapValues { (_, jars) ->
    jars.map { ModuleFinder.of(it).findAll().single().descriptor().name() }
  }
  return moduleNamesByLayer.mapNotNull { (layerSelector, modules) ->
    val documentationFiles = modules.mapNotNull { moduleName -> documentationDepsByModuleName[moduleName] }
    when {
      documentationFiles.isEmpty() -> {
        logger.debug("[${layerSelector.selector}] No documentation files found")
        null
      }
      else -> {
        logger.debug("[${layerSelector.selector}] Packing documentation files")
        packResourcesToZip(
          resources = documentationFiles.asSequence().map { file -> file.name to file.inputStream() },
          zipsDirectory = outputDirectory,
          layer = layerSelector.selector,
        )
      }
    }
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

/**
 * There is a matching constant in `SchemaDocumentationWorker` in `fleet-schema-plugin`. Please keep them in sync.
 */
const val JSON_DOCUMENTATION_FILENAME_EXTENSION: String = ".documentation.json"