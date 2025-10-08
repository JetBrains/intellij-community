package fleet.bundles

import fleet.util.async.catching
import fleet.util.letIf
import fleet.util.logging.logger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class PluginCommand {
  @Serializable
  @SerialName("add")
  data class Add(val pluginName: PluginName, val exactVersion: PluginVersion? = null) : PluginCommand()

  @SerialName("remove")
  @Serializable
  data class Remove(val pluginName: PluginName) : PluginCommand()
}

data class RequestedPluginConfiguration(
  val requestedVersions: Map<PluginName, PluginVersion?>,
  val forbidInstallation: Set<PluginName>
) {
  companion object {
    val EMPTY = RequestedPluginConfiguration(emptyMap(), emptySet())
  }
}

private val log by lazy { logger<PluginsConfig>() }

/**
 * collected from settings
 * */
data class PluginsConfig(val commands: List<PluginCommand>) {
  companion object
}

data class PluginResolutionResult(val config: ResolvedPluginsConfig,
                                  val problems: List<Problem>) {
  sealed class Problem {
    data class PluginNotFound(val name: PluginName) : Problem()
    data class Conflict(val pluginName: PluginName,
                        val dependencyName: PluginName,
                        val takenVersion: PluginVersion,
                        val requiredVersion: VersionRequirement) : Problem()
    data class FetchIssue(val pluginName: PluginName,
                          val throwable: Throwable) : Problem()
  }
}

private data class FrontResolutionArguments(
  val alreadyResolvedPlugins: Map<PluginName, PluginDescriptor>,
  val front: Map<PluginName, PluginVersion>
)

private data class FrontResolutionResult(
  val resolvedPlugins: Map<PluginName, PluginDescriptor>,
  val nextFront: Map<PluginName, PluginVersion>,
  val problemsFound: List<PluginResolutionResult.Problem>
)

private typealias FrontResolution = suspend (FrontResolutionArguments) -> FrontResolutionResult

/**
 * Resolution algorithm. Resolves target plugins layer by layer using provided `frontResolution` lambda.
 * @param frontResolution lambda to resolve the current layer and define the next one.
 * @param concretedPlugins additional fixed plugin set used as a base layer to satisfy dependencies.
 * @return `PluginResolutionResult` with all the plugins which dependencies were satisfied
 */
private suspend fun resolvePluginsConfig(
  pluginsConfig: PluginsConfig,
  shipVersion: PluginVersion,
  repo: PluginRepository,
  concretedPlugins: Map<PluginName, PluginDescriptor> = emptyMap(),
  frontResolution: FrontResolution
): PluginResolutionResult {
  val problems = ArrayList<PluginResolutionResult.Problem>()
  val requestedPluginConfiguration = calculateRequestedPlugins(pluginsConfig)
  val requestedPlugins = requestedPluginConfiguration.requestedVersions
  val forbidInstallation = requestedPluginConfiguration.forbidInstallation
  val pluginsWithVersionRequirement = requestedPlugins.mapNotNullTo(HashSet()) { (name, version) ->
    if (version != null) name else null
  }
  val pluginsWithoutVersionRequirement = requestedPlugins.mapNotNullTo(HashSet()) { (name, version) ->
    if (version == null) name else null
  }
  val latestVersions = repo.getLatestVersions(pluginsWithoutVersionRequirement, shipVersion)

  problems.addAll(pluginsWithoutVersionRequirement
                    .filter { name -> !latestVersions.containsKey(name) }
                    .map(PluginResolutionResult.Problem::PluginNotFound))

  val requestedPluginsWithVersions =
    (pluginsWithVersionRequirement.map { name ->
      name to requestedPlugins[name]!!
    } +
     pluginsWithoutVersionRequirement.mapNotNull { name ->
       latestVersions[name]?.let { version -> name to version }
     }).toMap()

  val pluginsToLoad = run {
    val resolvedPlugins = hashMapOf<PluginName, PluginDescriptor>()
    var front = requestedPluginsWithVersions
    while (front.isNotEmpty()) {
      front = front.filterNot { forbidInstallation.contains(it.key) } // the whole subtree will be dropped, no reason to resolve it
      val arguments = FrontResolutionArguments(resolvedPlugins, front)
      val result = frontResolution(arguments)
      resolvedPlugins.putAll(result.resolvedPlugins)
      problems.addAll(result.problemsFound)
      front = result.nextFront
    }
    filterMissingPluginsOut(resolvedPlugins, concretedPlugins, requestedPluginsWithVersions.keys)
  }

  return PluginResolutionResult(ResolvedPluginsConfig(bundlesToLoad = pluginsToLoad.values.toSet(),
                                                      shipVersion = shipVersion),
                                problems = problems)
}

/**
 * Resolves plugins from provided `PluginsConfig` using the provided `PluginRepository`.
 *
 * @param ignoreFrontendOnly - to filter out frontend-only plugins, such as themes, keymaps or icon packs during resolution
 * @return PluginResolutionResult with all the plugins which dependencies were satisfied
 */
suspend fun resolveWorkspacePlugins(
  pluginsConfig: PluginsConfig,
  shipVersion: PluginVersion,
  repo: PluginRepository,
  ignoreFrontendOnly: Boolean = true,
): PluginResolutionResult {
  return resolvePluginsConfig(pluginsConfig, shipVersion, repo) { arguments ->
    val problems = ArrayList<PluginResolutionResult.Problem>()
    val pluginsByNamePrime = repo.getPlugins(arguments.front).let { fetchingResult ->
      problems.addAll(fetchingResult.problems)
      fetchingResult.descriptors.letIf(ignoreFrontendOnly) { ds ->
        ds.filterNot { it.value.meta[KnownMeta.FrontendOnly] == "true" }
      }
    }
    val conflicts: Map<PluginName, List<PluginVersion>> = pluginsByNamePrime
      .flatMap { (pluginName, plugin) ->
        plugin.deps.mapNotNull { (dependencyName, versionRequirement) ->
          val satisfied = arguments.alreadyResolvedPlugins[dependencyName]
          when {
            satisfied == null -> dependencyName to versionRequirement.version
            satisfied.version.satisfies(versionRequirement) -> null
            else -> {
              //TODO: try to satisfy existing dependencies with the required version???
              problems.add(PluginResolutionResult.Problem.Conflict(pluginName = pluginName,
                                                                   dependencyName = dependencyName,
                                                                   takenVersion = satisfied.version,
                                                                   requiredVersion = versionRequirement))
              null
            }
          }
        }
      }
      .groupBy(keySelector = { (name, _) -> name },
               valueTransform = { (_, requirement) -> requirement })

    val nextDeps = conflicts.map { (name, requirements) ->
      //TODO: handle conflicts better
      name to requirements.max()
    }.toMap()

    FrontResolutionResult(resolvedPlugins = pluginsByNamePrime,
                          nextFront = nextDeps,
                          problemsFound = problems)
  }
}


/**
 * Resolves frontend only plugins from provided config, using workspaceResolvedConfig as a base layer.
 *
 * @return only frontend-only plugins, which dependencies were satisfied by the workspaceResolvedConfig
 */
suspend fun resolveFrontendOnlyPlugins(
  pluginsConfig: PluginsConfig,
  shipVersion: PluginVersion,
  repo: PluginRepository,
  workspaceResolvedConfig: ResolvedPluginsConfig
): PluginResolutionResult {
  val concretedBaseLayer = workspaceResolvedConfig.bundlesToLoad.associateBy { it.name }
  return resolvePluginsConfig(pluginsConfig, shipVersion, repo, concretedBaseLayer) { arguments ->
    val problems = ArrayList<PluginResolutionResult.Problem>()
    val pluginsByNamePrime = repo.getPlugins(arguments.front)
      .let { fetchingResult ->
        problems.addAll(fetchingResult.problems)
        fetchingResult.descriptors
      }
      .filterValues { it.meta[KnownMeta.FrontendOnly] == "true" }
    val conflicts: Map<PluginName, List<PluginVersion>> = pluginsByNamePrime
      .flatMap { (pluginName, plugin) ->
        plugin.deps.mapNotNull { (dependencyName, versionRequirement) ->
          val satisfiedByWorkspace =  concretedBaseLayer[dependencyName]
          val satisfied = arguments.alreadyResolvedPlugins[dependencyName]
          val frontendOnly = plugin.meta[KnownMeta.FrontendOnly] == "true"
          if (frontendOnly) when {
            satisfied == null -> dependencyName to versionRequirement.version
            satisfied.version.satisfies(versionRequirement) -> null
            else -> {
              // TODO: try to satisfy existing dependencies with the required version???
              problems.add(PluginResolutionResult.Problem.Conflict(pluginName = pluginName,
                                                                   dependencyName = dependencyName,
                                                                   takenVersion = satisfied.version,
                                                                   requiredVersion = versionRequirement))
              null
            }
          }
          else {
            when {
              satisfiedByWorkspace == null -> {
                problems.add(PluginResolutionResult.Problem.PluginNotFound(pluginName))
              }
              !satisfiedByWorkspace.version.satisfies(versionRequirement) -> {
                problems.add(PluginResolutionResult.Problem.Conflict(pluginName = pluginName,
                                                                     dependencyName = dependencyName,
                                                                     takenVersion = satisfiedByWorkspace.version,
                                                                     requiredVersion = versionRequirement))
              }
            }
            null
          }
        }
      }
      .groupBy(keySelector = { (name, _) -> name },
               valueTransform = { (_, requirement) -> requirement })

    val nextDeps = conflicts.map { (name, requirements) ->
      //TODO: handle conflicts better
      name to requirements.max()
    }.toMap()

    FrontResolutionResult(resolvedPlugins = pluginsByNamePrime,
                          nextFront = nextDeps,
                          problemsFound = problems)
  }
}

fun calculateRequestedPlugins(pluginsConfig: PluginsConfig): RequestedPluginConfiguration {
  val versionsToInstall = mutableMapOf<PluginName, PluginVersion?>()
  val forbidInstallation = mutableSetOf<PluginName>()

  pluginsConfig.commands.forEach { command ->
    when (command) {
      is PluginCommand.Add -> versionsToInstall[command.pluginName] = command.exactVersion
      is PluginCommand.Remove -> {
        versionsToInstall.remove(command.pluginName)
        forbidInstallation.add(command.pluginName)
      }
    }
  }

  return RequestedPluginConfiguration(versionsToInstall, forbidInstallation)
}

private fun filterMissingPluginsOut(resolvedOnes: Map<PluginName, PluginDescriptor>,
                                    concretedPlugins: Map<PluginName, PluginDescriptor>,
                                    requestedPlugins: Set<PluginName>): Map<PluginName, PluginDescriptor> {
  fun walkSubtree(resolvedOnes: Map<PluginName, PluginDescriptor>,
                  pluginName: PluginName): Map<PluginName, PluginDescriptor>? =
    if (concretedPlugins[pluginName] != null) {
      emptyMap()
    }
    else {
      resolvedOnes[pluginName]?.let { plugin ->
        HashMap<PluginName, PluginDescriptor>().let { res ->
          res[pluginName] = plugin
          plugin.deps.keys.forEach { dep ->
            val subtree = walkSubtree(resolvedOnes, dep)
            if (subtree == null) {
              log.warn { "Filtering out plugin ${pluginName.name}, dependency ${dep.name} not found in resolved plugins" }
              return null
            }
            else {
              res.putAll(subtree)
            }
          }
          res
        }
      }
    }
  return hashMapOf<PluginName, PluginDescriptor>().apply {
    requestedPlugins.mapNotNull { walkSubtree(resolvedOnes, it) }.forEach { m ->
      putAll(m)
    }
  }
}

@Serializable
data class ResolvedPluginsConfig(val bundlesToLoad: Set<PluginDescriptor>,
                                 val shipVersion: PluginVersion) {
  companion object {
    val json = Json { prettyPrint = true }
  }

  override fun toString(): String = json.encodeToString(serializer(), this)
}

data class PluginFetchingResult(
  val descriptors: Map<PluginName, PluginDescriptor>,
  val problems: List<PluginResolutionResult.Problem.FetchIssue>
)

private suspend fun PluginRepository.getPlugins(plugins: Map<PluginName, PluginVersion>): PluginFetchingResult =
  coroutineScope {
    val problems = mutableListOf<PluginResolutionResult.Problem.FetchIssue>()
    val descriptors = plugins.map { (name, version) ->
      name to async {
        catching { getPlugin(name, version) }
      }
    }.mapNotNull { (name, deferred) ->
      deferred.await().let { result ->
        result.onFailure { e ->
          problems.add(PluginResolutionResult.Problem.FetchIssue(name, e))
        }.getOrNull()?.let {
          plugin -> name to plugin
        }
      }
    }.toMap()
    PluginFetchingResult(descriptors, problems)
  }

private fun PluginVersion.satisfies(requirement: VersionRequirement): Boolean {
  return when (requirement) {
    is VersionRequirement.Above -> this >= requirement.version
    is VersionRequirement.CompatibleWith -> major == requirement.version.major && this >= requirement.version
  }
}

private fun PluginResolutionResult.Problem.presentable() = when (this) {
  is PluginResolutionResult.Problem.Conflict -> "$pluginName - CONFLICT: ${dependencyName.name} requires ${requiredVersion.version}, but ${takenVersion.presentableText} was taken"
  is PluginResolutionResult.Problem.FetchIssue -> "$pluginName - FETCH ISSUE"
  is PluginResolutionResult.Problem.PluginNotFound -> "${name.name} - PLUGIN NOT FOUND"
}

fun PluginResolutionResult.simplifiedPresentation(): String = buildString {
  appendLine()
  append("PROBLEMS:")
  append(problems.joinToString(separator = "\n") { it.presentable() })
  appendLine()
  append("SHIP version: ${config.shipVersion.presentableText}")
  appendLine()
  append("DESCRIPTORS:")
  appendLine()
  append(config.bundlesToLoad.joinToString(separator = "\n") { "${it.name.name} - ${it.version.presentableText}" })
  appendLine()
  append("_".repeat(40))
  appendLine()
}