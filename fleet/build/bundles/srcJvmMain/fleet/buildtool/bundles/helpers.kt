package fleet.buildtool.bundles

import fleet.buildtool.fs.sha256
import fleet.bundles.PluginCommand
import fleet.bundles.PluginDescriptor
import fleet.bundles.PluginName
import fleet.bundles.PluginRepository
import fleet.bundles.PluginSet
import fleet.bundles.PluginVersion
import fleet.bundles.PluginsConfig
import fleet.bundles.ResolvedPluginsConfig
import fleet.bundles.asPluginRepository
import fleet.bundles.compose
import fleet.bundles.resolveWorkspacePlugins
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.uuid.ExperimentalUuidApi

const val partsJsonFilename = "parts.json"

suspend fun resolvePluginsDependencies(
  cacheDirectory: Path,
  projectPluginDescriptors: Collection<Path>,
  projectPluginResolvedPluginConfigurations: Collection<Path>,
  shipVersion: PluginVersion,
  logger: Logger,
): ResolvedPluginsConfig {
  val pluginsToResolve = projectPluginDependencies(projectPluginDescriptors, logger)
  return when {
    pluginsToResolve.isEmpty() -> ResolvedPluginsConfig(bundlesToLoad = emptySet(), shipVersion = shipVersion)
    else -> {
      val repositories = projectPluginRepositories(projectPluginResolvedPluginConfigurations, logger)
                         ?: error("failed to resolve plugins dependencies, must have at least one non-null repository")

      repositories.resolvePlugins(cacheDirectory, pluginsToResolve, shipVersion, logger)
    }
  }
}

private fun projectPluginDependencies(
  projectPluginDescriptors: Collection<Path>,
  logger: Logger,
): Map<PluginName, PluginVersion> {
  require(projectPluginDescriptors.all { it.extension == "json" }) {
    """
      |`projectPluginResolvedPluginConfigurations` must contain only json files, got:
      ${projectPluginDescriptors.joinToString("\n") { "|  - $it" }}
      |
      |Usually, that means either that:
      |
      | - a Gradle plugin layer subproject is not configured correctly and is missing:
      |```
      |plugins {
      |  id("org.jetbrains.fleet-plugin-layer")
      |}
      |```
      |
      | - OR, that your Fleet plugin depends on a layer subproject instead of the plugin subproject, for example:
      |```
      |pluginDependencies {
      |  plugin(project(":fleet.plugins.misc.frontend"))
      |}
      |```
      |
      |instead of:
      |
      |```
      |pluginDependencies {
      |  plugin(project(":fleet.plugins.misc"))
      |}
      |```
    """.trimMargin()
  }

  val (projectPluginDescriptorsFiles, nonExistingProjectPluginDescriptorsFiles) = projectPluginDescriptors.partition {
    it.exists()
  }
  nonExistingProjectPluginDescriptorsFiles.forEach { file ->
    logger.warn("[fleet-dependencies] Plugin descriptor file not found, plugin will be ignored '$file'")
  }
  return projectPluginDescriptorsFiles.map { descriptorFile ->
    Json.decodeFromString(PluginDescriptor.serializer(), descriptorFile.readText())
  }.associate {
    it.name to it.version
  }
}

/**
 * Creates a plugin repository composed of all project dependencies inter-project dependency artifact (resolved plugin configurations)
 */
private fun projectPluginRepositories(
  projectPluginResolvedPluginConfigurations: Collection<Path>,
  logger: Logger,
): PluginRepository? {
  require(projectPluginResolvedPluginConfigurations.all { it.extension == "json" }) { // TODO: leaks Gradle details in that error message
    """
      |`projectPluginResolvedPluginConfigurations` must contain only json files, got:
      ${projectPluginResolvedPluginConfigurations.joinToString("\n") { "|  - $it" }}
      |
      |Usually, that means either that:
      |
      | - a Gradle plugin layer subproject is not configured correctly and is missing:
      |```
      |plugins {
      |  id("org.jetbrains.fleet-plugin-layer")
      |}
      |```
      |
      | - OR, that your Fleet plugin depends on a layer subproject instead of the plugin subproject, for example:
      |```
      |pluginDependencies {
      |  plugin(project(":fleet.plugins.misc.frontend"))
      |}
      |```
      |
      |instead of:
      |
      |```
      |pluginDependencies {
      |  plugin(project(":fleet.plugins.misc"))
      |}
      |```
    """.trimMargin()
  }

  val (resolvedConfFiles, nonExistingResolvedConfFiles) = projectPluginResolvedPluginConfigurations.partition {
    it.exists()
  }
  nonExistingResolvedConfFiles.forEach { file ->
    logger.warn("[fleet-dependencies] Resolved configuration file not found, some plugins might be missing '$file'")
  }
  return resolvedConfFiles.map { resolvedConfFile ->
    Json.decodeFromString(ResolvedPluginsConfig.serializer(), resolvedConfFile.readText())
  }.map { resolvedPluginConfiguration ->
    PluginSet(
      shipVersions = setOf(resolvedPluginConfiguration.shipVersion.versionString),
      plugins = resolvedPluginConfiguration.bundlesToLoad,
    ).asPluginRepository()
  }.reduceOrNull(PluginRepository::compose)
}

@OptIn(ExperimentalSerializationApi::class)
/**
 * Resolve plugins against [repository].
 *
 * The result is cached on disk so subsequent calls to that function for the same [plugins] is cheap.
 */
// TODO: would be nice to have a lockfile logic instead of that caching
private suspend fun PluginRepository.resolvePlugins(
  cacheDirectory: Path,
  plugins: Map<PluginName, PluginVersion?>,
  shipVersion: PluginVersion,
  logger: Logger,
): ResolvedPluginsConfig {
  val repository = this

  val cachedConfig = cacheDirectory.resolve("${cacheKey(shipVersion, plugins, repository)}.json")

  return when {
    cachedConfig.exists() -> {
      logger.info("[fleet-dependencies] Plugins configurations resolved from cache in '$cachedConfig'")
      DefaultJson.decodeFromString(ResolvedPluginsConfig.serializer(), cachedConfig.readText())
    }
    else -> {
      logger.debug("[fleet-dependencies] Resolving plugins configuration using plugins repositories...")
      val commands = plugins.map { (name, version) -> PluginCommand.Add(pluginName = name, exactVersion = version) }
      val result = resolveWorkspacePlugins(PluginsConfig(commands), shipVersion, repository, false)
      when {
        result.problems.isNotEmpty() -> {
          val problems = result.problems.joinToString("\n") { " - $it" }
          logger.error("[fleet-dependencies] failed to resolve plugins, plugins configuration has been resolved partially and won't be cached due to the following errors\n${problems}")
          result.config
        }
        else -> {
          val json = Json(DefaultJson) {
            prettyPrint = true
            explicitNulls = true
          }
          val tmpConfig = createTempFile(prefix = cachedConfig.name)
          tmpConfig.writeText(json.encodeToString(ResolvedPluginsConfig.serializer(), result.config))
          try {
            cachedConfig.parent.createDirectories()
            tmpConfig.moveTo(cachedConfig, overwrite = false)
            logger.info("[fleet-dependencies] Plugins configurations fetched from Marketplace and cached in '$cachedConfig'")
            result.config
          } catch (_: FileAlreadyExistsException) {
            logger.info("[fleet-dependencies] Plugins configurations fetched from Marketplace but was concurrently cached in '$cachedConfig' by another project, using the one cached instead.")
            tmpConfig.deleteIfExists()
            DefaultJson.decodeFromString(ResolvedPluginsConfig.serializer(), cachedConfig.readText())
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalUuidApi::class)
private fun cacheKey(shipVersion: PluginVersion, plugins: Map<PluginName, PluginVersion?>, repository: PluginRepository): String {
  val toHash = buildString {
    append(repository.cacheKey())
    append(shipVersion.versionString)
    plugins.entries.sortedBy { (name, _) -> name.name }.forEach { (name, version) ->
      append(name.name)
      append(version?.versionString ?: "") // when no version is specified, we do not invalidate cache, the same way we do not for Marketplace repository, we do not have a lockfile yet, so we cannot have reproducibility, but we want at least reproducibility after the first resolution
    }
  }
  return sha256(toHash.toByteArray(Charsets.UTF_8))
}

val DefaultJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}