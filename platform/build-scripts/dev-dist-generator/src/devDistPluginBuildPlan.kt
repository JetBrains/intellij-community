@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicArtifact
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicArtifactCatalogue
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicDescriptorFacts
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicLayout
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicLayoutGap
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicLibrary
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicNativePolicy
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparationFacts
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicProjectionCache
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import com.intellij.platform.buildScripts.pluginModelTool.projectPluginSymbolicLayout
import org.jetbrains.intellij.build.dev.DevPluginResourceExclusions
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.PluginPackingProjection
import org.jetbrains.intellij.build.devDist.ReusableJarArtifact
import org.jetbrains.intellij.build.devDist.isNativeTreeAsset
import org.jetbrains.intellij.build.devDist.planPluginPacking
import org.jetbrains.intellij.build.getLibraryFileName
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.intellij.build.productLayout.util.isProductionRuntimeDependency
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.util.ArrayDeque

/** Single-root module IDs are JPS names. Multi-root module IDs append `:<target-label>`. Library IDs are recorded jar target labels. */
data class DevDistPluginRawInput(
  @JvmField val id: String,
  @JvmField val label: String,
  @JvmField val kind: String,
  @JvmField val fileName: String,
  /** Repository-relative prefix used to normalize a declared multi-file source target. */
  @JvmField val sourceTreePrefix: String? = null,
  /** A source tree may have no files and then materializes as an empty directory. */
  @JvmField val optionalSourceTree: Boolean = false,
  /** What a filtered checkout directory leaves out. Its label names the filtered filegroup of its package. */
  @JvmField val sourceTreeExclusions: DevPluginResourceExclusions = DevPluginResourceExclusions.NONE,
)

/** Explicit file metadata for a label whose output name or root kind the generated index cannot prove. */
data class DevDistPluginFileFacts(
  @JvmField val kind: String,
  @JvmField val fileName: String,
)

/** One JPS library a layout callback reads as a whole: a project library, or the module library of [moduleName]. */
data class DevDistPluginLibraryInput(
  @JvmField val libraryName: String,
  @JvmField val moduleName: String? = null,
)

/**
 * Facts the Bazel index cannot supply. [preparationKeys] binds generic source transformations, not native handling.
 * [additionalInputs] binds raw descriptor and callback inputs. A preparation output does not need a raw binding.
 * Every additional input must occur in the final plan's raw inputs, including required preparation inputs.
 * [additionalLibraries] names the libraries a callback reads by their container. Every one must occur in the final
 * plan's required libraries. [fileFacts] is keyed by the raw input ID. [testModules] names modules whose selected
 * roots are test outputs only.
 */
data class DevDistPluginCatalogueFacts(
  @JvmField val preparationKeys: Map<String, String> = emptyMap(),
  @JvmField val additionalInputs: List<DevDistPluginRawInput> = emptyList(),
  @JvmField val additionalLibraries: List<DevDistPluginLibraryInput> = emptyList(),
  @JvmField val fileFacts: Map<String, DevDistPluginFileFacts> = emptyMap(),
  @JvmField val testModules: Set<String> = emptySet(),
)

/**
 * The single owner's result. [requiredRawInputs] are the files and directories the plan names directly.
 * [requiredLibraries] are the container labels the plan names; the catalogue rule expands each to its member jars.
 * [reusableArtifacts] are the `content_module_jar` outputs the assets match, in asset order. The plan file states none
 * of them; the chain names their modules to the packer.
 */
internal interface DevDistPluginBuildPlan {
  val projection: PluginPackingProjection
  val catalogue: PluginSymbolicArtifactCatalogue
  val requiredRawInputs: List<DevDistPluginRawInput>
  val requiredLibraries: List<String>
  val reusableArtifacts: List<ReusableJarArtifact>
  val layoutSignature: String

  /** The reused modules, the keys the chain and the asset rows use. */
  val reusedModules: List<String>
    get() = reusableArtifacts.map { it.module }
}

/** The plan with its reuse decision. Every reader of the selected assets and required inputs goes through here. */
internal fun DevDistPluginBuildPlan.selectedPlan(): PluginPackingPlan = projection.plan(reusableArtifacts)

/**
 * Computes a plan from one original layout without activating or writing generator declarations.
 * Production code calls this kernel only through [DevDistBuildSections.registerPluginPlan]. Low-level tests may call it directly.
 * [canonicalOwnerIndex] contains exact recipes from the section owner's rendering run, not artifacts assembled by callers.
 * [nativePolicy] must come from the selected product.
 * The initial projection discovers dependencies without native replacement. Only the validated policy-aware projection can select reuse.
 * Dependency discovery does not validate source identities. The policy-aware projection supplies all source validation gaps.
 * No candidate is selected before the complete symbolic projection and all required catalogue facts pass validation.
 */
internal fun computeDevDistPluginBuildPlan(
  layout: PluginLayout,
  project: JpsProject,
  index: DevDistBazelIndex,
  descriptorFacts: PluginSymbolicDescriptorFacts,
  preparationFacts: PluginSymbolicPreparationFacts,
  variant: PluginSymbolicVariant,
  catalogueFacts: DevDistPluginCatalogueFacts,
  nativePolicy: PluginSymbolicNativePolicy?,
  canonicalOwnerIndex: DevDistCanonicalOwners,
  projectInputs: DevDistProjectInputs = DevDistProjectInputs(project, index),
): DevDistPluginBuildPlan {
  val selectedNativePolicy = requireNotNull(nativePolicy) { "Plugin '${layout.mainModule}' requires the selected product's native policy" }
  val builder = PluginBuildCatalogueBuilder(layout, project, index, descriptorFacts, variant, catalogueFacts, projectInputs)
  val initialCatalogue = builder.build()
  val originalDiscovery = projectPluginSymbolicLayout(
    layout = layout,
    project = project,
    catalogue = initialCatalogue,
    descriptorFacts = descriptorFacts,
    preparationFacts = preparationFacts,
    variant = variant,
    cache = projectInputs.projectionCache,
  )
  builder.validatePreparationNamespaces(originalDiscovery)
  val originalDiscoveryPlan = planWithoutReuse(originalDiscovery)
  val originalPreparationIds = originalDiscoveryPlan.preparations.mapTo(HashSet()) { it.id }
  val discoveredModules = builder.moduleCount
  val originalCatalogue = builder.includeRequiredModules(originalDiscoveryPlan.requiredInputs)
  val originalDependencies = preparationFacts.dependencies.filter { it.id in originalPreparationIds }
  // The original sources must plan without reuse, as the discovery did. With no new module and no dropped dependency,
  // the sources are the discovery itself, which planned already.
  if (builder.moduleCount != discoveredModules || originalDependencies.size != preparationFacts.dependencies.size) {
    val originalSources = projectPluginSymbolicLayout(
      layout = layout,
      project = project,
      catalogue = originalCatalogue,
      descriptorFacts = descriptorFacts,
      preparationFacts = preparationFacts.copy(dependencies = originalDependencies),
      variant = variant,
      cache = projectInputs.projectionCache,
    )
    builder.validatePreparationNamespaces(originalSources)
    planWithoutReuse(originalSources)
  }

  val selectedPreparationFacts = preparationFacts
  val discovery = projectPluginSymbolicLayout(
    layout = layout,
    project = project,
    catalogue = originalCatalogue,
    descriptorFacts = descriptorFacts,
    preparationFacts = preparationFacts,
    variant = variant,
    nativePolicy = selectedNativePolicy,
    cache = projectInputs.projectionCache,
  )
  builder.validatePreparationNamespaces(discovery)
  val discoveryPlan = planWithoutReuse(discovery)
  val requiredPreparationIds = discoveryPlan.preparations.mapTo(HashSet()) { it.id }
  val selectedPreparations = selectedPreparationFacts.copy(
    dependencies = selectedPreparationFacts.dependencies.filter { it.id in requiredPreparationIds },
  )
  val selectedModules = builder.moduleCount
  val catalogue = builder.includeRequiredModules(discoveryPlan.requiredInputs)
  // With no new module and no dropped dependency, the final projection reads what the discovery read, so it is the
  // discovery.
  val symbolic = if (builder.moduleCount == selectedModules &&
                     selectedPreparations.dependencies.size == selectedPreparationFacts.dependencies.size) {
    discovery
  }
  else {
    projectPluginSymbolicLayout(
      layout = layout,
      project = project,
      catalogue = catalogue,
      descriptorFacts = descriptorFacts,
      preparationFacts = selectedPreparations,
      variant = variant,
      nativePolicy = selectedNativePolicy,
      cache = projectInputs.projectionCache,
    ).also(builder::validatePreparationNamespaces)
  }
  val gaps = (builder.gaps + symbolic.gaps).distinct()
  check(gaps.isEmpty()) {
    "Plugin '${layout.mainModule}', variant '${variant.id}', lacks build plan facts:\n" +
    gaps.joinToString("\n") { "${it.key}: ${it.detail}" }
  }
  val symbolicProjection = symbolic.projection(canonicalOwnerIndex.candidates(symbolic))
  val projection = symbolicProjection.projection
  val plan = projection.plan(symbolicProjection.reusableArtifacts)
  // A natives jar and its tree have one producer: the reused `content_module_jar` in natives mode.
  val unownedNatives = plan.assets.filter {
    it.artifact == null && (isNativeTreeAsset(it.asset) || it.asset.recipe?.writer?.nativeLib?.isNotEmpty() == true)
  }
  check(unownedNatives.isEmpty()) {
    "Plugin '${layout.mainModule}', variant '${variant.id}', has presigned natives that no content_module_jar packs: " +
    unownedNatives.joinToString { it.asset.destination }
  }
  val (requiredRawInputs, requiredLibraries) = builder.selectInputs(plan.requiredInputs, catalogue)
  val consumedInputs = requiredRawInputs.mapTo(HashSet()) { it.id }
  val unusedInputs = catalogueFacts.additionalInputs.map { it.id }.distinct().filterNot { it in consumedInputs }
  check(unusedInputs.isEmpty()) { "Plugin '${layout.mainModule}' has unused additional input bindings: $unusedInputs" }
  val unusedLibraries = catalogueFacts.additionalLibraries.distinct().filterNot { builder.libraryId(it) in requiredLibraries }
  check(unusedLibraries.isEmpty()) { "Plugin '${layout.mainModule}' has unused additional library bindings: $unusedLibraries" }
  return PluginBuildPlan(
    projection = projection,
    catalogue = catalogue,
    requiredRawInputs = requiredRawInputs,
    requiredLibraries = requiredLibraries,
    reusableArtifacts = symbolicProjection.reusableArtifacts,
    layoutSignature = plan.layoutSignature,
  )
}

private fun planWithoutReuse(symbolic: PluginSymbolicLayout): PluginPackingPlan {
  return planPluginPacking(
    plugin = symbolic.plugin,
    variant = symbolic.variant,
    assets = symbolic.assets,
    preparations = symbolic.preparations,
    preparationRoots = symbolic.preparationRoots,
    artifacts = emptyList(),
  )
}

/**
 * The `content_module_jar` outputs of the section owner's run, checked once, and keyed by recipe and mode.
 *
 * A run computes thousands of plans, and each plan reuses a few of these jars. [candidates] gives a plan only the jars
 * an asset can match. For each recipe and mode that is the first owner in [owners] order, the one [planPluginPacking]
 * picks from the whole list, so the reuse decision stays the same.
 */
internal class DevDistCanonicalOwners(owners: Map<String, ReusableJarArtifact>, index: DevDistBazelIndex) {
  private val byRecipe = HashMap<Pair<CanonicalJarRecipe, Int>, ReusableJarArtifact>()

  init {
    for ((owner, artifact) in owners) {
      checkNotNull(index.contentModuleJarLabel(owner, dependentIsCommunity = index.planPackageIsCommunity)) {
        "Canonical owner '$owner' has no content-module artifact label"
      }
      check(artifact.module == owner) { "Canonical owner '$owner' must name its own module, not '${artifact.module}'" }
      require(artifact.mode in 1..511) { "Artifact '${artifact.module}' has an unsupported mode" }
      byRecipe.putIfAbsent(artifact.recipe to artifact.mode, artifact)
    }
  }

  /** The jars whose recipe and mode an asset of [layout] states, in asset order. */
  fun candidates(layout: PluginSymbolicLayout): List<ReusableJarArtifact> {
    val result = LinkedHashSet<ReusableJarArtifact>()
    for (asset in layout.assets) {
      val recipe = asset.recipe ?: continue
      byRecipe.get(recipe to asset.mode)?.let(result::add)
    }
    return result.toList()
  }
}

private class PluginBuildPlan(
  override val projection: PluginPackingProjection,
  override val catalogue: PluginSymbolicArtifactCatalogue,
  override val requiredRawInputs: List<DevDistPluginRawInput>,
  override val requiredLibraries: List<String>,
  override val reusableArtifacts: List<ReusableJarArtifact>,
  override val layoutSignature: String,
) : DevDistPluginBuildPlan

/**
 * The raw model ids of the whole project, computed once per run.
 *
 * [PluginBuildCatalogueBuilder] asks whether an id names a module output or a library file of the project, and which
 * module a required input names. Both answers depend on the project and the index alone, and a run computes thousands
 * of plans over one project, so this class holds them once. A plan adds its own inputs and the test outputs of its
 * test modules, see [DevDistPluginCatalogueFacts.testModules].
 */
internal class DevDistProjectInputs(project: JpsProject, private val index: DevDistBazelIndex) {
  /** The answers every projection over the project shares. */
  @JvmField val projectionCache: PluginSymbolicProjectionCache = PluginSymbolicProjectionCache(project)

  /** The production ids of every module, and the container and jar targets of every module and project library. */
  private val productionIds = HashSet<String>()

  /** The module that owns a production id of a module output. A library id has no owner. */
  private val moduleByProductionId = HashMap<String, String>()

  /** The position of a module in the project, so a caller can keep the project order. */
  private val moduleOrder = HashMap<String, Int>()

  init {
    for ((position, module) in project.modules.withIndex()) {
      val name = module.name
      moduleOrder.put(name, position)
      val description = index.targets.modules.get(name) ?: continue
      for (target in description.productionTargets) {
        val id = moduleInputId(name, target, description.productionTargets.size)
        productionIds.add(id)
        moduleByProductionId.put(id, name)
      }
      for (library in module.libraryCollection.libraries) {
        val record = description.moduleLibraries.get(library.name) ?: continue
        productionIds.add(record.target)
        productionIds.addAll(record.jarTargets)
      }
    }
    for (library in project.libraryCollection.libraries) {
      val record = index.targets.projectLibraries.get(library.name) ?: continue
      productionIds.add(record.target)
      productionIds.addAll(record.jarTargets)
    }
  }

  /** The test output ids of [module]. */
  fun testIds(module: String): List<String> {
    val targets = index.targets.modules.get(module)?.testTargets.orEmpty()
    return targets.map { moduleInputId(module, it, targets.size) }
  }

  /**
   * Whether [id] names a module output or a library file of the project, as [PluginBuildCatalogueBuilder] selects
   * them: the test outputs of a module in [testModules], and the production outputs of every other module.
   */
  fun isProjectId(id: String, testModules: Set<String>): Boolean {
    for (testModule in testModules) {
      if (id in testIds(testModule)) return true
    }
    if (id !in productionIds) return false
    val owner = moduleByProductionId.get(id)
    return owner == null || owner !in testModules
  }

  /**
   * The modules a plan requires, in the project order: a module named by a required input, or one whose selected
   * output is a required input. A module in [testModules] is selected by its test outputs.
   */
  fun requiredModules(requiredInputs: Set<String>, testModules: Set<String>): List<String> {
    val result = HashSet<String>()
    for (input in requiredInputs) {
      if (moduleOrder.containsKey(input)) result.add(input)
      moduleByProductionId.get(input)?.let { if (it !in testModules) result.add(it) }
    }
    for (testModule in testModules) {
      if (testModule in moduleOrder && testIds(testModule).any { it in requiredInputs }) result.add(testModule)
    }
    return result.sortedBy { moduleOrder.getValue(it) }
  }
}

private class PluginBuildCatalogueBuilder(
  private val layout: PluginLayout,
  private val project: JpsProject,
  private val index: DevDistBazelIndex,
  private val descriptors: PluginSymbolicDescriptorFacts,
  private val variant: PluginSymbolicVariant,
  private val facts: DevDistPluginCatalogueFacts,
  private val projectInputs: DevDistProjectInputs,
) {
  private val projectLibraries = project.libraryCollection
  private val inputs = LinkedHashMap<String, DevDistPluginRawInput>()
  private val artifacts = LinkedHashMap<String, PluginSymbolicArtifact>()
  private val moduleRoots = LinkedHashMap<String, List<String>>()

  /** The number of modules the catalogue holds. It grows only through [includeRequiredModules]. */
  val moduleCount: Int
    get() = moduleRoots.size
  private val libraries = LinkedHashMap<Pair<String?, String>, PluginSymbolicLibrary>()
  private val libraryIdentities = HashMap<Pair<String?, String>, String>()
  val gaps = ArrayList<PluginSymbolicLayoutGap>()

  fun build(): PluginSymbolicArtifactCatalogue {
    for (input in facts.additionalInputs) addInput(input)
    val members = LinkedHashSet<String>()
    members.add(layout.mainModule)
    layout.includedModules.mapTo(members) { it.moduleName }
    descriptors.pluginXml?.let { xml ->
      for (content in JDOMUtil.load(xml).getChildren("content")) {
        for (element in content.getChildren("module")) {
          val name = element.getAttributeValue("name") ?: continue
          if ('/' !in name) members.add(name)
        }
      }
    }
    if (layout.auto) {
      val prefix = "${layout.mainModule.removeSuffix(".plugin")}."
      for (dependency in project.findModuleByName(layout.mainModule)?.getProductionModuleDependencies(withTests = false).orEmpty()) {
        val name = dependency.moduleReference.moduleName
        if (name.startsWith(prefix) && name !in descriptors.packedElsewhere) members.add(name)
      }
    }
    for (member in members) {
      addModule(member)
      addModuleLibraries(member)
    }
    for (item in layout.getIncludedModuleLibraries()) {
      for (library in project.findModuleByName(item.moduleName)?.libraryCollection?.libraries.orEmpty()) {
        if (getLibraryFileName(library) == item.libraryName) addLibrary(library)
      }
    }
    for (item in layout.getIncludedProjectLibraries()) {
      projectLibraries.findLibrary(item.libraryName)?.let(::addLibrary)
    }
    for (input in facts.additionalLibraries) {
      val library = findLibrary(input)
      if (library == null) gap("additional-library:${input.moduleName}:${input.libraryName}", "The declared callback library is absent from the JPS project")
      else addLibrary(library)
    }
    if (!variant.skipCustomResourceGenerators) {
      for (name in layout.getResourceGeneratorProjectLibraries()) {
        val library = projectLibraries.findLibrary(name)
        if (library == null) gap("generator-library:$name", "The declared generator input is absent from the JPS project")
        else addLibrary(library)
      }
    }
    return catalogue()
  }

  fun includeRequiredModules(requiredInputs: List<String>): PluginSymbolicArtifactCatalogue {
    for (module in projectInputs.requiredModules(requiredInputs = requiredInputs.toHashSet(), testModules = facts.testModules)) {
      if (module !in moduleRoots) addModule(module)
    }
    return catalogue()
  }

  fun validatePreparationNamespaces(symbolic: PluginSymbolicLayout) {
    val producers = HashMap<String, MutableList<PluginPackingPreparation>>()
    for (preparation in symbolic.preparations) {
      for (output in preparation.outputs) producers.computeIfAbsent(output) { ArrayList() }.add(preparation)
    }
    val pending = ArrayDeque(symbolic.assets.flatMap { it.inputs } + symbolic.preparationRoots)
    val visited = HashSet<PluginPackingPreparation>()
    fun visit(preparation: PluginPackingPreparation) {
      if (!visited.add(preparation)) return
      for (output in preparation.outputs) {
        require(!isRawModelId(output)) { "Preparation '${preparation.id}' output '$output' aliases a raw model ID" }
      }
      pending.addAll(preparation.inputs)
    }
    for (preparation in symbolic.preparations) {
      if (preparation.alwaysRun) visit(preparation)
    }
    while (pending.isNotEmpty()) {
      for (preparation in producers.get(pending.removeFirst()).orEmpty()) {
        visit(preparation)
      }
    }
  }

  /**
   * Whether [id] names a raw input of this plan, a selected module output, or a library file of the project. A module
   * root and a library of this plan come from the same index records, so [DevDistProjectInputs] answers for them.
   */
  private fun isRawModelId(id: String): Boolean {
    return inputs.containsKey(id) || projectInputs.isProjectId(id, facts.testModules)
  }

  private fun catalogue(): PluginSymbolicArtifactCatalogue {
    return PluginSymbolicArtifactCatalogue(
      artifacts = artifacts.values.toList(),
      moduleRoots = moduleRoots.toMap(),
      libraries = libraries.values.toList(),
      testModules = facts.testModules.intersect(moduleRoots.keys),
    )
  }

  private fun findLibrary(input: DevDistPluginLibraryInput): JpsLibrary? {
    val module = input.moduleName ?: return projectLibraries.findLibrary(input.libraryName)
    return project.findModuleByName(module)?.libraryCollection?.libraries?.singleOrNull { it.name == input.libraryName }
  }

  /** The container label of a callback library, the id the plan names it by. */
  fun libraryId(input: DevDistPluginLibraryInput): String? {
    return findLibrary(input)?.let { index.libraryLabel(it.name, input.moduleName, dependentIsCommunity = false) }
  }

  private fun addModule(name: String) {
    val description = index.targets.modules.get(name)
    if (description == null) {
      gap("module-output:$name", "The Bazel index has no module output record")
      return
    }
    val targets = if (name in facts.testModules) description.testTargets else description.productionTargets
    if (targets.isEmpty() || targets.distinct().size != targets.size) {
      gap("module-output:$name", "Expected nonempty, distinct selected root targets; recorded targets are $targets")
      return
    }
    val roots = ArrayList<String>(targets.size)
    for (label in targets) {
      val id = moduleInputId(name, label, targets.size)
      roots.add(id)
      val file = fileFacts(id, label) ?: continue
      addInput(DevDistPluginRawInput(id, label, file.kind, file.fileName))
    }
    moduleRoots.put(name, roots)
  }

  private fun addModuleLibraries(name: String) {
    val module = project.findModuleByName(name) ?: return
    if (name in layout.getModulesWithExcludedModuleLibraries()) return
    val excluded = layout.getExcludedModuleLibraries().get(name).orEmpty()
    val service = JpsJavaExtensionService.getInstance()
    for (dependency in module.dependenciesList.dependencies.filterIsInstance<JpsLibraryDependency>()) {
      if (dependency.libraryReference.parentReference !is JpsModuleReference ||
          !isProductionRuntimeDependency(dependency, service, withTests = name in facts.testModules)) continue
      val library = dependency.library ?: continue
      val libraryName = getLibraryFileName(library)
      if (libraryName in excluded || layout.getIncludedModuleLibraries().any { it.libraryName == libraryName && !it.extraCopy }) continue
      addLibrary(library)
    }
  }

  private fun addLibrary(library: JpsLibrary) {
    val owner = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
    val name = if (owner == null) library.name else getLibraryFileName(library)
    val key = owner to name
    val previousIdentity = libraryIdentities.putIfAbsent(key, library.name)
    if (previousIdentity != null && previousIdentity != library.name) {
      gap("library-identity:$owner:$name", "Different JPS libraries '$previousIdentity' and '${library.name}' share the distribution name '$name'")
      return
    }
    if (libraries.containsKey(key)) return
    val description = if (owner == null) index.targets.projectLibraries.get(library.name)
    else index.targets.modules.get(owner)?.moduleLibraries?.get(library.name)
    if (description == null) {
      gap("library-index:$owner:${library.name}", "The Bazel index lacks the exact JPS library identity")
      return
    }
    if (description.jarTargets.isEmpty()) {
      gap("library-files:$owner:${library.name}", "The Bazel index has no ordered jar targets")
      return
    }
    val label = index.libraryLabel(library.name, owner, dependentIsCommunity = false)
    if (label == null || label != description.target) {
      gap("library-label:$owner:${library.name}", "The container label does not match the owner index")
      return
    }
    for (rawLabel in description.jarTargets) {
      val file = fileFacts(rawLabel, rawLabel) ?: continue
      addInput(DevDistPluginRawInput(rawLabel, rawLabel, file.kind, file.fileName))
    }
    libraries.put(key, PluginSymbolicLibrary(name, owner, description.jarTargets.toList(), id = label))
  }

  private fun fileFacts(id: String, label: String): DevDistPluginFileFacts? {
    facts.fileFacts.get(id)?.let { return it }
    if (isRecordedJarFileLabel(label)) {
      return DevDistPluginFileFacts("archive", label.substringAfterLast(':').substringAfterLast('/'))
    }
    gap("file-metadata:$id", "Target '$label' is not a known generated jar-file label; supply its root kind and actual filename")
    return null
  }

  private fun addInput(input: DevDistPluginRawInput) {
    if (input.id.isBlank() || !isAbsoluteFileLabel(input.label) || input.kind !in setOf("archive", "directory", "file") ||
        input.fileName.isBlank() || input.fileName in setOf(".", "..") || input.fileName.any { it == '/' || it == '\\' || it == '\u0000' }) {
      gap("raw-input:${input.id}", "A raw binding requires an ID, an absolute label, a root kind, and one filename")
      return
    }
    if (input.sourceTreePrefix != null && (input.kind != "directory" || !isSafeSourceTreePrefix(input.sourceTreePrefix))) {
      gap("raw-input:${input.id}", "A source tree requires a directory kind and a safe repository-relative prefix")
      return
    }
    val previous = inputs.putIfAbsent(input.id, input)
    if (previous != null && previous != input) {
      gap("raw-input:${input.id}", "Conflicting raw bindings: $previous and $input")
      return
    }
    artifacts.put(input.id, PluginSymbolicArtifact(input.id, input.kind, input.fileName, facts.preparationKeys.get(input.id)))
  }

  /**
   * Splits the required inputs of a plan into the raw file bindings and the library containers, both in plan order.
   * A member jar named directly stays a raw input. A member jar of a required library is not a raw input: the
   * catalogue rule expands the container, so a member listed twice would overlap it.
   */
  fun selectInputs(required: List<String>, catalogue: PluginSymbolicArtifactCatalogue): Pair<List<DevDistPluginRawInput>, List<String>> {
    val byLibrary = catalogue.libraries.filter { it.id != null }.associateBy { requireNotNull(it.id) }
    val rawInputs = LinkedHashMap<String, DevDistPluginRawInput>()
    val libraries = LinkedHashSet<String>()
    for (input in required) {
      if (byLibrary.containsKey(input)) {
        libraries.add(input)
        continue
      }
      val binding = checkNotNull(inputs.get(input)) { "Required raw input '$input' has no Bazel file binding" }
      rawInputs.putIfAbsent(input, binding)
    }
    for (library in libraries) {
      val member = byLibrary.getValue(library).files.firstOrNull { it in rawInputs }
      check(member == null) { "Library '$library' is required as a whole, and its member '$member' is a raw input too" }
    }
    return rawInputs.values.toList() to libraries.toList()
  }

  private fun gap(key: String, detail: String) {
    gaps.add(PluginSymbolicLayoutGap(key, detail))
  }
}

private fun moduleInputId(module: String, target: String, rootCount: Int): String {
  return if (rootCount == 1) module else "$module:$target"
}

private fun isRecordedJarFileLabel(label: String): Boolean {
  if (!isAbsoluteFileLabel(label)) return false
  val repository = label.substringBefore("//")
  return repository in setOf("", "@community", "@lib", "@ultimate_lib") && label.substringAfterLast(':').endsWith(".jar")
}

private fun isAbsoluteFileLabel(label: String): Boolean {
  if (label.any { it.isWhitespace() || it == '\\' || it == '\u0000' }) return false
  val match = Regex("(?:@[A-Za-z0-9._+-]+)?//([^:]*):([^:]+)").matchEntire(label) ?: return false
  val packagePath = match.groupValues.get(1)
  val target = match.groupValues.get(2)
  return (packagePath.isEmpty() || packagePath.split('/').none { it.isEmpty() || it == "." || it == ".." }) &&
         target.split('/').none { it.isEmpty() || it == "." || it == ".." }
}

private fun isSafeSourceTreePrefix(prefix: String): Boolean {
  return prefix.isEmpty() ||
         prefix.none { it == '\\' || it == ':' || it == '\u0000' } &&
         prefix.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
