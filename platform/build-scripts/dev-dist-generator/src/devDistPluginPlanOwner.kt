@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginPreparationRecipe
import org.jetbrains.intellij.build.dev.snapshotDevPluginPreparationOperation
import org.jetbrains.intellij.build.dev.snapshotDevPluginPreparationRecipe
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingProjection
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PluginLayout
import java.util.Collections

/** The identity of a completed graph within one section owner. */
@ApiStatus.Internal
data class DevDistPluginPlanKey(
  @JvmField val product: String,
  @JvmField val plugin: String,
  @JvmField val variant: String,
)

/** Derived execution data. This record does not select a backend or declare plugin eligibility. */
internal class DevDistPluginPlanRecord(
  @JvmField val variant: PluginSymbolicVariant,
  @JvmField val plan: DevDistPluginBuildPlan,
  preparationRecipe: DevPluginPreparationRecipe? = null,
  private val originalLayout: DevDistPluginOriginalLayout? = null,
) {
  private val retainedRecipe = preparationRecipe?.let(::snapshotDevPluginPreparationRecipe)

  /** The operations as the plan file holds them. Two recipes compare equal through this text. */
  private val retainedRecipeText = retainedRecipe?.let { PLAN_JSON.encodeToString(DevPluginPreparationRecipe.serializer(), it) }

  /** The ordered raw inputs the remainder action reads. Empty for an inert record without a recipe. */
  @JvmField val remainderInputs: List<String> = retainedRecipe?.let { deriveRemainderInputs(plan, it) }.orEmpty()

  /** Each read owns its byte arrays. The shared helper also freezes every nested collection. */
  val preparationRecipe: DevPluginPreparationRecipe?
    get() = retainedRecipe?.let(::snapshotDevPluginPreparationRecipe)

  /**
   * The projection as the plan file holds it: [DevDistPluginBuildPlan.projection] with the operations in the order of
   * the recipe. The record checks that the recipe states the operations of the plan.
   */
  val fileProjection: PluginPackingProjection
    get() = plan.projection.copy(operations = preparationRecipe?.operations.orEmpty())

  /**
   * This record for [variant]: the same graph, with the plan stamped for [variant].
   * The projection reads the variant id only to stamp it, so a plan that no platform input changes needs no second run.
   */
  fun relabel(variant: PluginSymbolicVariant): DevDistPluginPlanRecord {
    val source = plan
    val relabeled = object : DevDistPluginBuildPlan {
      override val projection = source.projection.copy(variant = variant.id)
      override val catalogue = source.catalogue
      override val requiredRawInputs = source.requiredRawInputs
      override val requiredLibraries = source.requiredLibraries
      override val reusableArtifacts = source.reusableArtifacts
    }
    return DevDistPluginPlanRecord(variant, relabeled, retainedRecipe, originalLayout)
  }

  /** Checks retained generation-time facts, not live Kotlin source or callback implementation state. */
  fun requireOriginalLayout(layout: PluginLayout): String {
    return requireNotNull(originalLayout) { "The execution record has no original layout binding" }.requireUnchanged(layout)
  }

  /** Repeated registration preserves the first identity and requires unchanged, equivalent declared facts. */
  fun requireSameOriginalLayoutState(other: DevDistPluginPlanRecord) {
    requireNotNull(originalLayout).requireEquivalent(requireNotNull(other.originalLayout))
  }

  /** Direct copies remain graph fixtures. Only registration supplies an original layout binding. */
  fun copy(
    variant: PluginSymbolicVariant = this.variant,
    plan: DevDistPluginBuildPlan = this.plan,
    preparationRecipe: DevPluginPreparationRecipe? = this.preparationRecipe,
  ): DevDistPluginPlanRecord = DevDistPluginPlanRecord(variant, plan, preparationRecipe)

  /**
   * The graph without the platform: the variant is masked.
   * Two records with equal neutral graphs describe the same plugin content on different platforms.
   */
  fun neutralGraph(): List<Any?> {
    val projection = plan.projection
    return listOf(
      projection.copy(variant = ""),
      plan.catalogue,
      plan.requiredRawInputs,
      plan.requiredLibraries,
      plan.reusableArtifacts,
      retainedRecipeText,
      remainderInputs,
    )
  }

  fun hasSameGraph(other: DevDistPluginPlanRecord): Boolean {
    return variant == other.variant &&
           plan.projection == other.plan.projection &&
           plan.catalogue == other.plan.catalogue &&
           plan.requiredRawInputs == other.plan.requiredRawInputs &&
           plan.requiredLibraries == other.plan.requiredLibraries &&
           plan.reusableArtifacts == other.plan.reusableArtifacts &&
           retainedRecipeText == other.retainedRecipeText &&
           remainderInputs == other.remainderInputs
  }
}

/** Captures declared layout state before owner derivation. It never invokes a layout callback. */
internal class DevDistPluginOriginalLayout(private val layout: PluginLayout) {
  private val directoryName = layout.directoryName
  private val state = originalLayoutState(layout)

  fun requireUnchanged(candidate: PluginLayout): String {
    require(candidate === layout && originalLayoutState(candidate) == state) { "The original layout changed or belongs to another request" }
    return directoryName
  }

  fun requireEquivalent(other: DevDistPluginOriginalLayout) {
    requireUnchanged(layout)
    other.requireUnchanged(other.layout)
    require(state == other.state) { "The original layout facts differ from the repeated registration" }
  }
}

private fun originalLayoutState(layout: PluginLayout): List<Any?> {
  val restrictions = layout.bundlingRestrictions
  return listOf(
    layout.mainModule, layout.auto, layout.directoryName, layout.directoryNameSetExplicitly, layout.getMainJarName(),
    layout.includedModules.map(::originalModuleState), layout.moduleExcludes.mapValues { it.value.toList() },
    layout.getIncludedProjectLibraries().map { listOf(it.libraryName, it.packMode, it.outPath, it.owner?.let(::originalModuleState)) },
    layout.getIncludedModuleLibraries().toList(), layout.getExcludedModuleLibraries().mapValues { it.value.toList() },
    layout.getModulesWithExcludedModuleLibraries().toSet(),
    layout.patchers.toList(), layout.resourcePaths.toList(), layout.resourceGenerators.toList(),
    layout.customAssets.map { listOf(it, it.relativePath, it.platformSpecific) },
    layout.platformResourceGenerators.mapValues { it.value.toList() },
    layout.executablePatterns.mapValues { it.value.toList() }, layout.getDeprecatedPostScrambleProcessor().toList(),
    restrictions.supportedOs.toList(), restrictions.supportedArch.toList(), restrictions.includeInDistribution,
    restrictions === PluginBundlingRestrictions.MARKETPLACE,
    layout.semanticVersioning, layout.versionEvaluator, layout.versionSuffix, layout.rawPluginXmlPatcher, layout.pluginXmlPatcher,
    layout.descriptorMarkers?.map { it.literal to it.replacement }, layout.pluginCompatibilityExactVersion,
    layout.retainProductDescriptorForBundledPlugin, layout.enableSymlinksAndExecutableResources, layout.pathsToScramble.toList(),
    layout.scrambleSkipStatements.toList(), layout.scrambleClasspathPlugins.toList(), layout.scrambleClasspathFilter,
    layout.zkmScriptStub, layout.coScrambleZkmScriptInclude, layout.scrambleWithPlatform,
  )
}

/**
 * The layout facts a plugin plan reads, comparable across products.
 *
 * Each product builds its own layout objects, so the callbacks of two equal layouts are different objects. The plan
 * never invokes a callback. It reads the class of a callback and the asset spec the callback declares, so the key keeps
 * those two and drops the identity. Every other fact is the same one [DevDistPluginOriginalLayout] retains.
 */
internal fun planUnitLayoutKey(layout: PluginLayout): Any? = canonicalLayoutValue(originalLayoutState(layout))

/** A layout callback as a plan sees it: its class and the asset spec it declares, if any. */
private data class LayoutCallbackKey(@JvmField val type: Class<*>, @JvmField val assetSpec: Any?)

private fun canonicalLayoutValue(value: Any?): Any? {
  return when (value) {
    null, is String, is Number, is Boolean, is Enum<*> -> value
    is List<*> -> value.map(::canonicalLayoutValue)
    is Set<*> -> value.mapTo(LinkedHashSet(), ::canonicalLayoutValue)
    is Map<*, *> -> value.entries.associate { canonicalLayoutValue(it.key) to canonicalLayoutValue(it.value) }
    is Pair<*, *> -> canonicalLayoutValue(value.first) to canonicalLayoutValue(value.second)
    else -> if (HAS_VALUE_EQUALITY.get(value.javaClass)) {
      value
    }
    else {
      LayoutCallbackKey(value.javaClass, (value as? DevPluginLayoutAssetOwner)?.devPluginLayoutAssetSpec)
    }
  }
}

/** Whether a class states its own `equals`, so two of its values compare by content and not by identity. */
private val HAS_VALUE_EQUALITY = object : ClassValue<Boolean>() {
  override fun computeValue(type: Class<*>): Boolean = type.getMethod("equals", Any::class.java).declaringClass != Any::class.java
}

private fun originalModuleState(module: ModuleItem): List<Any?> {
  return listOf(module.moduleName, module.relativeOutputFile, module.isProductModule(), module.reason == ModuleIncludeReasons.PRODUCT_MODULES,
                module.moduleSet?.toList())
}

/**
 * The ordered inputs of the remainder action. The derivation also checks every operation of [recipe] against the
 * operations of the selected plan, so a mismatch fails the generator and not a Bazel action.
 */
private fun deriveRemainderInputs(plan: DevDistPluginBuildPlan, recipe: DevPluginPreparationRecipe): List<String> {
  val selected = plan.selectedPlan()
  val required = plan.requiredRawInputs.mapTo(HashSet()) { it.id }
  val inputs = DevDistPluginInputKinds(
    artifacts = plan.catalogue.artifacts.filter { it.id in required }.associate { it.id to it.kind },
    libraries = plan.requiredLibraries.toSet(),
  )
  validateDevDistPluginOperations(selected, inputs, recipe.operations)
  return java.util.List.copyOf(deriveDevDistPluginRemainderInputs(selected, inputs, recipe.operations))
}

/** Retains the owner's immutable canonical artifacts and snapshots the caller's graph data. */
internal fun snapshotDevDistPluginPlan(plan: DevDistPluginBuildPlan): DevDistPluginBuildPlan {
  return object : DevDistPluginBuildPlan {
    override val projection = plan.projection.copy(
      assets = java.util.List.copyOf(plan.projection.assets.map { asset ->
        asset.copy(inputs = java.util.List.copyOf(asset.inputs), recipe = asset.recipe?.let(::snapshotDevDistJarRecipe))
      }),
      preparationRoots = java.util.List.copyOf(plan.projection.preparationRoots),
      operations = java.util.List.copyOf(plan.projection.operations.map(::snapshotDevPluginPreparationOperation)),
    )
    override val catalogue = plan.catalogue.copy(
      artifacts = java.util.List.copyOf(plan.catalogue.artifacts),
      moduleRoots = Collections.unmodifiableMap(plan.catalogue.moduleRoots.mapValues { java.util.List.copyOf(it.value) }),
      libraries = java.util.List.copyOf(plan.catalogue.libraries.map { it.copy(files = java.util.List.copyOf(it.files)) }),
      testModules = java.util.Set.copyOf(plan.catalogue.testModules),
    )
    override val requiredRawInputs = java.util.List.copyOf(plan.requiredRawInputs)
    override val requiredLibraries = java.util.List.copyOf(plan.requiredLibraries)
    override val reusableArtifacts = java.util.List.copyOf(plan.reusableArtifacts.map { it.copy(recipe = snapshotDevDistJarRecipe(it.recipe)) })
  }
}

internal fun snapshotDevDistJarRecipe(recipe: CanonicalJarRecipe): CanonicalJarRecipe {
  return recipe.copy(sources = java.util.List.copyOf(recipe.sources.map { source ->
    source.copy(
      options = java.util.List.copyOf(source.options),
      preparedManifest = source.preparedManifest?.let { it.copy(sourceManifestPolicies = java.util.List.copyOf(it.sourceManifestPolicies)) },
    )
  }))
}

internal fun snapshotDevDistBazelIndex(index: DevDistBazelIndex): DevDistBazelIndex {
  val targets = index.targets
  return DevDistBazelIndex(
    targets = targets.copy(
      modules = Collections.unmodifiableMap(targets.modules.mapValues { (_, module) ->
        module.copy(
          productionTargets = java.util.List.copyOf(module.productionTargets),
          productionJars = java.util.List.copyOf(module.productionJars),
          testTargets = java.util.List.copyOf(module.testTargets),
          testJars = java.util.List.copyOf(module.testJars),
          exports = java.util.List.copyOf(module.exports),
          moduleLibraries = Collections.unmodifiableMap(module.moduleLibraries.mapValues { snapshotLibrary(it.value) }),
        )
      }),
      imlTargets = java.util.List.copyOf(targets.imlTargets),
      projectLibraries = Collections.unmodifiableMap(targets.projectLibraries.mapValues { snapshotLibrary(it.value) }),
      pluginDistributionTargets = Collections.unmodifiableMap(LinkedHashMap(targets.pluginDistributionTargets)),
    ),
    projectRoot = index.projectRoot,
    communityRoot = index.communityRoot,
    planPackageIsCommunity = index.planPackageIsCommunity,
  )
}

private fun snapshotLibrary(library: BazelTargetsInfo.LibraryDescription): BazelTargetsInfo.LibraryDescription {
  return library.copy(
    jars = java.util.List.copyOf(library.jars),
    jarTargets = java.util.List.copyOf(library.jarTargets),
    sourceJars = java.util.List.copyOf(library.sourceJars),
  )
}
