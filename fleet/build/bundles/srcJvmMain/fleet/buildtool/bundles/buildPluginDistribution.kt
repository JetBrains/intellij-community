package fleet.buildtool.bundles

import fleet.bundles.Coordinates
import fleet.bundles.PluginDescriptor
import fleet.bundles.PluginParts
import fleet.bundles.encodeToString
import fleet.codecache.CodeCacheHasher
import fleet.codecache.relativePathToCodeCache
import fleet.buildtool.fs.zip
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.slf4j.Logger
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Assembles the standalone plugin distribution ZIP, mirroring the Gradle `BuildPluginDistributionTask`.
 *
 * The inputs are the plugin descriptor and parts JSON files together with the directories (or plain files) that
 * hold the plugin's module jars, resources and icons already prepared by the descriptor-generation step, plus the
 * third-party libraries license JSON. The remote coordinates recorded in [pluginPartsFile] are matched back to the
 * actual jars by their code-cache hash, and everything is packed into a reproducible ZIP named
 * `<plugin-name>-<plugin-version>.zip` inside [distributionOutputDirectory].
 *
 * The function is intentionally free of any build-system types (only [Path] / [Logger]); Gradle and Bazel both call
 * into it after materializing their inputs on disk.
 *
 * @return the path of the produced distribution ZIP.
 */
@OptIn(ExperimentalSerializationApi::class, ExperimentalPathApi::class)
fun buildPluginDistribution(
  jars: Set<Path>,
  pluginDescriptorFile: Path,
  pluginPartsFile: Path,
  resources: Set<Path>,
  defaultIcon: Path?,
  darkIcon: Path?,
  thirdPartyLicenses: Set<Path>,
  distributionOutputDirectory: Path,
  logger: Logger,
): Path {
  distributionOutputDirectory.deleteRecursively()
  distributionOutputDirectory.createDirectories()

  val plugin = pluginDescriptorFile.inputStream().use { inputStream ->
    DefaultJson.decodeFromStream(PluginDescriptor.serializer(), inputStream)
  }
  val parts = pluginPartsFile.inputStream().use { inputStream ->
    DefaultJson.decodeFromStream(PluginParts.serializer(), inputStream)
  }

  val files = parts.layers.values.flatMap { it.modulePath }.map { it.coordinates }
  val remoteFiles = files.filterIsInstance<Coordinates.Remote>().toSet()
  require(remoteFiles.size == files.size) {
    "the given parts file '$pluginPartsFile' must not contain any local coordinates when building distribution"
  }
  logger.info("""
    |Found the following coordinates to publish:
    |${remoteFiles.joinToString("\n") { coordinates -> "  - (${coordinates.hash}) '${coordinates.url}'" }}
  """.trimMargin())

  val jarsByHash = jars.unwrapJarFiles().groupBy { jar ->
    CodeCacheHasher().hash(jar)
  }.mapValues { (hash, jars) ->
    when {
      jars.singleOrNull() != null -> jars.single() // no duplicated jars for the same hash, ideal case
      jars.map { it.name }.toSet().singleOrNull() != null -> jars.first() // same hash, same name they are identical, safe case
      else -> error("""
        |found multiple jars for hash '$hash' (these jars are probably containing no code which is incorrect, check your Kotlin multiplatform configuration and directory structure of the corresponding layers subprojects):
        |${jars.joinToString("\n") { "  - $it" }}
      """.trimMargin())
    }
  }
  require(jarsByHash.isNotEmpty()) { "plugin distribution must have at least one jar file" }
  logger.info("""
    |Plugin uses the following jars at runtime:
    |${jarsByHash.entries.joinToString("\n") { (hash, jar) -> "  - ($hash) '${jar}'" }}
  """.trimMargin())

  val pluginJars = remoteFiles.map { coordinate ->
    val jar = jarsByHash[coordinate.hash] ?: error("jar with hash ${coordinate.hash} not provided to `jars` input")
    val coordinateName = Path.of(coordinate.relativePathToCodeCache()).name
    require(coordinateName == jar.name) {
      "Descriptor references jar name $coordinateName with ${coordinate.hash}, but the actual jar name is ${jar.name}"
    }
    jar
  }

  val resourceFiles = resources.flattenToFiles()
  val thirdPartyLicensesJson = thirdPartyLicenses.flattenToFiles().singleOrNull()?.readText() ?: "[]"

  val icons = listOf(
    defaultIconMarketplaceFilepath to defaultIcon,
    darkIconMarketplaceFilepath to darkIcon,
  )
    .filter { it.second != null }

  val zipFile = distributionOutputDirectory.resolve("${plugin.name.name}-${plugin.version.versionString}.zip")
  val entries: Sequence<Pair<String, InputStream>> =
    pluginJars.asSequence().map { jar -> jar.name to jar.inputStream() } +
    resourceFiles.asSequence().map { resource -> resource.name to resource.inputStream() } +
    sequenceOf(
      "extension.json" to plugin.encodeToString().encodeToByteArray().inputStream(),
      "dependencies.json" to thirdPartyLicensesJson.toByteArray().inputStream(),
      partsJsonFilename to pluginPartsFile.inputStream(),
    ) + icons.map { (filename, icon) -> filename to icon!!.inputStream() }
  zip(zipFile, entries)
  logger.info("Built plugin distribution at '$zipFile'")
  return zipFile
}

/**
 * Expands the given [Path]s into plain files: directories (e.g. Bazel `TreeArtifact` inputs) are listed one level
 * deep, plain files are kept as-is. Mirrors the flat file collections the Gradle task received.
 */
private fun Set<Path>.flattenToFiles(): List<Path> = flatMap { path ->
  when {
    path.isDirectory() -> path.listDirectoryEntries()
    else -> listOf(path)
  }
}
