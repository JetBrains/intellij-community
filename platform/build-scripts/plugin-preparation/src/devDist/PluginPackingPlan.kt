@file:Suppress("DestructuringDeclaration", "ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dev.devBuildPathIdentity
import org.jetbrains.intellij.build.dev.validateDevBuildDirectorySpellings
import java.nio.file.Path

@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PreparedSourceManifestRecipe(
  @EncodeDefault @JvmField val version: Int = 1,
  /** A null count counts module patches. Each patch entry represents one original file source. */
  @JvmField val originalMeaningfulSourceCount: Int? = null,
  @JvmField val sourceManifestPolicies: List<String>,
) {
  init {
    require(version == 1) { "Unsupported prepared manifest version '$version'" }
    require(originalMeaningfulSourceCount == null || originalMeaningfulSourceCount >= 0) { "Invalid prepared source count" }
    require(sourceManifestPolicies.all { it in setOf("keep", "drop", "coverage-agent", "rewrite-boot-class-path", "single-meaningful-source") }) {
      "Unsupported prepared manifest policies: $sourceManifestPolicies"
    }
    require(originalMeaningfulSourceCount != null || sourceManifestPolicies == listOf("keep")) {
      "Module patches require the keep policy"
    }
  }
}

@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class JarSourceRecipe(
  @JvmField val input: String,
  @JvmField val kind: String,
  @JvmField val filter: String,
  @JvmField val entry: String = "",
  @JvmField val options: List<String> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val preparedManifest: PreparedSourceManifestRecipe? = null,
) {
  init {
    require(preparedManifest == null || (kind == "prepared" && filter == "prepared" && entry.isEmpty() && options.isEmpty())) {
      "Only a symbolic prepared source can declare a prepared manifest recipe"
    }
  }
}

@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class JarWriterRecipe(
  @JvmField val manifest: String = "single-meaningful-source",
  @JvmField val mergeEntities: Boolean = false,
  @JvmField val directoryEntries: Boolean = false,
  @JvmField val rewriteBootClassPath: Boolean = false,
  @JvmField val outputName: String = "",
  /**
   * The presigned native library whose native entries the jar leaves out, or empty. A jar with it and a jar without it
   * differ, so a plugin reuses a natives jar only with a recipe that states the same library.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val nativeLib: String = "",
)

@ApiStatus.Internal
@Serializable
data class CanonicalJarRecipe(
  @JvmField val sources: List<JarSourceRecipe>,
  @JvmField val writer: JarWriterRecipe = JarWriterRecipe(),
) {
  init {
    require(sources.isNotEmpty()) { "A jar recipe requires ordered sources" }
    require(sources.all { it.input.isNotEmpty() && it.kind.isNotEmpty() && it.filter.isNotEmpty() }) {
      "A jar source requires an input, a root kind, and a filter"
    }
    require(!writer.rewriteBootClassPath || writer.outputName.isNotEmpty()) { "A manifest rewrite requires the output name" }
    require(sources.none { "manifest=rewrite-boot-class-path" in it.options } || writer.outputName.isNotEmpty()) {
      "An explicit manifest rewrite requires the output name"
    }
  }
}

/**
 * The `content_module_jar` output of [module], which a plan reuses in place of a remainder operation. The plan file
 * states no such row: the chain hands the reused module names to the packer, and the generator matches an asset to
 * the jar by [recipe] and [mode].
 */
@ApiStatus.Internal
data class ReusableJarArtifact(
  @JvmField val module: String,
  @JvmField val recipe: CanonicalJarRecipe,
  @JvmField val mode: Int = 420,
)

/**
 * A tree reserves its root and all descendants. It uses one directory input and never contributes to the classpath.
 * Preparation resolves its entries, modes, and links. The default asset mode does not override the source modes.
 * Tree projections, execution recipes, and collector specifications require version 2.
 * Version 1 retains its file and empty-directory semantics. Catalogues and inventories still use version 1.
 */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PluginPackingAsset(
  @JvmField val destination: String,
  @JvmField val inputs: List<String>,
  @JvmField val recipe: CanonicalJarRecipe? = null,
  @JvmField val mode: Int = 420,
  @JvmField val symlinkTarget: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val kind: String = "file",
  /** Whether the original distribution contributes this file to the plugin classpath. Directories never contribute. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val classPath: Boolean = true,
  /** Whether the writer gives copied tree files and directories the standard resource modes. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val normalizeTreeModes: Boolean = false,
  /** Selects the plugin root or the distribution root for [destination]. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val scope: String = PLUGIN_ASSET_SCOPE,
)

@ApiStatus.Internal
const val PLUGIN_ASSET_SCOPE: String = "plugin"

@ApiStatus.Internal
const val DISTRIBUTION_ASSET_SCOPE: String = "distribution"

/**
 * The input of the native tree a reused natives jar writes: `native-tree:<module>`. The tree asset is at the
 * distribution root, and the reused `content_module_jar` of the module produces it.
 */
@ApiStatus.Internal
const val NATIVE_TREE_INPUT_PREFIX: String = "native-tree:"

/** Whether [asset] is the native tree of a reused natives jar, see [NATIVE_TREE_INPUT_PREFIX]. */
@ApiStatus.Internal
fun isNativeTreeAsset(asset: PluginPackingAsset): Boolean {
  return asset.kind == "tree" && asset.inputs.singleOrNull()?.startsWith(NATIVE_TREE_INPUT_PREFIX) == true
}

@ApiStatus.Internal
fun pluginPackingExecutionVersion(assets: List<PluginPackingAsset>): Int {
  return when {
    assets.any { it.scope == DISTRIBUTION_ASSET_SCOPE } -> 3
    assets.any { it.kind == "tree" } -> 2
    else -> 1
  }
}

@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PluginPackingPreparation(
  @JvmField val id: String,
  @JvmField val inputs: List<String>,
  @JvmField val outputs: List<String>,
  @JvmField val modelSignature: String,
  /** Runs the validation action even when it produces no file outputs. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val alwaysRun: Boolean = false,
)

@ApiStatus.Internal
data class PlannedPluginAsset(
  @JvmField val asset: PluginPackingAsset,
  @JvmField val artifact: ReusableJarArtifact?,
)

@ApiStatus.Internal
class PluginPackingPlan internal constructor(
  @JvmField val plugin: String,
  @JvmField val variant: String,
  @JvmField val layoutSignature: String,
  @JvmField val assets: List<PlannedPluginAsset>,
  @JvmField val preparations: List<PluginPackingPreparation>,
  @JvmField val requiredInputs: List<String>,
) {
  fun validateLayout(signature: String) {
    check(layoutSignature == signature) {
      "Plugin '$plugin' has a stale layout plan: stored=$signature computed=$layoutSignature. Regenerate the dev distribution declarations."
    }
  }
}

@ApiStatus.Internal
fun planPluginPacking(
  plugin: String,
  variant: String,
  assets: List<PluginPackingAsset>,
  preparations: List<PluginPackingPreparation>,
  preparationRoots: List<String>,
  artifacts: Collection<ReusableJarArtifact>,
): PluginPackingPlan {
  require(plugin.isNotEmpty()) { "A plugin plan requires a plugin" }
  val hasDirectories = assets.any { it.kind == "directory" || it.kind == "tree" }
  if (hasDirectories) {
    for (scope in assets.map(PluginPackingAsset::scope).distinct()) {
      validateDevBuildDirectorySpellings(assets.filter { it.scope == scope }.map { it.destination }.filter(String::isNotEmpty))
    }
  }
  fun pathIdentity(path: String): String = if (hasDirectories) devBuildPathIdentity(path) else path
  val destinations = HashMap<Pair<String, String>, PluginPackingAsset>()
  for (asset in assets) {
    require(asset.scope in setOf(PLUGIN_ASSET_SCOPE, DISTRIBUTION_ASSET_SCOPE)) { "Unknown plugin asset scope '${asset.scope}'" }
    validateDestination(asset.destination, allowRoot = asset.kind == "tree" && asset.scope == PLUGIN_ASSET_SCOPE)
    require(destinations.putIfAbsent(asset.scope to pathIdentity(asset.destination), asset) == null) {
      "Plugin '$plugin' has conflicting destination '${asset.destination}'"
    }
    require(asset.inputs.none(String::isEmpty)) { "Plugin '${plugin}' has an empty input" }
    require(asset.kind in setOf("file", "directory", "tree")) { "Unsupported plugin asset kind '${asset.kind}'" }
    require(asset.mode in 0..511 && (asset.mode != 0 || asset.kind == "file" && asset.recipe == null && asset.symlinkTarget == null)) {
      "Plugin '$plugin' has an unsupported mode for '${asset.destination}'"
    }
    require(!asset.normalizeTreeModes || asset.kind == "tree") { "Only a plugin tree can normalize copied modes: '${asset.destination}'" }
    require(
      asset.kind != "tree" || (asset.inputs.size == 1 && asset.recipe == null && asset.symlinkTarget == null &&
                               !asset.classPath && asset.mode == 420)
    ) {
      "Plugin tree '${asset.destination}' requires one directory input, no jar recipe, no link target, no classpath, and no mode override"
    }
    require(asset.kind != "directory" || (asset.inputs.isEmpty() && asset.recipe == null && asset.symlinkTarget == null)) {
      "Plugin directory '${asset.destination}' must not declare file inputs or a link target"
    }
    require(asset.scope != DISTRIBUTION_ASSET_SCOPE || !asset.classPath) {
      "Distribution asset '${asset.destination}' must not contribute to the plugin classpath"
    }
    asset.symlinkTarget?.let { target ->
      require(asset.recipe == null && asset.inputs.isEmpty()) { "Plugin link '${asset.destination}' must not declare file inputs" }
      val resolved = Path.of(asset.destination).parent?.resolve(target) ?: Path.of(target)
      require(target.isNotEmpty() && !Path.of(target).isAbsolute && target.none { it == '\\' || it == ':' || it == '\u0000' } &&
              !resolved.normalize().startsWith("..")) { "Plugin link '${asset.destination}' escapes the plugin: $target" }
    }
    require(asset.recipe == null || asset.inputs.containsAll(asset.recipe.sources.map(JarSourceRecipe::input))) {
      "Plugin '$plugin' does not declare every source of '${asset.destination}'"
    }
  }
  for ((scope, destination) in destinations.keys) {
    var parent = destination.substringBeforeLast('/', "")
    while (parent.isNotEmpty()) {
      val parentKey = scope to parent
      require(parentKey !in destinations || destinations.getValue(parentKey).kind in setOf("directory", "tree")) {
        "Plugin '$plugin' has conflicting destinations '$parent' and '$destination'"
      }
      parent = parent.substringBeforeLast('/', "")
    }
  }
  val recipes = LinkedHashMap<Pair<CanonicalJarRecipe, Int>, ReusableJarArtifact>()
  val artifactModules = HashMap<String, Pair<CanonicalJarRecipe, Int>>()
  for (artifact in artifacts) {
    require(artifact.module.isNotEmpty()) { "A reusable artifact requires a module" }
    require(artifact.mode in 1..511) { "Artifact '${artifact.module}' has an unsupported mode" }
    val key = artifact.recipe to artifact.mode
    val previous = artifactModules.putIfAbsent(artifact.module, key)
    require(previous == null || previous == key) { "Artifact '${artifact.module}' has conflicting recipes" }
    recipes.putIfAbsent(key, artifact)
  }
  val planned = assets.map { asset ->
    val recipe = asset.recipe
    val nativeTreeModule = if (isNativeTreeAsset(asset)) asset.inputs.single().removePrefix(NATIVE_TREE_INPUT_PREFIX) else null
    if (nativeTreeModule != null) {
      require(asset.scope == DISTRIBUTION_ASSET_SCOPE) { "Plugin '$plugin' places the native tree '${asset.destination}' in the plugin" }
      // No owner in a plan without reuse. The caller refuses a native tree that its final plan leaves unowned.
      val owner = recipes.values.firstOrNull { it.module == nativeTreeModule && it.recipe.writer.nativeLib.isNotEmpty() }
      return@map PlannedPluginAsset(asset = asset, artifact = owner)
    }
    val artifact = if (recipe != null && recipe.sources.none { it.kind == "prepared" } &&
                       asset.inputs.toSet() == recipe.sources.mapTo(HashSet(), JarSourceRecipe::input)) {
      recipes.get(recipe to asset.mode)
    }
    else {
      null
    }
    PlannedPluginAsset(asset = asset, artifact = artifact)
  }
  val producers = HashMap<String, PluginPackingPreparation>()
  val preparationIds = HashSet<String>()
  for (preparation in preparations) {
    require(preparation.id.isNotEmpty() && preparation.modelSignature.isNotEmpty() && preparationIds.add(preparation.id)) {
      "Plugin '$plugin' has an invalid or repeated preparation '${preparation.id}'"
    }
    for (output in preparation.outputs) {
      require(output.isNotEmpty() && producers.putIfAbsent(output, preparation) == null) {
        "Plugin '$plugin' has conflicting preparation output '$output'"
      }
    }
  }
  val requiredInputs = LinkedHashSet<String>()
  val requiredPreparations = LinkedHashSet<PluginPackingPreparation>()
  val visiting = HashSet<String>()
  fun requireInput(input: String) {
    require(input.isNotEmpty()) { "Plugin '$plugin' has an empty preparation input" }
    val preparation = producers.get(input)
    if (preparation == null) {
      requiredInputs.add(input)
      return
    }
    if (preparation in requiredPreparations) {
      return
    }
    require(visiting.add(preparation.id)) { "Plugin '$plugin' has a preparation cycle at '${preparation.id}'" }
    preparation.inputs.forEach(::requireInput)
    visiting.remove(preparation.id)
    requiredPreparations.add(preparation)
  }
  for (asset in planned) {
    if (asset.artifact == null && !isNativeTreeAsset(asset.asset)) {
      asset.asset.inputs.forEach(::requireInput)
    }
  }
  preparationRoots.forEach(::requireInput)
  for (preparation in preparations.filter { it.alwaysRun }) {
    preparation.inputs.forEach(::requireInput)
    requiredPreparations.add(preparation)
  }
  return PluginPackingPlan(
    plugin = plugin,
    variant = variant,
    layoutSignature = pluginPackingLayoutSignature(plugin, variant, assets, preparations, preparationRoots),
    assets = planned,
    preparations = requiredPreparations.toList(),
    requiredInputs = requiredInputs.toList(),
  )
}

@ApiStatus.Internal
fun pluginPackingLayoutSignature(
  plugin: String,
  variant: String,
  assets: List<PluginPackingAsset>,
  preparations: List<PluginPackingPreparation>,
  preparationRoots: List<String>,
): String {
  return devDistSignature {
    fun texts(values: List<String>) {
      putInt(values.size)
      for (value in values) putString(value)
    }

    val scopedAssets = assets.any { it.scope != PLUGIN_ASSET_SCOPE }
    val trees = assets.any { it.kind == "tree" }
    val preparedManifests = trees || assets.any { asset -> asset.recipe?.sources?.any { it.preparedManifest != null } == true }
    val classPathFacts = preparedManifests || assets.any { !it.classPath }
    val directories = classPathFacts || assets.any { it.kind != "file" } || preparations.any { it.alwaysRun }
    putInt(if (scopedAssets) 6 else if (trees) 5 else if (preparedManifests) 4 else if (classPathFacts) 3 else if (directories) 2 else 1)
    putString(plugin)
    putString(variant)
    putInt(assets.size)
    for (asset in assets) {
      putString(asset.destination)
      if (scopedAssets) putString(asset.scope)
      if (directories) putString(asset.kind)
      if (classPathFacts) putBoolean(asset.classPath)
      putInt(asset.mode)
      putBoolean(asset.symlinkTarget != null)
      asset.symlinkTarget?.let { putString(it) }
      texts(asset.inputs)
      val recipe = asset.recipe
      putBoolean(recipe != null)
      if (recipe != null) {
        putInt(recipe.sources.size)
        for (source in recipe.sources) {
          putString(source.input)
          putString(source.kind)
          putString(source.filter)
          putString(source.entry)
          texts(source.options)
          if (preparedManifests) {
            val manifest = source.preparedManifest
            putBoolean(manifest != null)
            if (manifest != null) {
              putInt(manifest.version)
              putInt(manifest.originalMeaningfulSourceCount ?: -1)
              texts(manifest.sourceManifestPolicies)
            }
          }
        }
        putString(recipe.writer.manifest)
        putBoolean(recipe.writer.mergeEntities)
        putBoolean(recipe.writer.directoryEntries)
        putBoolean(recipe.writer.rewriteBootClassPath)
        putString(recipe.writer.outputName)
      }
    }
    putInt(preparations.size)
    for (preparation in preparations) {
      putString(preparation.id)
      texts(preparation.inputs)
      texts(preparation.outputs)
      putString(preparation.modelSignature)
      if (directories) putBoolean(preparation.alwaysRun)
    }
    texts(preparationRoots)
  }
}

private fun validateDestination(destination: String, allowRoot: Boolean) {
  require(
    destination.isEmpty() && allowRoot ||
    destination.isNotEmpty() && '\\' !in destination && ':' !in destination && '\u0000' !in destination &&
    destination.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
    "Unsafe plugin destination '$destination'"
  }
}
