package org.jetbrains.intellij.build.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.ReusableJarArtifact

@ApiStatus.Internal
const val CWM_FRONTEND_CALLBACK_KIND: String = "cwm-frontend-v1"

/** Executable inputs for one complete owner of library layout preparations. Lists retain their declaration order. */
@ApiStatus.Internal
@Serializable
class DevPluginLibraryLayoutRecipeConfiguration(
  @JvmField val version: Int = 1,
  @JvmField val mainModule: String,
  @JvmField val directoryName: String,
  @JvmField val mainJarName: String,
  @JvmField val idPrefix: String,
  @JvmField val layoutPatcherCount: Int,
  @SerialName("modules") private var orderedModules: List<DevPluginLibraryLayoutModule>,
  @SerialName("exclusions") private var orderedExclusions: List<DevPluginLibraryLayoutExclusion>,
  @SerialName("filters") private var orderedFilters: List<DevPluginLibraryLayoutRecipeFilter>,
  @SerialName("callbacks") private var orderedCallbacks: List<DevPluginLibraryLayoutCallback>,
  @SerialName("patchOutputs") private var orderedPatchOutputs: List<DevPluginLibraryLayoutRecipeOutput>,
  @SerialName("seeds") private var orderedSeeds: List<DevPluginLibraryLayoutRecipeSeed>,
  @SerialName("operations") private var orderedOperations: List<DevPluginLibraryLayoutRecipeOperation>,
  @SerialName("consumers") private var orderedConsumers: List<DevPluginLibraryLayoutConsumer>,
) {
  val modules: List<DevPluginLibraryLayoutModule> get() = orderedModules
  val exclusions: List<DevPluginLibraryLayoutExclusion> get() = orderedExclusions
  val filters: List<DevPluginLibraryLayoutRecipeFilter> get() = orderedFilters
  val callbacks: List<DevPluginLibraryLayoutCallback> get() = orderedCallbacks
  val patchOutputs: List<DevPluginLibraryLayoutRecipeOutput> get() = orderedPatchOutputs
  val seeds: List<DevPluginLibraryLayoutRecipeSeed> get() = orderedSeeds
  val operations: List<DevPluginLibraryLayoutRecipeOperation> get() = orderedOperations
  val consumers: List<DevPluginLibraryLayoutConsumer> get() = orderedConsumers

  init {
    require(version == 1) { "Unsupported library layout recipe version $version" }
    require(layoutPatcherCount > 0) { "A layout callback recipe requires an original callback count" }
    orderedModules = java.util.List.copyOf(orderedModules)
    orderedExclusions = java.util.List.copyOf(orderedExclusions)
    orderedFilters = java.util.List.copyOf(orderedFilters)
    orderedCallbacks = java.util.List.copyOf(orderedCallbacks)
    orderedPatchOutputs = java.util.List.copyOf(orderedPatchOutputs)
    orderedSeeds = java.util.List.copyOf(orderedSeeds)
    orderedOperations = java.util.List.copyOf(orderedOperations)
    orderedConsumers = java.util.List.copyOf(orderedConsumers)
    require(orderedConsumers.isNotEmpty()) { "A library layout recipe requires bound consumers" }
  }
}

/** Captures one original consumer, including its position, complete recipe, and artifact ownership. */
@ApiStatus.Internal
@Serializable
class DevPluginLibraryLayoutConsumer(
  @JvmField val index: Int,
  @SerialName("asset") private var capturedAsset: PluginPackingAsset,
  @SerialName("artifact") private var capturedArtifact: ReusableJarArtifact?,
) {
  val asset: PluginPackingAsset get() = capturedAsset
  val artifact: ReusableJarArtifact? get() = capturedArtifact

  init {
    require(index >= 0) { "A library layout consumer requires its original position" }
    capturedAsset = capturedAsset.copy(
      inputs = java.util.List.copyOf(capturedAsset.inputs), recipe = capturedAsset.recipe?.let(::freezeLibraryLayoutConsumerRecipe),
    )
    capturedArtifact = capturedArtifact?.let { it.copy(recipe = freezeLibraryLayoutConsumerRecipe(it.recipe)) }
  }
}

private fun freezeLibraryLayoutConsumerRecipe(recipe: CanonicalJarRecipe): CanonicalJarRecipe {
  return recipe.copy(sources = java.util.List.copyOf(recipe.sources.map { source ->
    source.copy(
      expansion = java.util.List.copyOf(source.expansion), options = java.util.List.copyOf(source.options),
      preparedManifest = source.preparedManifest?.let { it.copy(sourceManifestPolicies = java.util.List.copyOf(it.sourceManifestPolicies)) },
    )
  }))
}

@ApiStatus.Internal
@Serializable
class DevPluginLibraryLayoutModule(
  @JvmField val name: String,
  @JvmField val relativeOutputFile: String,
  @SerialName("moduleSet") private var orderedModuleSet: List<String>?,
) {
  val moduleSet: List<String>? get() = orderedModuleSet

  init {
    orderedModuleSet = orderedModuleSet?.let { java.util.List.copyOf(it) }
  }
}

@ApiStatus.Internal
@Serializable
class DevPluginLibraryLayoutExclusion(
  @JvmField val moduleName: String,
  @SerialName("patterns") private var orderedPatterns: List<String>,
) {
  val patterns: List<String> get() = orderedPatterns

  init {
    orderedPatterns = java.util.List.copyOf(orderedPatterns)
  }
}

@ApiStatus.Internal
@Serializable
class DevPluginLibraryLayoutRecipeFilter(
  @JvmField val moduleName: String,
  @JvmField val manifest: String,
  @SerialName("inputs") private var orderedInputs: List<DevPluginReference>,
  @SerialName("outputs") private var orderedOutputs: List<String>,
) {
  val inputs: List<DevPluginReference> get() = orderedInputs
  val outputs: List<String> get() = orderedOutputs

  init {
    orderedInputs = java.util.List.copyOf(orderedInputs)
    orderedOutputs = java.util.List.copyOf(orderedOutputs)
  }
}

/** Reconstructs the original library callback. A null source input retains an explicitly absent lookup. */
@ApiStatus.Internal
@Serializable
class DevPluginLibraryLayoutCallback(
  @JvmField val kind: String = "library-entries-v1",
  @JvmField val layoutIndex: Int,
  @JvmField val libraryName: String,
  @JvmField val libraryModuleName: String,
  @JvmField val prefix: String,
  @JvmField val targetModuleName: String,
  @JvmField val cwmFrontendOptions: DevPluginCwmFrontendCallbackOptions? = null,
  @SerialName("libraries") private var orderedLibraries: List<DevPluginLibraryLayoutRecipeLibrary>,
  @SerialName("sources") private var orderedSources: List<DevPluginLibraryLayoutRecipeSource>,
) {
  val libraries: List<DevPluginLibraryLayoutRecipeLibrary> get() = orderedLibraries
  val sources: List<DevPluginLibraryLayoutRecipeSource> get() = orderedSources

  init {
    orderedLibraries = java.util.List.copyOf(orderedLibraries)
    orderedSources = java.util.List.copyOf(orderedSources)
  }
}

@ApiStatus.Internal
@Serializable
class DevPluginLibraryLayoutRecipeLibrary(
  @JvmField val name: String,
  @JvmField val moduleName: String?,
  @SerialName("roots") private var orderedRoots: List<DevPluginReference>,
) {
  val roots: List<DevPluginReference> get() = orderedRoots

  init {
    orderedRoots = java.util.List.copyOf(orderedRoots)
  }
}

@ApiStatus.Internal
@Serializable
data class DevPluginLibraryLayoutRecipeSource(
  @JvmField val moduleName: String,
  @JvmField val path: String,
  @JvmField val input: DevPluginReference?,
)

/** Values captured when the generator creates the CWM frontend callback recipe. */
@ApiStatus.Internal
@Serializable
data class DevPluginCwmFrontendCallbackOptions(
  @JvmField val isEapOverride: String?,
  @JvmField val versionSuffixOverride: String?,
  @JvmField val nightlyBuild: Boolean,
  @JvmField val branchName: String?,
)

/** A null entry list retains dynamic patches. An empty list requires an empty output. */
@ApiStatus.Internal
@Serializable
class DevPluginLibraryLayoutRecipeOutput(
  @JvmField val moduleName: String,
  @JvmField val output: String,
  @SerialName("entries") private var orderedEntries: List<String>?,
) {
  val entries: List<String>? get() = orderedEntries

  init {
    orderedEntries = orderedEntries?.let { java.util.List.copyOf(it) }
  }
}

@ApiStatus.Internal
@Serializable
data class DevPluginLibraryLayoutRecipeSeed(
  @JvmField val moduleName: String,
  @JvmField val entry: String,
  @JvmField val input: DevPluginReference,
  @JvmField val size: Int,
  @JvmField val hash: Long,
)

/** The signature comes from the original adapter, including the ordered inputs for all operations of this owner. */
@ApiStatus.Internal
@Serializable
class DevPluginLibraryLayoutRecipeOperation(
  @JvmField val id: String,
  @JvmField val kind: String,
  @JvmField val modelSignature: String,
  @SerialName("inputs") private var orderedInputs: List<String>,
  @SerialName("outputs") private var orderedOutputs: List<String>,
) {
  val inputs: List<String> get() = orderedInputs
  val outputs: List<String> get() = orderedOutputs

  init {
    orderedInputs = java.util.List.copyOf(orderedInputs)
    orderedOutputs = java.util.List.copyOf(orderedOutputs)
  }
}

@ApiStatus.Internal
interface DevPluginLibraryLayoutRuntime {
  fun compile(
    configuration: DevPluginLibraryLayoutRecipeConfiguration,
    plan: PluginPackingPlan,
    catalogue: DevPluginArtifactCatalogue,
  ): Map<String, DevPluginPreparationAction>
}
