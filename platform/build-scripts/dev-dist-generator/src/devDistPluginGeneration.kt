@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicDescriptorFacts
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicModuleDescriptor
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicNativePolicy
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparationFacts
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparedEffect
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparedSourceManifest
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import com.intellij.platform.distributionContent.DevDistPlatformJars
import com.intellij.platform.productMode.ProductMode
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.dev.DEV_PLUGIN_PREPARATION_FORMAT
import org.jetbrains.intellij.build.dev.DevPluginLayoutAsset
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetMapping
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetPreparation
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetTransform
import org.jetbrains.intellij.build.dev.DevPluginPreparationOperation
import org.jetbrains.intellij.build.dev.DevPluginPreparationRecipe
import org.jetbrains.intellij.build.dev.DevPluginReference
import org.jetbrains.intellij.build.dev.devBuildPathIdentity
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.impl.LibraryEntriesLayoutPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.frontendIncompatibleRootModuleNames
import org.jetbrains.intellij.build.impl.getLibNameBySourceFile
import org.jetbrains.intellij.build.isTestModule
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import java.util.Collections
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap

/**
 * The catalogue ID of the generated descriptor of [mainModule]. The same descriptor serves every platform and every
 * product, so a plan file reads the same for every product that plans the plugin the same way.
 * `dev_dist_complex_plugin` of `dev_plugin_remainder.bzl` composes the same ID.
 */
internal fun devDistDescriptorInputId(mainModule: String): String = "descriptor:$mainModule"

/**
 * Registers every selected plugin from the same Product DSL and descriptor walk as the generated declarations.
 *
 * Each plugin is planned once per platform. When the platform plans agree, one neutral plan with the empty variant
 * replaces them, so the plugin gets one declaration. A plugin with a platform-specific asset keeps one record and one
 * chain per platform. So does a plugin that reuses a natives jar, see [reusesNatives]. The records share one folded
 * plan file when they have one JSON shape.
 *
 * [layoutBindings] holds the bindings of every request of the owner, see [bindGeneratedDevDistPluginLayouts].
 * [hasPackageAttribute] answers whether a content module descriptor declares a `package`, see [DescriptorCollector.hasPackageAttribute].
 *
 * [reuse] is an owner of the same section inputs with bound entries, see [DevDistBuildSections.fold]. A `(product,
 * plugin)` group of a plugin outside [recomputed] takes its entry and its records from [reuse]. [recomputed] names the
 * plugins whose own record differs in [owner], so only their plan inputs can differ. Returns the number of reused groups.
 */
internal fun registerGeneratedDevDistPluginPlans(
  owner: DevDistBuildSections,
  outputProvider: ModuleOutputProvider,
  layoutBindings: Map<DevDistPluginPlanKey, DevDistPluginLayoutBindings>,
  hasPackageAttribute: (Path) -> Boolean,
  reuse: DevDistBuildSections? = null,
  recomputed: Set<String> = emptySet(),
): Int {
  val reusedEntries = reuse?.pluginPlanEntries?.associateBy { it.product to it.mainModule }
  val plans = owner.descriptorPlans.associateBy(PluginDescriptorPlan::platformPrefix)
  val allVariants = owner.pluginRequests.mapTo(LinkedHashSet()) { it.variant.id }
  val requestsByPlugin = LinkedHashMap<Pair<String, String>, MutableList<DevDistPluginRequest>>()
  for (request in owner.pluginRequests) {
    requestsByPlugin.computeIfAbsent(request.product to request.layout.mainModule) { ArrayList() }.add(request)
  }
  // The declarations are checked once, here. Nothing below changes a plan or a record, and a check per request is
  // quadratic in the number of entries.
  owner.requireDescriptorDeclarationsUnchanged()
  val facts = PluginPlanFacts(owner.platformJars, hasPackageAttribute)

  // Phase one computes the records of every plugin beside each other. It reads the owner and writes nothing to it.
  // Phase two registers the records in request order, so the owner holds them in the order a sequential run produced.
  val outcomes = requestsByPlugin.entries.toList().mapConcurrent { (group, requests) ->
    val reused = if (reusedEntries == null || group.second in recomputed) {
      null
    }
    else {
      checkNotNull(reusedEntries.get(group)) { "The reused owner has no plan of '${group.second}' for '${group.first}'" }
    }
    if (reused != null) {
      return@mapConcurrent reusedPluginPlanGroup(reused)
    }
    computePluginPlanGroup(
      owner = owner,
      outputProvider = outputProvider,
      plans = plans,
      allVariants = allVariants,
      layoutBindings = layoutBindings,
      facts = facts,
      requests = requests,
    )
  }
  val failures = ArrayList<String>()
  val entries = ArrayList<DevDistPluginPlanEntry>()
  for (outcome in outcomes) {
    val failure = outcome.failure
    if (failure != null) {
      failures.add(failure)
      continue
    }
    for ((key, record) in outcome.records) {
      owner.registerPluginPlan(key, record)
    }
    entries.add(checkNotNull(outcome.entry))
  }
  check(failures.isEmpty()) {
    "Cannot generate ${failures.size} of ${requestsByPlugin.size} dev-plugin plans:\n" + failures.joinToString("\n")
  }
  checkPluginNativeTrees(entries)
  owner.bindPluginPlanEntries(entries)
  return if (reusedEntries == null) 0 else requestsByPlugin.keys.count { it.second !in recomputed }
}

/**
 * The plans of one `(product, plugin)` group: the entry, the records to register under their keys, or the failure.
 *
 * A neutral group holds the neutral record alone. The platform records it was decided from are not registered, which
 * is the state a sequential run reached after it dropped them.
 */
private class PluginPlanGroupOutcome(
  @JvmField val entry: DevDistPluginPlanEntry?,
  @JvmField val records: List<Pair<DevDistPluginPlanKey, DevDistPluginPlanRecord>>,
  @JvmField val failure: String?,
)

/** The outcome of a group that another owner of the same section inputs computed: its [entry] and the records of it. */
private fun reusedPluginPlanGroup(entry: DevDistPluginPlanEntry): PluginPlanGroupOutcome {
  return PluginPlanGroupOutcome(entry = entry, records = entry.records.map { (variant, record) -> entry.key(variant) to record }, failure = null)
}

private fun computePluginPlanGroup(
  owner: DevDistBuildSections,
  outputProvider: ModuleOutputProvider,
  plans: Map<String, PluginDescriptorPlan>,
  allVariants: Set<String>,
  layoutBindings: Map<DevDistPluginPlanKey, DevDistPluginLayoutBindings>,
  facts: PluginPlanFacts,
  requests: List<DevDistPluginRequest>,
): PluginPlanGroupOutcome {
  val first = requests.first()
  val mainModule = first.layout.mainModule
  try {
    check(requests.all { it.tier == first.tier }) {
      "Plugin '$mainModule' of '${first.product}' has more than one tier across the platforms: ${requests.mapTo(LinkedHashSet()) { it.tier.key }}"
    }
    val inputs = requests.map { request ->
      val bindings = requireNotNull(layoutBindings.get(request.key)) { "No layout bindings for ${request.key}" }
      pluginRequestInputs(owner, outputProvider, plans, request, request.variant, bindings, facts)
    }
    // One neutral plan serves all when these conditions hold. One layout, every platform, one descriptor for every
    // platform, equal plans, and no operation that reads the platform at run time. The plans are then equal but for the
    // variant stamp, so the neutral record is the first platform record stamped for the empty variant.
    val descriptorPlan = plans.getValue(first.product)
    val neutralShape = requests.all { it.layout === first.layout } &&
                       requests.mapTo(HashSet()) { it.variant.id } == allVariants &&
                       descriptorPlan.plugins.count { it.mainModule == mainModule && it.variant.isEmpty() } == 1
    fun neutralOutcome(firstRecord: DevDistPluginPlanRecord): PluginPlanGroupOutcome {
      val neutralVariant = PluginSymbolicVariant(id = "", distribution = first.variant.distribution)
      val record = firstRecord.relabel(neutralVariant)
      return PluginPlanGroupOutcome(
        entry = DevDistPluginPlanEntry(first.product, first.tier, mainModule, mapOf("" to first.layout), mapOf("" to record)),
        records = listOf(DevDistPluginPlanKey(first.product, mainModule, neutralVariant.id) to record),
        failure = null,
      )
    }

    // A group whose platforms state the same inputs plans one platform, when nothing of that plan reads the platform.
    val firstRecord = if (neutralShape && inputs.map(PluginRequestInputs::platformMask).distinct().size == 1 &&
                          !hasPlatformLayoutContent(first.layout)) {
      inputs.first().computeRecord(owner)
    }
    else {
      null
    }
    if (firstRecord != null && !planReadsPlatform(firstRecord.second, inputs.first().nativePolicy)) {
      if (owner.verifyPlanUnits) {
        val all = inputs.map { it.computeRecord(owner).second }
        check(all.map(DevDistPluginPlanRecord::neutralGraph).distinct().size == 1 && all.none(::reusesNatives)) {
          "Plugin '$mainModule' of '${first.product}' was decided platform-free, and its platform plans differ"
        }
      }
      return neutralOutcome(firstRecord.second)
    }

    val records = LinkedHashMap<String, DevDistPluginPlanRecord>()
    val keyedRecords = ArrayList<Pair<DevDistPluginPlanKey, DevDistPluginPlanRecord>>(requests.size)
    val layouts = LinkedHashMap<String, PluginLayout>()
    for ((index, input) in inputs.withIndex()) {
      val keyedRecord = if (index == 0 && firstRecord != null) firstRecord else input.computeRecord(owner)
      keyedRecords.add(keyedRecord)
      records.put(input.variant.id, keyedRecord.second)
      layouts.put(input.variant.id, input.request.layout)
    }
    val neutral = neutralShape &&
                  records.values.map(DevDistPluginPlanRecord::neutralGraph).distinct().size == 1 &&
                  records.values.none(::reusesNatives)
    if (neutral) {
      return neutralOutcome(keyedRecords.first().second)
    }
    return PluginPlanGroupOutcome(
      entry = DevDistPluginPlanEntry(first.product, first.tier, mainModule, layouts, records),
      records = keyedRecords,
      failure = null,
    )
  }
  catch (error: Throwable) {
    if (error is InterruptedException) {
      throw error
    }
    return PluginPlanGroupOutcome(entry = null, records = emptyList(), failure = "${first.product}/$mainModule: ${error.message ?: error.javaClass.name}")
  }
}

/**
 * Whether [layout] states content for one platform: a platform resource generator or a platform custom asset. Such a
 * layout gives each platform its own inputs, so its platforms are planned one by one.
 */
private fun hasPlatformLayoutContent(layout: PluginLayout): Boolean {
  return layout.platformResourceGenerators.isNotEmpty() || layout.customAssets.any { it.platformSpecific != null }
}

/**
 * Whether the platform can change [record], though the platform plans state the same inputs.
 *
 * The native policies of one product differ in the target alone, and only a presigned extraction reads the target.
 * A plan can meet one only in an archive of its catalogue whose library name the product presigns. A plan that
 * reuses a natives jar counts too, see [reusesNatives].
 */
private fun planReadsPlatform(record: DevDistPluginPlanRecord, nativePolicy: PluginSymbolicNativePolicy): Boolean {
  if (reusesNatives(record)) {
    return true
  }
  val presigned = nativePolicy.presignedLibraries
  return presigned.isNotEmpty() && record.plan.catalogue.artifacts.any { artifact ->
    artifact.kind == "archive" && getLibNameBySourceFile(Path.of(artifact.fileName)) in presigned
  }
}

/**
 * Whether [record] reuses a natives jar. The component of such a plugin places the native tree of its own platform, so
 * the plugin keeps one component per platform.
 */
private fun reusesNatives(record: DevDistPluginPlanRecord): Boolean {
  return record.plan.reusableArtifacts.any { it.recipe.writer.nativeLib.isNotEmpty() }
}

/**
 * The facts every plan of the run shares: the platform members of each product, and the fact of each content module
 * descriptor a plan reads. Every request of a product reads the same set, and every variant of a plugin reads the same
 * descriptors.
 */
private class PluginPlanFacts(
  private val platformJars: DevDistPlatformJars,
  private val hasPackageAttribute: (Path) -> Boolean,
) {
  private val platformModulesByProduct = ConcurrentHashMap<String, Set<String>>()

  fun platformModules(product: String): Set<String> {
    platformModulesByProduct.get(product)?.let { return it }
    val computed = platformJars.platformJars.asSequence()
      .filter { it.product == product }
      .flatMap { it.members.asSequence() }
      .plus(platformJars.platformContentModules.asSequence().filter { it.product == product }.map { it.module })
      .toHashSet()
    return platformModulesByProduct.putIfAbsent(product, computed) ?: computed
  }

  fun moduleDescriptor(file: Path): PluginSymbolicModuleDescriptor = PluginSymbolicModuleDescriptor(hasPackage = hasPackageAttribute(file))
}

/** The bindings of one request that read the layout, the index and the module outputs, and no state of the owner. */
@ApiStatus.Internal
class DevDistPluginLayoutBindings internal constructor(
  @JvmField internal val libraryLayout: GeneratedLibraryLayoutBindings?,
  @JvmField val layoutPatcherFacts: PluginSymbolicPreparationFacts,
  @JvmField internal val assets: GeneratedDevPluginBindings,
) {
  val preparationFacts: PluginSymbolicPreparationFacts
    get() = mergeGeneratedPreparationFacts(
      mergeGeneratedPreparationFacts(mergeGeneratedPreparationFacts(PluginSymbolicPreparationFacts(), libraryLayout?.facts), layoutPatcherFacts),
      assets.facts,
    )
}

/**
 * Binds the layout of [request] for [variant]. A layout fact the plan cannot state throws [DevDistUnplannableLayoutException].
 * [half] binds the closed asset sources and the embedded frontend. [embeddedHomeOf] answers the product whose embedded
 * descriptor action a layout of a product reads, see [DevDistEmbeddedFrontendClasses.home].
 */
internal fun bindDevDistPluginLayout(
  request: DevDistPluginRequest,
  variant: PluginSymbolicVariant,
  index: DevDistBazelIndex,
  outputProvider: ModuleOutputProvider,
  resources: DevDistResourceSources,
  half: DevDistHalf,
  embeddedHomeOf: (String) -> String = { it },
): DevDistPluginLayoutBindings {
  val descriptorInput = devDistDescriptorInputId(request.layout.mainModule)
  requireNoModuleExcludes(request.layout)
  val libraryLayout = generatedLibraryLayoutBindings(request, index, descriptorInput, half.embeddedFrontend, embeddedHomeOf)
  return DevDistPluginLayoutBindings(
    libraryLayout = libraryLayout,
    layoutPatcherFacts = generatedLayoutPatcherFacts(request.layout, libraryLayout?.handledLayoutSlots.orEmpty()),
    assets = generateDevPluginAssetBindings(request.copy(variant = variant), index, outputProvider, half.assetBinder, resources),
  )
}

/**
 * The layout bindings of the kept requests, keyed by request key, and the main modules that the bindings drop, sorted.
 * The value of [dropped] is the reason of the first rejected request of the main module.
 */
internal class DevDistPluginLayoutBindingOutcome(
  @JvmField val bindings: Map<DevDistPluginPlanKey, DevDistPluginLayoutBindings>,
  @JvmField val dropped: Map<String, String>,
)

/**
 * Binds the layout of every request before the descriptor plan reads a plugin. A layout fact the plan cannot state
 * stops the run, for a bundled and an additional plugin alike. [registerGeneratedDevDistPluginPlans] reads the
 * bindings back by request key. [resources] resolves the `withResource*` declarations once for every request.
 * [half] binds the closed asset sources and the embedded frontend. [embeddedHomeOf] answers the home of the embedded
 * descriptor class of a product, see [DevDistEmbeddedFrontendClasses.home].
 *
 * A rejected request that [tolerated] accepts does not stop the run. The outcome drops every request of its main
 * module then, because a plan group needs the bindings of each of its requests.
 */
internal fun bindGeneratedDevDistPluginLayouts(
  requests: List<DevDistPluginRequest>,
  index: DevDistBazelIndex,
  outputProvider: ModuleOutputProvider,
  half: DevDistHalf,
  resources: DevDistResourceSources = DevDistResourceSources(index, outputProvider),
  embeddedHomeOf: (String) -> String = { it },
  tolerated: (DevDistPluginRequest) -> Boolean = { false },
): DevDistPluginLayoutBindingOutcome {
  // The bindings of a request read no state of another request, so they are computed beside each other. The outcomes
  // are then read in request order, so the run stops on the first rejected request, as a sequential loop did.
  val outcomes = requests.mapConcurrent { request ->
    try {
      Result.success(bindDevDistPluginLayout(request, request.variant, index, outputProvider, resources, half, embeddedHomeOf))
    }
    catch (e: DevDistUnplannableLayoutException) {
      Result.failure(e)
    }
  }
  val dropped = TreeMap<String, String>()
  for ((request, outcome) in requests.zip(outcomes)) {
    val failure = outcome.exceptionOrNull() ?: continue
    if (!tolerated(request)) {
      throw failure
    }
    dropped.putIfAbsent(request.layout.mainModule, failure.message.orEmpty())
  }
  val result = LinkedHashMap<DevDistPluginPlanKey, DevDistPluginLayoutBindings>()
  for ((request, outcome) in requests.zip(outcomes)) {
    if (request.layout.mainModule !in dropped) {
      result.put(request.key, outcome.getOrThrow())
    }
  }
  return DevDistPluginLayoutBindingOutcome(bindings = result, dropped = Collections.unmodifiableMap(dropped))
}

/**
 * The inputs of the plan of [request] for [variant]. [computeRecord] plans them. [platformMask] states them without the
 * platform, so a group can tell before any plan whether its platforms plan alike.
 */
private class PluginRequestInputs(
  @JvmField val request: DevDistPluginRequest,
  @JvmField val variant: PluginSymbolicVariant,
  private val project: JpsProject,
  private val descriptorFacts: PluginSymbolicDescriptorFacts,
  private val preparationFacts: PluginSymbolicPreparationFacts,
  private val catalogueFacts: DevDistPluginCatalogueFacts,
  @JvmField val nativePolicy: PluginSymbolicNativePolicy,
  private val bindingOperations: List<List<DevPluginPreparationOperation>>,
  private val recipeFactory: (DevDistPluginBuildPlan) -> DevPluginPreparationRecipe,
) {
  /** The plan record with its key. It reads the owner and registers nothing. */
  fun computeRecord(owner: DevDistBuildSections): Pair<DevDistPluginPlanKey, DevDistPluginPlanRecord> {
    val record = owner.computePluginPlan(
      product = request.product,
      layout = request.layout,
      project = project,
      descriptorFacts = descriptorFacts,
      preparationFacts = preparationFacts,
      variant = variant,
      catalogueFacts = catalogueFacts,
      nativePolicy = nativePolicy,
      preparationRecipeFactory = recipeFactory,
    )
    return DevDistPluginPlanKey(request.product, request.layout.mainModule, variant.id) to record
  }

  /**
   * Every input without the platform: the variant without its id and distribution. The [nativePolicy] names no target,
   * so it is the same for every platform of one product.
   */
  fun platformMask(): List<Any?> = listOf(
    descriptorFacts, preparationFacts, catalogueFacts, bindingOperations,
    variant.copy(id = "", distribution = null),
    nativePolicy,
  )
}

/** The inputs of the plan of [request] for [variant]. It reads the owner and plans nothing. */
private fun pluginRequestInputs(
  owner: DevDistBuildSections,
  outputProvider: ModuleOutputProvider,
  plans: Map<String, PluginDescriptorPlan>,
  request: DevDistPluginRequest,
  variant: PluginSymbolicVariant,
  bindings: DevDistPluginLayoutBindings,
  facts: PluginPlanFacts,
): PluginRequestInputs {
  val descriptorPlan = requireNotNull(plans.get(request.product)) {
    "No descriptor plan exists for '${request.product}'"
  }
  val entry = descriptorPlan.plugins.singleOrNull {
    it.mainModule == request.layout.mainModule && it.servesDevPlatform(variant.id)
  } ?: error("No unique descriptor entry serves '${request.layout.mainModule}' on '${variant.id}'")
  require(entry.layout === request.layout) {
    "The descriptor plan and packing request selected different layouts"
  }
  val descriptorLabel = owner.descriptorDeclaration(descriptorPlan, entry).label
  val descriptorInput = devDistDescriptorInputId(request.layout.mainModule)
  val descriptorFacts = descriptorFacts(request, entry, outputProvider, facts)
    .copy(pluginXmlInput = descriptorInput)
  val libraryLayout = bindings.libraryLayout
  val assets = bindings.assets
  val preparationFacts = bindings.preparationFacts
  val descriptorRawInput = DevDistPluginRawInput(
    id = descriptorInput,
    label = descriptorLabel,
    kind = "file",
    fileName = "${request.layout.mainModule}.plugin.xml",
  )
  return PluginRequestInputs(
    request = request,
    variant = variant,
    project = request.propertiesProject(outputProvider),
    descriptorFacts = descriptorFacts,
    preparationFacts = preparationFacts,
    catalogueFacts = assets.catalogueFacts.copy(
      additionalInputs = listOf(descriptorRawInput) + libraryLayout?.catalogueFacts?.additionalInputs.orEmpty() +
                         assets.catalogueFacts.additionalInputs,
      additionalLibraries = (libraryLayout?.catalogueFacts?.additionalLibraries.orEmpty() + assets.catalogueFacts.additionalLibraries).distinct(),
      testModules = assets.catalogueFacts.testModules +
                    testOutputModules(request = request, entry = entry, outputProvider = outputProvider),
    ),
    nativePolicy = request.copy(variant = variant).nativePolicy(),
    bindingOperations = listOf(libraryLayout?.operations.orEmpty(), assets.operations),
    recipeFactory = {
      DevPluginPreparationRecipe(
        version = DEV_PLUGIN_PREPARATION_FORMAT,
        operations = libraryLayout?.operations.orEmpty() + assets.operations,
      )
    },
  )
}

/**
 * Selects the JPS modules of a Product DSL test plugin that are packed from test output.
 *
 * The main module joins by the rule of [isTestModule], as the reference packager applies it.
 * A content module joins when the plan reads its descriptor from test output. The main module's own descriptor states
 * where the plan reads it, not what is packed, so it adds nothing here.
 */
internal fun testOutputModules(
  request: DevDistPluginRequest,
  entry: PluginDescriptorEntry,
  outputProvider: ModuleOutputProvider,
): Set<String> {
  if (request.testPlugin == null) return emptySet()

  val mainModule = request.layout.mainModule
  val result = LinkedHashSet<String>()
  if (outputProvider.findRequiredModule(mainModule).isTestModule()) {
    result.add(mainModule)
  }
  for (descriptor in entry.descriptors) {
    if (!descriptor.testOutput) continue
    val moduleName = checkNotNull(descriptor.moduleName)
    if (moduleName != mainModule) {
      result.add(moduleName)
    }
  }
  return result
}

private fun generatedLayoutPatcherFacts(layout: PluginLayout, boundSlots: Set<String>): PluginSymbolicPreparationFacts {
  val omittedSlots = layout.patchers.withIndex().mapNotNullTo(LinkedHashSet()) { (index, patcher) ->
    val key = "layout-patcher:$index"
    if (key in boundSlots) return@mapNotNullTo null
    val owner = patcher as? DevPluginLayoutAssetOwner ?: return@mapNotNullTo null
    require(owner.devPluginLayoutAssetSpec.omitted) { "Layout patcher $index has an unsupported development asset declaration" }
    key
  }
  return PluginSymbolicPreparationFacts(omittedSlots = omittedSlots)
}

/** [operations] holds the packer-executed operations of the library-entry patchers and of the embedded frontend icons. */
internal class GeneratedLibraryLayoutBindings(
  @JvmField val facts: PluginSymbolicPreparationFacts,
  @JvmField val catalogueFacts: DevDistPluginCatalogueFacts,
  @JvmField val handledLayoutSlots: Set<String>,
  @JvmField val operations: List<DevPluginPreparationOperation>,
)

/**
 * Binds library-entry patchers and the two patchers of the embedded frontend to packer-executed operations and declared
 * file inputs. Another layout patcher declares an omitted asset slot, see [generatedLayoutPatcherFacts], or fails
 * generation. [embeddedFrontend] is `null` for a half without an embedded frontend.
 */
private fun generatedLibraryLayoutBindings(
  request: DevDistPluginRequest,
  index: DevDistBazelIndex,
  descriptorInput: String,
  embeddedFrontend: DevDistEmbeddedFrontendSupport?,
  embeddedHomeOf: (String) -> String,
): GeneratedLibraryLayoutBindings? {
  val layout = request.layout
  if (layout.patchers.isEmpty()) return null
  val effects = LinkedHashMap<String, PluginSymbolicPreparedEffect>()
  val manifests = LinkedHashMap<String, PluginSymbolicPreparedSourceManifest>()
  val modulePatches = LinkedHashMap<String, MutableList<JarSourceRecipe>>()
  val operations = ArrayList<DevPluginPreparationOperation>()
  val additionalInputs = ArrayList<DevDistPluginRawInput>()
  val additionalLibraries = ArrayList<DevDistPluginLibraryInput>()
  for ((layoutIndex, callback) in layout.patchers.withIndex()) {
    if (callback is LibraryEntriesLayoutPatcher) {
      val binding = libraryEntriesLayoutBinding(callback, layoutIndex, index)
      additionalLibraries.add(DevDistPluginLibraryInput(callback.libraryName, callback.libraryModuleName))
      effects.put(binding.key, binding.effect)
      // The extracted entries patch the target module. They add no meaningful source to the jar.
      manifests.put(binding.output, PluginSymbolicPreparedSourceManifest(0, listOf("keep")))
      modulePatches.computeIfAbsent(callback.targetModuleName) { ArrayList() }.add(JarSourceRecipe(binding.output, "prepared", "prepared"))
      operations.add(binding.operation)
    }
  }
  if (embeddedFrontend != null && embeddedFrontend.packsEmbeddedFrontend(layout)) {
    val binding = embeddedFrontendLayoutBinding(embeddedFrontend, request, home = embeddedHomeOf(request.product))
    effects.put(binding.key, binding.effect)
    // The copied icons patch the icons module. They add no meaningful source to the jar.
    manifests.put(binding.output, PluginSymbolicPreparedSourceManifest(0, listOf("keep")))
    operations.add(binding.operation)
    additionalInputs.addAll(binding.inputs)
    modulePatches.computeIfAbsent(embeddedFrontend.descriptorModule) { ArrayList() }.addAll(0, binding.frontendSplitSources)
    modulePatches.computeIfAbsent(embeddedFrontend.iconsModule) { ArrayList() }.add(0, JarSourceRecipe(binding.output, "prepared", "prepared"))
  }
  if (effects.isEmpty()) return null
  val descriptor = JarSourceRecipe(descriptorInput, "file", "none", PLUGIN_XML_RELATIVE_PATH, options = listOf("patch"))
  modulePatches.computeIfAbsent(layout.mainModule) { ArrayList() }.add(0, descriptor)
  return GeneratedLibraryLayoutBindings(
    facts = PluginSymbolicPreparationFacts(effects = effects, modulePatches = modulePatches, preparedSourceManifests = manifests),
    catalogueFacts = DevDistPluginCatalogueFacts(additionalInputs = additionalInputs, additionalLibraries = additionalLibraries),
    handledLayoutSlots = LinkedHashSet(effects.keys),
    operations = operations,
  )
}

private class LibraryEntriesLayoutBinding(
  @JvmField val key: String,
  @JvmField val output: String,
  @JvmField val effect: PluginSymbolicPreparedEffect,
  @JvmField val operation: DevPluginPreparationOperation,
)

/**
 * Binds one [LibraryEntriesLayoutPatcher] to a packer-executed `layout-assets` operation. The operation extracts the
 * entries under the patcher's prefix from the library jar. Its output is a prepared source of the target module's jar.
 * The operation names the library container, so a version bump of the jar leaves the plan as it is.
 */
private fun libraryEntriesLayoutBinding(
  callback: LibraryEntriesLayoutPatcher,
  layoutIndex: Int,
  index: DevDistBazelIndex,
): LibraryEntriesLayoutBinding {
  val library = requireNotNull(index.targets.modules.get(callback.libraryModuleName)?.moduleLibraries?.get(callback.libraryName)) {
    "Library layout patcher cannot resolve '${callback.libraryName}' in '${callback.libraryModuleName}'"
  }
  require(library.jarTargets.size == 1) {
    "Library layout patcher '${callback.libraryName}' requires one jar, but the model declares ${library.jarTargets.size}"
  }
  val input = library.target
  val key = "layout-patcher:$layoutIndex"
  val id = "layout-assets:$key"
  val output = "$id:output"
  val operation = DevPluginPreparationOperation(
    id = id,
    kind = "layout-assets",
    inputs = listOf(DevPluginReference(input)),
    output = output,
    manifest = "keep",
    layoutAssets = DevPluginLayoutAssetPreparation(
      format = "entries",
      assets = listOf(DevPluginLayoutAsset(
        destination = "",
        sources = listOf(0),
        transform = DevPluginLayoutAssetTransform.archiveTree(mappings = listOf(DevPluginLayoutAssetMapping(pattern = "${callback.prefix}**"))),
      )),
    ),
  )
  return LibraryEntriesLayoutBinding(key = key, output = output, effect = PluginSymbolicPreparedEffect(operation), operation = operation)
}

/**
 * The embedded frontend as the plan states it: the icons operation with its effect slot, the declared file inputs, and
 * the two patched files of the frontend jar. [frontendSplitSources] lead the sources of that jar.
 */
private class EmbeddedFrontendLayoutBinding(
  @JvmField val key: String,
  @JvmField val output: String,
  @JvmField val effect: PluginSymbolicPreparedEffect,
  @JvmField val operation: DevPluginPreparationOperation,
  @JvmField val inputs: List<DevDistPluginRawInput>,
  @JvmField val frontendSplitSources: List<JarSourceRecipe>,
)

/**
 * Binds the two patchers of the layout that embeds the frontend. The first is the embedded product descriptor, the
 * second is the frontend patcher of the production layout, and both declare an omitted asset slot. Two Bazel actions of
 * the descriptor tool produce the embedded descriptor and the client application info, and the frontend jar reads both
 * as patched files. The frontend product icons are a `layout-assets` operation that copies each icon under its client
 * name into the icons jar. The packer executes it, so the chain needs no Kotlin preparation.
 *
 * The application info helper and the branding tree follow the product of [request]: the baseline product keeps the unsuffixed
 * helper targets of the plugin's own `dev_dist_plugin` call, and a divergent product reads product-suffixed helpers. The
 * embedded descriptor follows [home], the product that declares the one action of the class of the product of [request].
 */
private fun embeddedFrontendLayoutBinding(
  support: DevDistEmbeddedFrontendSupport,
  request: DevDistPluginRequest,
  home: String,
): EmbeddedFrontendLayoutBinding {
  val layout = request.layout
  require(layout.patchers.size == 2 && layout.patchers.all { (it as? DevPluginLayoutAssetOwner)?.devPluginLayoutAssetSpec?.omitted == true }) {
    "The layout that embeds the frontend declares the embedded product descriptor and the frontend patcher, both with an omitted asset slot"
  }
  val product = request.product
  val frontendApplicationInfoName = frontendApplicationInfoTargetName(support, product)
  val embeddedDescriptorName = embeddedProductDescriptorTargetName(support, home)
  val branding = embeddedFrontendBranding(support, request)
  val clientApplicationInfo = DevDistPluginRawInput(
    id = "${support.inputIdPrefix}:client-application-info",
    label = "${support.pluginPackage}:$frontendApplicationInfoName",
    kind = "file",
    fileName = "$frontendApplicationInfoName.xml",
  )
  val brandingInput = DevDistPluginRawInput(
    id = "${support.inputIdPrefix}:branding",
    label = branding.label,
    kind = "directory",
    fileName = "images",
    sourceTreePrefix = branding.sourceTreePrefix,
  )
  val embeddedDescriptor = DevDistPluginRawInput(
    id = "${support.inputIdPrefix}:embedded-product-descriptor",
    label = "${support.pluginPackage}:$embeddedDescriptorName",
    kind = "file",
    fileName = "$embeddedDescriptorName.xml",
  )
  val key = "layout-patcher:${support.frontendPatcherIndex}"
  val id = "layout-assets:$key"
  val output = "$id:output"
  val operation = DevPluginPreparationOperation(
    id = id,
    kind = "layout-assets",
    inputs = support.iconPatches.map { (source, _) -> DevPluginReference(brandingInput.id, source) },
    output = output,
    manifest = "keep",
    layoutAssets = DevPluginLayoutAssetPreparation(
      format = "entries",
      assets = support.iconPatches.mapIndexed { index, (_, destination) -> DevPluginLayoutAsset(destination = destination, sources = listOf(index)) },
    ),
  )
  return EmbeddedFrontendLayoutBinding(
    key = key,
    output = output,
    effect = PluginSymbolicPreparedEffect(operation),
    operation = operation,
    inputs = listOf(clientApplicationInfo, brandingInput, embeddedDescriptor),
    frontendSplitSources = listOf(
      JarSourceRecipe(embeddedDescriptor.id, "file", "none", support.descriptorLoadPath, options = listOf("patch")),
      JarSourceRecipe(clientApplicationInfo.id, "file", "none", support.clientApplicationInfoPath, options = listOf("patch")),
    ),
  )
}

/** The frontend product icons of one request, as a directory input of the icons operation. */
private class EmbeddedFrontendBranding(
  @JvmField val label: String,
  @JvmField val sourceTreePrefix: String,
)

/**
 * The branding filegroup of the product of [request] and the source-tree prefix of its frontend icons.
 *
 * The baseline product keeps the hand-written root filegroup. Every other product uses `<images package>:frontend_branding`
 * over its `imagesDirectoryPath`, so a product that embeds the frontend must declare that filegroup.
 */
private fun embeddedFrontendBranding(support: DevDistEmbeddedFrontendSupport, request: DevDistPluginRequest): EmbeddedFrontendBranding {
  requireNotNull(request.properties.imagesDirectoryPath) {
    "Product '${request.product}' embeds the frontend but declares no imagesDirectoryPath for frontend branding"
  }
  val relative = support.imagesDirectory(request.product)
  if (request.product == support.baselineProduct) {
    return EmbeddedFrontendBranding(label = support.baselineBrandingLabel, sourceTreePrefix = relative)
  }
  val packagePath = relative.substringBeforeLast('/', missingDelimiterValue = "")
  require(packagePath.isNotEmpty()) {
    "Product '${request.product}' images directory '$relative' has no parent package for a frontend_branding filegroup"
  }
  return EmbeddedFrontendBranding(label = "//$packagePath:frontend_branding", sourceTreePrefix = relative)
}

/** The helper target name of the embedded product descriptor of [product]. The baseline product keeps the unsuffixed name. */
@ApiStatus.Internal
fun embeddedProductDescriptorTargetName(support: DevDistEmbeddedFrontendSupport, product: String): String {
  val base = "${support.pluginMainModule}_dev_embedded_product_descriptor"
  return if (product == support.baselineProduct) base else "${base}_$product"
}

/** The helper target name of the frontend application info of [product]. The baseline product keeps the unsuffixed name. */
internal fun frontendApplicationInfoTargetName(support: DevDistEmbeddedFrontendSupport, product: String): String {
  val base = "${support.pluginMainModule}_dev_frontend_application_info"
  return if (product == support.baselineProduct) base else "${base}_$product"
}

internal fun mergeGeneratedPreparationFacts(
  first: PluginSymbolicPreparationFacts,
  second: PluginSymbolicPreparationFacts?,
): PluginSymbolicPreparationFacts {
  if (second == null) return first
  val effects = LinkedHashMap(first.effects)
  for ((key, effect) in second.effects) {
    require(effects.putIfAbsent(key, effect) == null) { "Duplicate generated preparation effect '$key'" }
  }
  val patches = LinkedHashMap(first.modulePatches)
  for ((module, sources) in second.modulePatches) {
    require(patches.putIfAbsent(module, sources) == null) { "Duplicate generated module patches for '$module'" }
  }
  val manifests = LinkedHashMap(first.preparedSourceManifests)
  for ((output, manifest) in second.preparedSourceManifests) {
    require(manifests.putIfAbsent(output, manifest) == null) { "Duplicate generated prepared output '$output'" }
  }
  val declaredAssets = LinkedHashMap(first.declaredAssets)
  for ((key, assets) in second.declaredAssets) {
    require(declaredAssets.putIfAbsent(key, assets) == null) { "Duplicate generated declared asset slot '$key'" }
  }
  return PluginSymbolicPreparationFacts(
    effects = effects,
    modulePatches = patches,
    dependencies = first.dependencies + second.dependencies,
    preparedSourceManifests = manifests,
    declaredAssets = declaredAssets,
    omittedSlots = first.omittedSlots + second.omittedSlots,
  )
}

/**
 * The dev distribution has no module filter. A directory that a module jar must not carry lives outside the resource
 * root, and the layout copies it from there, see ADR 0026.
 */
private fun requireNoModuleExcludes(layout: PluginLayout) {
  val (module, excludes) = layout.moduleExcludes.entries.firstOrNull { it.value.isNotEmpty() } ?: return
  throw DevDistUnplannableLayoutException(
    "Plugin '${layout.mainModule}' excludes $excludes from the module '$module'. " +
    "The dev distribution has no module filter: move the directory out of the resource root and copy it with withResource."
  )
}

/**
 * Enumerates every supported target for each split product of [half]. [additionalModulesByProduct] holds the modules the
 * dev-server run configurations of a product name, see [devDistRunConfigurationModules]. A module of it without a
 * request stops the run. [testPlugins] are the Product DSL test plugins that such a module can be.
 * [registryLayoutsByProduct] holds the registry plugins that each product plans in the registry tier, see
 * [assignRegistryLayoutsToProducts].
 */
internal fun enumerateGeneratedDevDistPluginRequests(
  products: List<DiscoveredProduct>,
  outputProvider: ModuleOutputProvider,
  additionalModulesByProduct: Map<String, List<String>>,
  half: DevDistHalf,
  testPlugins: List<TestPluginSpec>,
  registryLayoutsByProduct: Map<String, List<String>>,
): List<DevDistPluginRequest> {
  val variants = SUPPORTED_DISTRIBUTIONS.map { distribution ->
    PluginSymbolicVariant(id = devDistHostPlatform(distribution), distribution = distribution)
  }
  return products.flatMap { product ->
    enumerateDevDistPluginRequests(
      product = product,
      outputProvider = outputProvider,
      variants = variants,
      extraPluginModules = half.extraPluginModules(product.name) ?: return@flatMap emptyList(),
      testPlugins = testPlugins,
      additionalModules = additionalModulesByProduct.get(product.name).orEmpty(),
      registryModules = registryLayoutsByProduct.get(product.name).orEmpty(),
    )
  }
}

/**
 * The registry plugins of the community half, sorted: the plugins of [population] and the layouts of [registryLayouts]
 * that no request of [composed] plans.
 *
 * A registry plugin needs a JPS module, see [findModule], and a Bazel package, see [isPlaced]. The descriptor plan cannot
 * place a plugin without a package. A test plugin is planned only when a run configuration names it. A test plugin is a
 * module that [isTestModule] matches, or a module of [testPluginModules], which match a Product DSL test
 * plugin, see [resolveDevDistTestPlugins].
 */
internal fun devDistRegistryPlugins(
  population: Collection<String>,
  registryLayouts: Collection<String>,
  composed: Set<String>,
  isPlaced: (String) -> Boolean,
  findModule: (String) -> JpsModule?,
  testPluginModules: Set<String> = emptySet(),
): List<String> {
  val candidates = TreeSet(population)
  candidates.addAll(registryLayouts)
  return candidates.filter { mainModule ->
    if (mainModule in composed || mainModule in testPluginModules || !isPlaced(mainModule)) {
      return@filter false
    }
    val module = findModule(mainModule) ?: return@filter false
    !module.isTestModule()
  }
}

/**
 * The registry plugins that each product plans in the registry tier, keyed by product in [productOrder].
 *
 * A plugin of [registryPlugins] goes to the first product of [productOrder] whose explicit layouts in [layoutsByProduct]
 * hold its main module. A plugin that no product holds goes to the first product of [productOrder] in
 * [layoutsByProduct], which gives it an automatic layout. A product outside [layoutsByProduct] gets no plugin. A product
 * without a plugin has no key. [productOrder] is the split product order of the half, see [DevDistHalf.splitDistributions].
 */
internal fun assignRegistryLayoutsToProducts(
  registryPlugins: Collection<String>,
  productOrder: Collection<String>,
  layoutsByProduct: Map<String, Set<String>>,
): Map<String, List<String>> {
  val fallback = productOrder.firstOrNull { it in layoutsByProduct }
  val result = LinkedHashMap<String, MutableList<String>>()
  for (mainModule in registryPlugins) {
    val product = productOrder.firstOrNull { layoutsByProduct.get(it)?.contains(mainModule) == true } ?: fallback ?: continue
    result.computeIfAbsent(product) { ArrayList() }.add(mainModule)
  }
  // The map order follows the product order, and not the order of the layouts.
  val ordered = LinkedHashMap<String, List<String>>()
  for (product in productOrder) {
    result.get(product)?.let { ordered.put(product, java.util.List.copyOf(it)) }
  }
  return ordered
}

/**
 * The `dev_dist_complex_plugin` calls of one complex plugin, as top-level statements.
 *
 * [sectionText] holds the calls the plugin's own `dev` section states, or `null`. [crossHalfText] holds the calls of the
 * cross-half plugin package at [crossHalfPath], or `null`. A community plugin states its baseline call in its own section
 * when every label of that call is one a community package can name. Every other call of a community plugin names a
 * product in its chain class, so it sits cross-half. [exportsPlanFiles] says the section exports the plan files, because
 * a cross-half call reads one of them. One blank line separates two calls, see [renderDevDistPluginExecutionCalls].
 */
internal class DevDistPluginCallRendering(
  @JvmField val sectionText: String?,
  @JvmField val crossHalfPath: String,
  @JvmField val crossHalfText: String?,
  @JvmField val exportsPlanFiles: Boolean,
)

/**
 * The rendered plugin executions of one run: the calls of every complex plugin keyed by main module and sorted, and the
 * `DEV_DIST_PLUGIN_COMPONENTS` map of `dev_dist_content_sets.bzl`.
 */
internal class DevDistPluginExecutionRendering(
  @JvmField val calls: Map<String, DevDistPluginCallRendering>,
  @JvmField val components: String,
)

/**
 * Renders the `dev_dist_complex_plugin` calls of every complex plugin, one chain per plan file behind them, and the
 * product and tier index that consumes every plugin component.
 *
 * The call of a plugin sits in the package its plan home names, see [DevDistPluginPlanHome]: the own package of an
 * ultimate plugin, and the cross-half plugin package of a community plugin. The call states `plan_package` when the
 * plan file sits in another package than the call.
 *
 * A plugin with a simple packaging has no chain here. Its component is the `dev_plugin` target its own section or its
 * cross-half package declares, and the index names that label. The component map states no bundled tier for a product
 * of [communityProducts], see [devDistCommunityProducts].
 */
internal fun renderGeneratedDevDistPluginExecutions(
  owner: DevDistBuildSections,
  files: DevDistPluginPlanFiles,
  productOrder: Collection<String> = owner.half.splitProducts,
  communityProducts: Set<String> = emptySet(),
): DevDistPluginExecutionRendering {
  val plans = owner.descriptorPlans.associateBy(PluginDescriptorPlan::platformPrefix)
  val rank = productOrder.withIndex().associate { (index, product) -> product to index }
  // The complex entries of every plugin, in product order, so the first group of a plugin holds its baseline product.
  val complexEntries = TreeMap<String, MutableList<DevDistPluginPlanEntry>>()
  for (entry in owner.pluginPlanEntries) {
    if (owner.simplePackaging(entry.mainModule, entry.product) == null) {
      complexEntries.computeIfAbsent(entry.mainModule) { ArrayList() }.add(entry)
    }
  }
  val complexComponents = HashMap<Pair<String, String>, GeneratedPluginComponent>()
  val calls = TreeMap<String, DevDistPluginCallRendering>()
  // The chain stems of one package fold to distinct paths on a case-insensitive file system: every chain target
  // writes `<package>/<stem>...` into `bazel-out`, and two stems that differ in case only share one file there.
  val targetIdentities = HashSet<String>()
  for ((mainModule, entries) in complexEntries) {
    entries.sortWith(compareBy({ rank.get(it.product) ?: Int.MAX_VALUE }, { it.product }))
    val home = files.home(mainModule)
    val callPackageLabel = devDistPluginCallPackageLabel(mainModule, home)
    val crossHalfPackageLabel = "//${crossHalfPackageDirectory(mainModule, product = null)}"
    // One call serves every product whose rendered call is the same. The call names no product, so the text without a
    // chain class is the key. The first group is the baseline and takes no class. Every other group takes the class of
    // its first product, which keeps the chain stems of two calls of one plugin apart.
    val groups = LinkedHashMap<String, MutableList<DevDistPluginPlanEntry>>()
    for (entry in entries) {
      val text = renderPluginCall(owner, files, plans.getValue(entry.product), entry, callPackageLabel, chainClass = "").text
      groups.computeIfAbsent(text) { ArrayList() }.add(entry)
    }
    val sectionTexts = ArrayList<String>()
    val crossHalfTexts = ArrayList<String>()
    var exportsPlanFiles = false
    for ((index, members) in groups.values.withIndex()) {
      val first = members.first()
      val chainClass = if (index == 0) "" else owner.half.caseSafeProductName(first.product)
      // The baseline call of a community plugin whose plan files sit in its own package moves into its section when a
      // community package can name every label of it. The chain class of every other call names a product.
      val inSection = !home.callIsCrossHalf ||
                      index == 0 && home.exportsPlanFiles && namesCommunityLabelsOnly(groups.keys.first(), owner.index.planPackageIsCommunity)
      val packageLabel = if (!home.callIsCrossHalf) callPackageLabel else if (inSection) home.packageLabel else crossHalfPackageLabel
      val rendered = renderPluginCall(owner, files, plans.getValue(first.product), first, packageLabel, chainClass)
      for (label in rendered.labels.values) {
        check(targetIdentities.add(devBuildPathIdentity(label))) {
          "Duplicate dev-plugin target '$label', or one that folds to it on a case-insensitive file system"
        }
      }
      if (inSection) sectionTexts.add(rendered.text) else crossHalfTexts.add(rendered.text)
      if (!inSection && home.packageLabel in rendered.planPackages) exportsPlanFiles = true
      val component = if (first.isNeutral) GeneratedPluginComponent(mainModule, label = rendered.labels.getValue(""))
      else GeneratedPluginComponent(mainModule, labels = rendered.labels)
      for (entry in members) {
        complexComponents.put(entry.product to mainModule, component)
      }
    }
    // Every call ends with one newline, so the join leaves one blank line between two calls.
    calls.put(mainModule, DevDistPluginCallRendering(
      sectionText = sectionTexts.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n"),
      crossHalfPath = crossHalfPackagePath(mainModule, product = null),
      crossHalfText = crossHalfTexts.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n"),
      exportsPlanFiles = exportsPlanFiles,
    ))
  }
  val components = groupPluginComponents(owner.pluginPlanEntries, product = { it.product }, tier = { it.tier }) { entry ->
    val packaging = owner.simplePackaging(entry.mainModule, entry.product)
    if (packaging != null) GeneratedPluginComponent(entry.mainModule, label = owner.devPluginLabel(packaging, entry.product))
    else complexComponents.getValue(entry.product to entry.mainModule)
  }
  checkComponentMembership(components)
  return DevDistPluginExecutionRendering(
    calls = Collections.unmodifiableMap(calls),
    components = renderPluginComponents(components, planLabel = owner.index::planLabel, communityProducts = communityProducts) { product ->
      owner.half.compositionOrder(product)
    },
  )
}

/**
 * The rendered call of one group with its embedded frontend helpers, the component label of each chain keyed by variant,
 * and the package of every plan file the call reads.
 */
private class RenderedPluginCall(@JvmField val text: String, @JvmField val labels: Map<String, String>, @JvmField val planPackages: Set<String>)

/** A quoted string of a rendered call that holds `//`: a label, including one with a `{platform}` token. */
private val CALL_LABEL = Regex("\"([^\"\\s]*//[^\"\\s]*)\"")

/**
 * Whether every label of the rendered call [text] is one a community package can name: `@community` or `@lib`, and a
 * `//` label in the [communityPass], see [isCommunityCallLabel].
 */
private fun namesCommunityLabelsOnly(text: String, communityPass: Boolean): Boolean {
  return CALL_LABEL.findAll(text).all { isCommunityCallLabel(it.groupValues.get(1), communityPass) }
}

/**
 * Renders the `dev_dist_complex_plugin` call of [entry] with [chainClass], and the embedded frontend helpers of its
 * product. The call states no product, so two products whose calls render alike share one call.
 */
private fun renderPluginCall(
  owner: DevDistBuildSections,
  files: DevDistPluginPlanFiles,
  descriptorPlan: PluginDescriptorPlan,
  entry: DevDistPluginPlanEntry,
  callPackageLabel: String,
  chainClass: String,
): RenderedPluginCall {
  val labels = LinkedHashMap<String, String>()
  val planPackages = LinkedHashSet<String>()
  val variants = ArrayList<DevDistPluginExecutionVariant>()
  for ((variant, record) in entry.records) {
    val descriptorEntry = descriptorPlan.plugins.single { it.mainModule == entry.mainModule && it.servesDevPlatform(variant) }
    val declaration = owner.descriptorDeclaration(descriptorPlan, descriptorEntry)
    val key = entry.key(variant)
    val planHome = files.planHome(key)
    planPackages.add(planHome.packageLabel)
    val platform = variant.ifEmpty { null }
    val configuration = DevDistPluginExecutionConfiguration(
      key = key,
      packageLabel = callPackageLabel,
      planPackage = if (planHome.packageLabel == callPackageLabel) "" else planHome.packageLabel,
      chainClass = chainClass,
      name = devDistChainStem(entry.mainModule, chainClass, platform),
      componentName = entry.mainModule,
      targetPlatform = platform,
      graph = files.executionGraphLabels(key, record),
      descriptorLabel = declaration.label,
      descriptorVariant = descriptorEntry.variant,
    )
    val rendered = renderDevDistPluginExecutionTargets(owner, entry, record, files, configuration)
    variants.add(rendered)
    labels.put(variant, rendered.componentLabel)
  }
  val helpers = owner.half.embeddedFrontend?.let { renderEmbeddedFrontendHelpers(it, entry, descriptorPlan) }.orEmpty()
  val callText = renderDevDistPluginExecutionCalls(entry.mainModule, variants)
  return RenderedPluginCall(text = if (helpers.isEmpty()) callText else helpers + "\n" + callText, labels = labels, planPackages = planPackages)
}

/**
 * The absolute label of the package that holds the calls of [mainModule]: the cross-half plugin package for a
 * community plugin, and the plan home's package otherwise.
 */
private fun devDistPluginCallPackageLabel(mainModule: String, home: DevDistPluginPlanHome): String {
  return if (home.callIsCrossHalf) "//${crossHalfPackageDirectory(mainModule, product = null)}" else home.packageLabel
}

/**
 * The standalone embedded frontend helper macros of one product, or the empty string for every other plugin.
 *
 * The embedded descriptor action is one per class: only the home of the class declares it, see
 * [EmbeddedProductDescriptorPlan.home]. The baseline product keeps the unsuffixed target name. The baseline product keeps
 * the application info helper inside the plugin's own `dev_dist_plugin` call. A divergent product cannot share that
 * helper, because the product application info differs. So that helper sits beside the product's
 * `dev_dist_complex_plugin` call under a product-suffixed name.
 */
private fun renderEmbeddedFrontendHelpers(
  support: DevDistEmbeddedFrontendSupport,
  entry: DevDistPluginPlanEntry,
  descriptorPlan: PluginDescriptorPlan,
): String {
  if (entry.mainModule != support.pluginMainModule) {
    return ""
  }
  val descriptorEntry = descriptorPlan.plugins.first { it.mainModule == support.pluginMainModule }
  // A frontend product packs the plugin without the embedded frontend, so its call has no helper, see `packsEmbeddedFrontend`.
  val embedded = descriptorEntry.embeddedProductDescriptor ?: return ""
  val product = entry.product
  val isBaseline = product == support.baselineProduct
  val calls = ArrayList<String>()
  if (embedded.home == product) {
    calls.add(renderEmbeddedProductDescriptorCall(support = support, embedded = embedded, product = product.takeUnless { isBaseline }))
  }
  if (!isBaseline) {
    val frontend = requireNotNull(embedded.frontendApplicationInfo) {
      "Product '$product' plans '${support.pluginMainModule}' without a frontend application info"
    }
    val frontendCall = Target("dev_dist_frontend_application_info")
    frontendCall.option("client_application_info", frontend.clientApplicationInfo)
    frontendCall.option("main_module", support.pluginMainModule)
    frontendCall.option("product", product)
    frontendCall.option("product_application_info", frontend.productApplicationInfo)
    calls.add(frontendCall.render().trim())
  }
  return calls.joinToString(separator = "\n\n", postfix = if (calls.isEmpty()) "" else "\n")
}

/**
 * The `dev_dist_embedded_product_descriptor` call of one class home. [product] is the home, or `null` for the baseline
 * product, which keeps the unsuffixed target name. The macro composes the content over the module-set table and derives
 * the conventional descriptor rows, so the call states only the other rows.
 */
private fun renderEmbeddedProductDescriptorCall(
  support: DevDistEmbeddedFrontendSupport,
  embedded: EmbeddedProductDescriptorPlan,
  product: String?,
): String {
  // The attributes in buildifier's alphabetical order, so a regenerate leaves the section unchanged.
  val attributes = TreeMap(embedded.content.attributes(DEV_DIST_MODULE_SETS_SYMBOL))
  if (embedded.descriptors.isNotEmpty()) {
    attributes.put("descriptors", LinkedHashMap(embedded.descriptors))
  }
  if (embedded.libraryDescriptors.isNotEmpty()) {
    attributes.put("library_descriptors", LinkedHashMap(embedded.libraryDescriptors))
  }
  attributes.put("main_module", support.pluginMainModule)
  product?.let { attributes.put("product", it) }
  if (embedded.separateJar.isNotEmpty()) {
    attributes.put("separate_jar", embedded.separateJar)
  }
  val call = Target("dev_dist_embedded_product_descriptor")
  for ((key, value) in attributes) {
    call.option(key, value)
  }
  return call.render().trim()
}

/**
 * One plugin component, keyed by its main module: one label for every platform, or one label per platform for a
 * platform-specific plugin. The component name and the fragment name are the main module, so neither is stated.
 */
internal class GeneratedPluginComponent(
  @JvmField val mainModule: String,
  @JvmField val label: String? = null,
  @JvmField val labels: Map<String, String> = emptyMap(),
)
/**
 * The components of [entries], keyed by product and then by tier of [DEV_DIST_COMPONENT_TIERS]. Every product of
 * [entries] has every component tier.
 *
 * An entry of another tier, such as [DevDistPluginTier.REGISTRY], gets no component, and [component] does not read it.
 * The tiers keep the entry order, because the bundled tier is the composition order.
 */
internal fun <E> groupPluginComponents(
  entries: Iterable<E>,
  product: (E) -> String,
  tier: (E) -> DevDistPluginTier,
  component: (E) -> GeneratedPluginComponent,
): Map<String, Map<DevDistPluginTier, List<GeneratedPluginComponent>>> {
  val result = LinkedHashMap<String, Map<DevDistPluginTier, MutableList<GeneratedPluginComponent>>>()
  for (entry in entries) {
    val tiers = result.computeIfAbsent(product(entry)) { DEV_DIST_COMPONENT_TIERS.associateWithTo(LinkedHashMap()) { ArrayList() } }
    val tierComponents = tiers.get(tier(entry)) ?: continue
    tierComponents.add(component(entry))
  }
  return result
}


private fun checkComponentMembership(products: Map<String, Map<DevDistPluginTier, List<GeneratedPluginComponent>>>) {
  for ((product, tiers) in products) {
    check(tiers.keys == DEV_DIST_COMPONENT_TIERS.toSet()) { "Dev-plugin tiers differ for '$product': ${tiers.keys}" }
    check(tiers.getValue(DevDistPluginTier.BUNDLED).isNotEmpty()) { "No bundled dev-plugin component for '$product'" }
    val modules = HashSet<String>()
    val labels = HashSet<String>()
    for (components in tiers.values) {
      for (component in components) {
        check(modules.add(component.mainModule)) { "Duplicate dev-plugin module '${component.mainModule}' for '$product'" }
        check((component.label == null) != component.labels.isEmpty()) { "Component '${component.mainModule}' must state one label form" }
        for (label in listOfNotNull(component.label) + component.labels.values) {
          check(labels.add(label)) { "Duplicate dev-plugin label '$label' for '$product'" }
        }
      }
    }
  }
}

/**
 * `DEV_DIST_PLUGIN_COMPONENTS[product]` holds two tiers, `bundled` and `additional`, under the `dev-build.json`
 * product key, see [DEV_DIST_COMPONENT_TIERS]. A tier maps a plugin's main module to its component label. A
 * platform-specific plugin maps to a dict from host platform to label instead. A plugin is in one tier only. A
 * registry plugin is in no tier.
 *
 * The bundled tier is in composition order: [compositionOrder] first, then the rest in request order. A
 * distribution composes the neutral bundled entries in list order, then the platform-specific entries in list order.
 * The composer writes `plugin-classpath.txt` in that order. The fingerprint hashes that file, so a re-sort is a
 * fingerprint change. A component keeps its label in the recorded form, and [planLabel] spells it for the package of the
 * table, see [DevDistBazelIndex.planLabel].
 *
 * A product of [communityProducts] states only its additional tier. Its distribution composes the bundled tier of the
 * community half, see [devDistCommunityProducts].
 */
internal fun renderPluginComponents(
  products: Map<String, Map<DevDistPluginTier, List<GeneratedPluginComponent>>>,
  planLabel: (String) -> String,
  communityProducts: Set<String>,
  compositionOrder: (String) -> List<String>,
): String = buildString {
  append("DEV_DIST_PLUGIN_COMPONENTS = {\n")
  for ((product, tiers) in products) {
    append("    \"").append(product).append("\": {\n")
    for (tier in DEV_DIST_COMPONENT_TIERS) {
      if (product in communityProducts && tier == DevDistPluginTier.BUNDLED) {
        continue
      }
      val components = tiers.getValue(tier)
      val ordered = if (tier == DevDistPluginTier.BUNDLED) composedBundledComponents(product, compositionOrder(product), components) else components
      append("        \"").append(tier.key).append("\": {\n")
      for (component in ordered) {
        append("            \"").append(component.mainModule).append("\": ")
        val label = component.label
        if (label != null) {
          append("\"").append(planLabel(label)).append("\",\n")
        }
        else {
          append("{\n")
          for ((platform, platformLabel) in component.labels.toSortedMap()) {
            append("                \"").append(platform).append("\": \"").append(planLabel(platformLabel)).append("\",\n")
          }
          append("            },\n")
        }
      }
      append("        },\n")
    }
    append("    },\n")
  }
  append("}\n")
}

/**
 * The bundled components in composition order: [compositionOrder] first, then the rest in entry order. An empty order
 * keeps the registration order. A plan-named plugin is listed once, is a bundled component, and is neutral, because a
 * distribution composes every neutral component before the platform-specific ones.
 */
internal fun composedBundledComponents(
  product: String,
  compositionOrder: List<String>,
  components: List<GeneratedPluginComponent>,
): List<GeneratedPluginComponent> {
  val remaining = components.associateByTo(LinkedHashMap()) { it.mainModule }
  val result = ArrayList<GeneratedPluginComponent>(components.size)
  val listed = HashSet<String>()
  for (mainModule in compositionOrder) {
    check(listed.add(mainModule)) { "Plan-named plugin '$mainModule' of '$product' is listed twice in the composition order" }
    val component = checkNotNull(remaining.remove(mainModule)) { "Plan-named plugin '$mainModule' of '$product' is not a bundled component" }
    check(component.label != null) { "Plan-named plugin '$mainModule' of '$product' is platform-specific and cannot lead the bundled tier" }
    result.add(component)
  }
  result.addAll(remaining.values)
  return result
}

/** [facts] answers the platform members of the product of [request] and the fact of a descriptor, see [PluginPlanFacts]. */
private fun descriptorFacts(
  request: DevDistPluginRequest,
  entry: PluginDescriptorEntry,
  outputProvider: ModuleOutputProvider,
  facts: PluginPlanFacts,
): PluginSymbolicDescriptorFacts {
  val layout = request.layout
  val properties = request.properties
  val platformModules = facts.platformModules(request.product)
  val autoMembers = if (layout.auto) {
    outputProvider.findRequiredModule(layout.mainModule).getProductionModuleDependencies(withTests = false)
      .map { it.moduleReference.moduleName }
      .filterTo(LinkedHashSet()) { it.startsWith("${layout.mainModule.removeSuffix(".plugin")}.") }
  }
  else emptySet()
  val packedElsewhere = autoMembers.filterTo(LinkedHashSet()) { name ->
    name in platformModules || properties.productLayout.pluginLayouts.value.any { other ->
      other !== layout && other.includedModules.any { it.moduleName == name }
    }
  }
  val testModules = testOutputModules(request = request, entry = entry, outputProvider = outputProvider)
  val moduleDescriptors = LinkedHashMap<String, PluginSymbolicModuleDescriptor?>()
  for (content in entry.contentModules) {
    val name = content.name
    if ('/' in name || moduleDescriptors.containsKey(name)) continue
    val module = outputProvider.findRequiredModule(name)
    val file = outputProvider.findFileInModuleSources(
      module = module,
      relativePath = name.replace('/', '.') + ".xml",
      onlyProductionSources = module.name !in testModules,
    )
    moduleDescriptors.put(name, file?.let(facts::moduleDescriptor))
  }
  return PluginSymbolicDescriptorFacts(
    pluginXml = contentDescriptor(entry.contentModules),
    pluginXmlInput = "unbound",
    moduleDescriptors = moduleDescriptors,
    isPluginXmlFinal = true,
    // A frontend product takes the roots too, so it plans every plugin as its monolith does.
    frontendRoots = if (properties.embeddedFrontendRootModule == null && properties.productMode != ProductMode.FRONTEND) emptySet() else frontendIncompatibleRootModuleNames().toSet(),
    packedElsewhere = packedElsewhere,
  )
}

private fun contentDescriptor(contentModules: List<DeclaredContentModule>): String {
  val root = Element("idea-plugin")
  if (contentModules.isNotEmpty()) {
    val content = Element("content")
    for (module in contentModules) {
      val element = Element("module").setAttribute("name", module.name)
      module.loading?.let { element.setAttribute("loading", it) }
      content.addContent(element)
    }
    root.addContent(content)
  }
  return JDOMUtil.writeElement(root)
}

private fun DevDistPluginRequest.propertiesProject(outputProvider: ModuleOutputProvider) =
  outputProvider.findRequiredModule(properties.applicationInfoModule).project

private fun DevDistPluginRequest.nativePolicy(): PluginSymbolicNativePolicy {
  checkNotNull(variant.distribution)
  return PluginSymbolicNativePolicy(
    handlerEnabled = true,
    macSigningEnabled = false,
    signingMode = DEV_DIST_SIGN_NATIVE_FILE_MODE,
    presignedLibraries = LinkedHashMap(properties.presignedNativeLibs),
  )
}
