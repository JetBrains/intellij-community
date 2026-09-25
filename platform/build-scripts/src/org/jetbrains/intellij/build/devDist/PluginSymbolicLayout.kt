@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.assembleOrderedJarSources

/** An input identity, not an opened archive. Equal physical library files must use the same [id]. */
@ApiStatus.Internal
data class PluginSymbolicArtifact(
  @JvmField val id: String,
  @JvmField val kind: String,
  @JvmField val fileName: String,
  @JvmField val preparationKey: String? = null,
)

/**
 * The files of one JPS library in the order returned by the output provider.
 * [id] is the canonical container label. A complete unchanged library keeps this identity in the recipe, and the plan
 * names the container only. [files] serve the generator: the catalogue rule expands the container to its members.
 */
@ApiStatus.Internal
data class PluginSymbolicLibrary(
  @JvmField val libraryName: String,
  @JvmField val moduleName: String? = null,
  @JvmField val files: List<String>,
  @JvmField val id: String? = null,
)

/** Module roots and library files are declared even when their contents have not been built. */
@ApiStatus.Internal
data class PluginSymbolicArtifactCatalogue(
  @JvmField val artifacts: List<PluginSymbolicArtifact>,
  @JvmField val moduleRoots: Map<String, List<String>>,
  @JvmField val libraries: List<PluginSymbolicLibrary>,
  @JvmField val testModules: Set<String> = emptySet(),
)

/**
 * Descriptor text must reflect the selected variant, resolved includes, and descriptor preparation.
 * A null value in [moduleDescriptors] states that the descriptor is absent. An omitted key states that the fact is unknown.
 */
@ApiStatus.Internal
data class PluginSymbolicDescriptorFacts(
  @JvmField val pluginXml: String?,
  @JvmField val pluginXmlInput: String,
  @JvmField val moduleDescriptors: Map<String, PluginSymbolicModuleDescriptor?>,
  @JvmField val frontendRoots: Set<String> = emptySet(),
  @JvmField val packedElsewhere: Set<String> = emptySet(),
  /** The input already contains all descriptor callbacks and version patches. */
  @JvmField val isPluginXmlFinal: Boolean = false,
)

/** The fact the projection reads out of one content module descriptor: whether its root declares a `package`. */
@ApiStatus.Internal
data class PluginSymbolicModuleDescriptor(@JvmField val hasPackage: Boolean)

/** One development distribution. A null [distribution] projects the common part only. */
@ApiStatus.Internal
data class PluginSymbolicVariant(
  @JvmField val id: String,
  @JvmField val distribution: SupportedDistribution? = null,
  @JvmField val searchableOptions: Boolean = false,
  @JvmField val skipCustomResourceGenerators: Boolean = false,
  @JvmField val scramble: Boolean = false,
)

/**
 * Declares an opaque operation and all its contributions without running its callback.
 * [sources] replace a filtered root or supply a custom jar. [assets] declare generated files outside that jar.
 * [sourceContributions] maps each original root to its own ordered sources when multiple roots share an operation.
 * Such contributions must not share a source: a merged bundle cannot stand in for separate source positions.
 */
@ApiStatus.Internal
data class PluginSymbolicPreparedEffect(
  @JvmField val preparation: PluginPackingPreparation,
  @JvmField val sources: List<JarSourceRecipe> = emptyList(),
  @JvmField val assets: List<PluginPackingAsset> = emptyList(),
  @JvmField val sourceContributions: Map<String, List<JarSourceRecipe>> = emptyMap(),
)

/** One retained source slot after original source assembly. [ordinal] distinguishes repeated uses of the same input, destination, and channel. */
@ApiStatus.Internal
data class PluginSymbolicNativeOccurrence(
  @JvmField val destination: String,
  @JvmField val input: String,
  @JvmField val channel: PluginSymbolicNativeSourceChannel,
  @JvmField val ordinal: Int,
)

/** A native requirement derived at one source occurrence of the original jar. No archive has been opened. */
@ApiStatus.Internal
data class PluginSymbolicNativeUse(
  @JvmField val occurrence: PluginSymbolicNativeOccurrence,
  @JvmField val handling: PluginSymbolicNativeHandling,
  @JvmField val distributionPrefix: String?,
)

/**
 * Keys name layout slots: `layout-patcher:N`, `custom-asset:N`, `resource:N`, `resource-generator:N`,
 * `platform-resource-generator:N`, `platform-custom-asset:N`, `module-filter:MODULE`, `searchable-options:MODULE`,
 * or an artifact's preparation key. A `platform-*` index counts only the callbacks that serve the variant's distribution.
 * [modulePatches] gives the final patch order, including the descriptor, after every declared patcher runs.
 * [preparedSourceManifests] is keyed by prepared output ID and records original counts and concrete execution policies.
 * A descriptor callback uses `descriptor`; scrambling reports a gap rather than an unsafe replacement.
 */
@ApiStatus.Internal
data class PluginSymbolicPreparationFacts(
  @JvmField val effects: Map<String, PluginSymbolicPreparedEffect> = emptyMap(),
  @JvmField val modulePatches: Map<String, List<JarSourceRecipe>> = emptyMap(),
  @JvmField val dependencies: List<PluginPackingPreparation> = emptyList(),
  @JvmField val preparedSourceManifests: Map<String, PluginSymbolicPreparedSourceManifest> = emptyMap(),
  /** Declared assets that need no Kotlin preparation. Keys use the same layout slots as [effects]. */
  @JvmField val declaredAssets: Map<String, List<PluginPackingAsset>> = emptyMap(),
  /** Selected layout callback slots that intentionally contribute nothing to this development variant. */
  @JvmField val omittedSlots: Set<String> = emptySet(),
)

@ApiStatus.Internal
data class PluginSymbolicLayoutGap(
  @JvmField val key: String,
  @JvmField val detail: String,
)

/** Assets retain insertion order. A result with gaps cannot select producers or become a serialized projection. */
@ApiStatus.Internal
class PluginSymbolicLayout internal constructor(
  @JvmField val plugin: String,
  @JvmField val variant: String,
  @JvmField val assets: List<PluginPackingAsset>,
  @JvmField val preparations: List<PluginPackingPreparation>,
  @JvmField val preparationRoots: List<String>,
  @JvmField val gaps: List<PluginSymbolicLayoutGap>,
) {
  /**
   * The plan file content and the reuse decision. [artifacts] are the jars the plugin may reuse; the result names the
   * ones an asset matches, in asset order. The projection itself states no reuse.
   */
  fun projection(artifacts: Collection<ReusableJarArtifact> = emptyList()): PluginSymbolicProjection {
    check(gaps.isEmpty()) {
      "Plugin '$plugin' lacks symbolic facts: ${gaps.joinToString { "${it.key}: ${it.detail}" }}"
    }
    val plan = planPluginPacking(
      plugin = plugin,
      variant = variant,
      assets = assets,
      preparations = preparations,
      preparationRoots = preparationRoots,
      artifacts = artifacts,
    )
    val projection = PluginPackingProjection(
      version = pluginPackingExecutionVersion(assets),
      plugin = plugin,
      variant = variant,
      layoutSignature = plan.layoutSignature,
      assets = assets,
      preparations = preparations,
      preparationRoots = preparationRoots,
    )
    return PluginSymbolicProjection(projection, plan.assets.mapNotNull { it.artifact }.distinct())
  }
}

/** The plan file content of one plugin variant and the reused jars its assets match. */
@ApiStatus.Internal
class PluginSymbolicProjection(
  @JvmField val projection: PluginPackingProjection,
  @JvmField val reusableArtifacts: List<ReusableJarArtifact>,
)

internal class PluginSymbolicJarSource(
  @JvmField val identity: Any,
  @JvmField val library: PluginSymbolicLibraryGroup? = null,
  @JvmField val resolve: () -> List<JarSourceRecipe>,
) {
  constructor(recipe: JarSourceRecipe) : this(recipe, resolve = { listOf(recipe) })

  override fun equals(other: Any?): Boolean = other is PluginSymbolicJarSource && identity == other.identity

  override fun hashCode(): Int = identity.hashCode()
}

/** One complete library as a jar source. [files] are the member ids the group stands for, in their order. */
internal class PluginSymbolicLibraryGroup(@JvmField val recipe: JarSourceRecipe, @JvmField val files: List<String>)

internal data class PluginSymbolicLibrarySourceIdentity(
  @JvmField val input: String,
  @JvmField val presignedCandidate: Boolean?,
)

/** Keeps original source identities until assembly. Recipe grouping and preparation follow the writer's source deduplication. */
@ApiStatus.Internal
class PluginSymbolicJarAssembly {
  private val jars = LinkedHashMap<String, SymbolicJar>()

  fun addModule(destination: String, sources: List<JarSourceRecipe>, testOutput: Boolean = false, descriptorModule: Boolean = false) {
    addOriginalModule(destination, sources.map(::PluginSymbolicJarSource), testOutput, descriptorModule)
  }

  /** [descriptorModule] marks the module that holds the plugin descriptor. Its sources lead the jar, as in the writer. */
  internal fun addOriginalModule(
    destination: String,
    sources: List<PluginSymbolicJarSource>,
    testOutput: Boolean = false,
    descriptorModule: Boolean = false,
  ) {
    val jar = jars.computeIfAbsent(destination) { SymbolicJar() }
    jar.modules.add(sources)
    if (descriptorModule) {
      jar.descriptorModuleSources = sources
    }
    jar.testOutput = jar.testOutput || testOutput
  }

  fun addSources(destination: String, sources: List<JarSourceRecipe>, separate: Boolean = false) {
    addOriginalSources(destination, sources.map(::PluginSymbolicJarSource), separate)
  }

  internal fun addOriginalSources(destination: String, sources: List<PluginSymbolicJarSource>, separate: Boolean = false) {
    require(!separate || !jars.containsKey(destination)) { "Custom asset conflicts with '$destination'" }
    jars.computeIfAbsent(destination) { SymbolicJar() }.sources.addAll(sources)
  }

  /**
   * Marks the jar at [destination] as the jar of the presigned native library [library]. The jar leaves the library's
   * native entries out, and its native tree goes to [distributionPrefix] at the distribution root. Only a reused
   * `content_module_jar` packs such a jar, and the tree is its output.
   */
  internal fun markNatives(destination: String, library: String, distributionPrefix: String, reportGap: (PluginSymbolicLayoutGap) -> Unit) {
    val jar = jars.computeIfAbsent(destination) { SymbolicJar() }
    val natives = PluginSymbolicJarNatives(library, distributionPrefix)
    val previous = jar.natives
    if (previous != null && previous != natives) {
      reportGap(PluginSymbolicLayoutGap("native-library:$destination", "A jar merges two presigned native libraries: ${previous.library} and $library"))
      return
    }
    jar.natives = natives
  }

  fun assets(
    preparedSourceManifests: Map<String, PluginSymbolicPreparedSourceManifest> = emptyMap(),
    libraryFileCounts: Map<String, Int> = emptyMap(),
    reportGap: (PluginSymbolicLayoutGap) -> Unit = { error("${it.key}: ${it.detail}") },
  ): List<PluginPackingAsset> {
    return jars.mapNotNull { (destination, jar) ->
      if (jar.modules.isNotEmpty()) {
        val libraries = jar.sources.map { it.identity }.filterIsInstance<PluginSymbolicLibrarySourceIdentity>().groupBy { it.input }
        for ((input, identities) in libraries) {
          if (identities.any { it.presignedCandidate == null } && identities.any { it.presignedCandidate != null }) {
            reportGap(PluginSymbolicLayoutGap("source-identity:$destination:$input", "Native policy is required to compare the original library sources"))
          }
        }
      }
      val ordered = try {
        assembleOrderedJarSources(jar.sources, jar.modules, jar.descriptorModuleSources).toList()
      }
      catch (error: IllegalArgumentException) {
        reportGap(PluginSymbolicLayoutGap("source-identity:$destination", checkNotNull(error.message)))
        return@mapNotNull null
      }
      val sources = resolveSources(ordered)
      val natives = jar.natives
      if (sources.isEmpty()) {
        emptyList()
      }
      else if (natives == null) {
        val asset = PluginPackingAsset(
          destination = destination,
          inputs = sources.map { it.input }.distinct(),
          recipe = CanonicalJarRecipe(sources = sources, writer = JarWriterRecipe(mergeEntities = true, directoryEntries = jar.testOutput)),
        )
        listOf(resolvePluginSymbolicManifest(asset, preparedSourceManifests, libraryFileCounts, reportGap))
      }
      else {
        val owner = sources.first()
        if (owner.kind != "module" || sources.drop(1).any { it.kind != "library" } || jar.testOutput) {
          reportGap(PluginSymbolicLayoutGap("native-library:$destination", "A jar with a presigned native library must be one module jar with its libraries"))
          return@mapNotNull null
        }
        listOf(
          PluginPackingAsset(
            destination = destination,
            inputs = sources.map { it.input }.distinct(),
            recipe = CanonicalJarRecipe(sources = sources, writer = JarWriterRecipe(mergeEntities = true, nativeLib = natives.library)),
          ),
          PluginPackingAsset(
            destination = natives.distributionPrefix.removeSuffix("/"),
            inputs = listOf(NATIVE_TREE_INPUT_PREFIX + owner.input),
            kind = "tree",
            classPath = false,
            scope = DISTRIBUTION_ASSET_SCOPE,
          ),
        )
      }
    }.flatten()
  }

  private fun resolveSources(ordered: List<PluginSymbolicJarSource>): List<JarSourceRecipe> {
    val result = ArrayList<JarSourceRecipe>()
    var index = 0
    while (index < ordered.size) {
      val source = ordered.get(index)
      val group = source.library
      if (group == null) {
        result.addAll(source.resolve())
        index++
        continue
      }
      val resolved = ArrayList<JarSourceRecipe>()
      var count = 0
      while (index < ordered.size && ordered.get(index).library === group) {
        resolved.addAll(ordered.get(index).resolve())
        count++
        index++
      }
      val files = group.files
      if (count == files.size && resolved == files.map { JarSourceRecipe(it, "archive", "library-v1") }) {
        result.add(group.recipe)
      }
      else {
        result.addAll(resolved)
      }
    }
    return result
  }

  private class SymbolicJar {
    val sources = ArrayList<PluginSymbolicJarSource>()
    val modules = ArrayList<List<PluginSymbolicJarSource>>()
    var descriptorModuleSources: List<PluginSymbolicJarSource>? = null
    var testOutput = false
    var natives: PluginSymbolicJarNatives? = null
  }
}

private data class PluginSymbolicJarNatives(val library: String, val distributionPrefix: String)
