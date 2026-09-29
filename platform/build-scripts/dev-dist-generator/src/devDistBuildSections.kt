// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicDescriptorFacts
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicNativePolicy
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparationFacts
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import com.intellij.platform.distributionContent.DevDistPlatformJars
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.LinuxLibcImpl
import org.jetbrains.intellij.build.MacLibcImpl
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.WindowsLibcImpl
import org.jetbrains.intellij.build.buildSpan
import org.jetbrains.intellij.build.dev.DevPluginPreparationRecipe
import org.jetbrains.intellij.build.dev.DevPluginResourceExclusions
import org.jetbrains.intellij.build.devDist.ReusableJarArtifact
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.stats.DevDistPlanFileResult
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.jps.model.JpsProject
import java.nio.file.Path
import java.util.Collections
import java.util.IdentityHashMap
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

/**
 * The facts the tool decides for the dev distribution, which the plan reads as labels.
 *
 * [contentModuleJarLabels] records each owner label with its jar artifact and recipe. The artifact is the owner's
 * default output, `<label>.production.jar`.
 * Labels use the spelling of a package outside `community/`. A module absent from the index packs no independent `lib/` jar.
 * [pluginRecords] is the [DevSectionRecord] of every plugin of the population that has a Bazel package, whether or not
 * the plugin renders a section.
 * [population] is [PluginPackingDerivation.population]. All maps and the population are sorted.
 * [frontendRootDescriptorJars] maps each frontend product to the jar of the embedded descriptor module that packs its
 * root descriptor, see [DevDistEmbeddedFrontendClasses].
 */
internal class DevDistToolVerdicts(
  @JvmField val contentModuleJarLabels: Map<String, DevDistModuleJarArtifact>,
  @JvmField val pluginRecords: Map<String, DevSectionRecord>,
  @JvmField val population: Set<String>,
  @JvmField val frontendRootDescriptorJars: Map<String, String> = emptyMap(),
) {
  /**
   * The `dev_dist_plugin_descriptor` label the own package of [mainModule] declares for [variant], or `null` when it
   * declares none.
   *
   * `null` says the section refused, and every refusal has one shape: a community package cannot name what the entry
   * reads. So the plan generator writes a package of its own for such an entry, see [collectCrossHalfDescriptorPackages].
   * Read out of the record and never derived from the target name, because absence is the whole signal.
   */
  fun ownDescriptorTarget(mainModule: String, variant: String): String? {
    return pluginRecords.get(mainModule)?.descriptorTargets?.get(variant)
  }
}

/** One module owner and its exact recipe, derived together from the rendered packing statement. */
internal data class DevDistModuleJarArtifact(
  @JvmField val label: String,
  @JvmField val canonicalArtifact: ReusableJarArtifact,
)

/**
 * The dev-distribution statements of every module's `BUILD.bazel`, rendered as the JPS-to-Bazel converter renders them.
 *
 * [contentModuleJarCalls] holds the `content_module_jar(...)` call of every module that gets one, keyed by module name.
 * Each call is one [Target.render], so it ends with one newline. [devSections] holds the body of the `dev <main module>`
 * section of every plugin that states content or a descriptor, keyed by main module. [loadStatements] holds the load
 * lines each stated module needs in its package. All three maps are sorted. [verdicts] holds the facts of the same
 * computation as labels, for the plan. [descriptorPlans] holds the plan entries the sections read, so the plan reads
 * the same entries and builds none of its own. [residueClasses] holds the residue classes of every (plugin, variant)
 * of those plans, keyed by [planEntryKey], so every home follows one baseline.
 * [pluginPlanRecords] holds only explicitly registered graphs. The owner keeps its index and canonical recipes private.
 */
internal class DevDistBuildSections private constructor(
  @JvmField val contentModuleJarCalls: Map<String, String>,
  private val pendingSections: Map<String, PendingDevSection>,
  private val sectionLoadStatements: TreeMap<String, MutableList<LoadStatement>>,
  @JvmField val verdicts: DevDistToolVerdicts,
  @JvmField val descriptorPlans: List<PluginDescriptorPlan>,
  @JvmField val residueClasses: Map<String, DescriptorResidueClasses>,
  @JvmField val crossHalfDescriptorPackages: CrossHalfDescriptorPackages,
  @JvmField val platformJars: DevDistPlatformJars,
  @JvmField val pluginRequests: List<DevDistPluginRequest>,
  internal val index: DevDistBazelIndex,
  /** The half of the run. Its split products give the product order, and its embedded frontend names the helper rules. */
  internal val half: DevDistHalf,
  /** The statements of the targets that the binder of [half] writes, see [DevDistAssetBinder.sectionStatements]. */
  private val halfStatements: DevDistHalfSectionStatements,
  /** The frontend root descriptor jars of the `dev` section of the embedded descriptor module, see [DevDistEmbeddedFrontendClasses]. */
  private val frontendRootDescriptorJars: List<Target>,
  /** Whether each request is planned on its own too, and checked against the shortcut that planned it, see [computePluginPlan]. */
  @JvmField internal val verifyPlanUnits: Boolean,
  /**
   * The `content_module_jar` call of every community module that the ultimate half packs differently from the community
   * half, keyed by module and sorted. The product package [DEV_DIST_CONTENT_MODULE_JARS_PACKAGE] holds these calls. Only
   * the ultimate half under [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES] has any.
   */
  @JvmField val relocatedContentModuleJarCalls: Map<String, String> = emptyMap(),
  /**
   * The `withResource*` inputs of every layout of the community registry that no product of the run plans, keyed by main
   * module. Only the community half under [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES] has any, because it writes the
   * resource statements of every community package, see [collectRegistryLayoutResourceInputs].
   */
  private val registryResourceInputs: Map<String, List<DevDistPluginRawInput>> = emptyMap(),
) {
  private val canonicalOwnerIndex = DevDistCanonicalOwners(verdicts.contentModuleJarLabels.mapValues { it.value.canonicalArtifact }, index)
  private val completedPluginPlans = LinkedHashMap<DevDistPluginPlanKey, DevDistPluginPlanRecord>()
  private var boundPluginPlanEntries: List<DevDistPluginPlanEntry>? = null
  private val simplePackagings = TreeMap<String, DevDistSimplePackaging?>()
  private val divergentPackagings = TreeMap<String, DevDistSimplePackaging>()
  private val renderedDevSections = TreeMap<String, String>()
  private val renderedResourceFilegroups = TreeMap<String, String>()
  private var resourceStatements: DevDistResourceStatements? = null
  private val pluginExecutionCalls = TreeMap<String, String>()
  private val planFileExports = TreeMap<String, String>()
  private var pluginExecutionsBound = false
  private val originalDescriptorPlans = descriptorPlans.toList()
  private val descriptorDeclarations = IdentityHashMap<PluginDescriptorPlan, OwnedDescriptorPlan>()

  /** The project-wide inputs of each project a plan reads, computed once. A run has one project. */
  private val projectInputs = ConcurrentHashMap<JpsProject, DevDistProjectInputs>()

  /** The plan of each plan unit, see [PluginPlanUnitKey]. The products of one unit share the plan. */
  private val planUnits = ConcurrentHashMap<PluginPlanUnitKey, Lazy<DevDistPluginBuildPlan>>()

  /** The number of plan units the registration computed. The trace states it beside the number of requests. */
  val planUnitCount: Int
    get() = planUnits.size

  init {
    val declarationsByLabel = HashMap<String, DevDistDescriptorDeclaration>()
    for (plan in descriptorPlans) {
      val entries = ArrayList<OwnedDescriptorDeclaration>()
      val keys = HashSet<String>()
      for (entry in plan.plugins) {
        check(keys.add(planEntryKey(entry))) { "Duplicate descriptor entry '${planEntryKey(entry)}' in '${plan.platformPrefix}'" }
        val ownRecord = verdicts.pluginRecords.get(entry.mainModule)
        val ownLabel = ownRecord?.descriptorTargets?.get(entry.variant)
        val shared = crossHalfDescriptorPackages.sharedDeclaration(entry)
        check(ownLabel == null || shared == null) { "Conflicting descriptor declarations for '${planEntryKey(entry)}'" }
        // A divergent product reads its product package. A baseline product reads the plugin's own leaf, or the plugin
        // package when the own package declares none.
        val declaration = crossHalfDescriptorPackages.productDeclaration(plan.platformPrefix, entry) ?: when {
          ownLabel != null -> DevDistDescriptorDeclaration(ownLabel, entry)
          else -> checkNotNull(shared) { "No descriptor declaration for '${planEntryKey(entry)}'" }
        }
        declaration.requireUnchanged(entry)
        declarationsByLabel.putIfAbsent(declaration.label, declaration)?.requireUnchanged(entry)
        entries.add(OwnedDescriptorDeclaration(entry, declaration, ownRecord))
      }
      check(descriptorDeclarations.put(plan, OwnedDescriptorPlan(plan, entries)) == null) { "Duplicate descriptor plan '${plan.platformPrefix}'" }
    }
  }

  /**
   * Resolves a declaration for the original plan and entry of this owner run.
   *
   * A reader calls [requireDescriptorDeclarationsUnchanged] once before its first read. A check here, per read, is
   * quadratic in the number of entries.
   */
  fun descriptorDeclaration(plan: PluginDescriptorPlan, entry: PluginDescriptorEntry): DevDistDescriptorDeclaration {
    val original = requireNotNull(descriptorDeclarations.get(plan)) { "The descriptor plan belongs to another owner run" }
    val owned = requireNotNull(original.entries.singleOrNull { it.entry === entry }) { "The descriptor entry does not belong to this plan" }
    return owned.declaration
  }

  /** Validates the original plans, entries, and target records before a consumer reads cached descriptor declarations. */
  fun requireDescriptorDeclarationsUnchanged() {
    check(descriptorPlans.size == originalDescriptorPlans.size && descriptorPlans.indices.all { descriptorPlans[it] === originalDescriptorPlans[it] }) {
      "The descriptor plans changed after their declarations"
    }
    for (plan in originalDescriptorPlans) {
      val original = descriptorDeclarations.getValue(plan)
      original.requireUnchanged(plan)
      for (record in original.entries) {
        record.declaration.requireUnchanged(record.entry)
        val current = verdicts.pluginRecords.get(record.entry.mainModule)
        check(current === record.ownRecord && current?.descriptorTargets == record.ownTargets) {
          "The descriptor record for '${planEntryKey(record.entry)}' changed after its declaration"
        }
      }
    }
  }

  private class OwnedDescriptorPlan(
    plan: PluginDescriptorPlan,
    val entries: List<OwnedDescriptorDeclaration>,
  ) {
    private val platformPrefix = plan.platformPrefix
    private val releaseDate = plan.releaseDate
    private val releaseVersion = plan.releaseVersion
    private val eap = plan.eap

    fun requireUnchanged(plan: PluginDescriptorPlan) {
      check(plan.platformPrefix == platformPrefix && plan.releaseDate == releaseDate && plan.releaseVersion == releaseVersion && plan.eap == eap &&
            plan.plugins.size == entries.size && plan.plugins.indices.all { plan.plugins[it] === entries[it].entry }) {
        "The descriptor plan '$platformPrefix' changed after its declarations"
      }
    }
  }

  private class OwnedDescriptorDeclaration(
    val entry: PluginDescriptorEntry,
    val declaration: DevDistDescriptorDeclaration,
    val ownRecord: DevSectionRecord?,
  ) {
    val ownTargets: Map<String, String>? = ownRecord?.descriptorTargets?.toMap()
  }

  /** The load lines each stated module needs in its package, keyed by module and sorted. */
  val loadStatements: Map<String, List<LoadStatement>>
    get() = Collections.unmodifiableMap(sectionLoadStatements)

  /** A read-only snapshot for the content-set generator. Registration does not change rendered sections. */
  val pluginPlanRecords: Map<DevDistPluginPlanKey, DevDistPluginPlanRecord>
    get() = Collections.unmodifiableMap(LinkedHashMap(completedPluginPlans))

  /** The folded plans, one entry per product and plugin. Bound once, after every plan is registered. */
  val pluginPlanEntries: List<DevDistPluginPlanEntry>
    get() = checkNotNull(boundPluginPlanEntries) { "The plugin plan entries are not bound yet" }

  /** The body of the `dev <main module>` section of every plugin that states content or a descriptor, keyed by main module. */
  val devSections: Map<String, String>
    get() {
      check(boundPluginPlanEntries != null) { "The dev sections render after the plugin plan entries are bound" }
      return Collections.unmodifiableMap(renderedDevSections)
    }

  /** The mode of every product of the run, see [PluginDescriptorPlan.mode], sorted. */
  val productModes: Set<String>
    get() = descriptorPlans.mapTo(TreeSet()) { it.mode }

  /**
   * The body of the `dev` section of [mainModule] with only the mode refusals of [modes], or `null` when the plugin
   * states no section. Another run compares its own section with it, see [foreignDevSections].
   */
  fun devSectionBody(mainModule: String, modes: Set<String>): String? {
    check(boundPluginPlanEntries != null) { "The dev sections render after the plugin plan entries are bound" }
    return pendingSections.get(mainModule)?.render(simplePackagings.get(mainModule), modes)?.body
  }

  /**
   * Binds the folded entries, classifies each plugin's packaging, and renders every dev section.
   *
   * A plugin is simple in every product or in none, and the products of the baseline residue class decide. They share
   * the packaging the plugin's own section states, so two of them that plan the plugin differently stop the run. A
   * product outside the baseline class states its own packaging in its product package, see [DescriptorResidueClasses]:
   * the frontend filter of a product without an embedded frontend packs a content module into the main jar that another
   * product packs into a jar of its own. Such a product keeps the plan file when the baseline class does, and stops the
   * run when only the baseline class packs the plugin as a simple plugin. The product mode changes no packaging: a
   * frontend product packs the plugin as its monolith does, and `dev_plugin` places no jar of a module the mode refuses.
   */
  fun bindPluginPlanEntries(entries: List<DevDistPluginPlanEntry>) {
    check(boundPluginPlanEntries == null) { "The plugin plan entries are already bound" }
    val expectedKeys = entries.flatMapTo(HashSet()) { entry -> entry.records.keys.map(entry::key) }
    check(expectedKeys == completedPluginPlans.keys) { "The folded entries do not cover the registered plans" }
    boundPluginPlanEntries = java.util.List.copyOf(entries)
    val classified = entries.map { entry ->
      val draft = pendingSections.get(entry.mainModule)
      // A plugin without a neutral descriptor entry has no residue classes. Treat every product as baseline so the
      // first pass still decides packaging; non-neutral plans stay on the plan file.
      val baseline = residueClasses.get(entry.mainModule)?.isBaseline(entry.product) ?: true
      val packaging = classifySimplePluginPackaging(
        entry = entry,
        descriptorInput = devDistDescriptorInputId(entry.mainModule),
        contentModuleNames = draft?.contentModuleNames.orEmpty(),
        ownDescriptorDeclared = verdicts.ownDescriptorTarget(entry.mainModule, "") != null,
        contentModuleJarModules = verdicts.contentModuleJarLabels.keys,
        baseline = baseline,
        refusedContentModules = draft?.refusedContentModules.orEmpty(),
        index = index,
        relocatedModules = relocatedContentModuleJarCalls.keys,
        contentModuleJarLabel = ::contentModuleJarLabel,
      )
      ClassifiedPluginPlanEntry(entry = entry, baseline = baseline, packaging = packaging)
    }
    // The first product of the baseline class, in product order, decides whether the plugin is simple. Every other
    // product follows that tier. A product that packs the simple plugin differently states its own `dev_plugin` in a
    // product package, and products that pack alike share it, see `devPluginPackageProduct`. A plugin that keeps the
    // plan file gets a chain class for each differing plan text instead, see `renderGeneratedDevDistPluginExecutions`.
    val rank = half.splitProducts.withIndex().associate { (index, product) -> product to index }
    val ordered = classified.sortedBy { rank.get(it.entry.product) ?: Int.MAX_VALUE }
    val byPlugin = TreeMap<String, DevDistSimplePackaging?>()
    for ((entry, baseline, packaging) in ordered) {
      if (baseline && entry.mainModule !in byPlugin) {
        byPlugin.put(entry.mainModule, packaging)
      }
    }
    for ((entry, _, packaging) in ordered) {
      check(entry.mainModule in byPlugin) { "Plugin '${entry.mainModule}' has no entry of a baseline product" }
      val first = byPlugin.get(entry.mainModule) ?: continue
      checkNotNull(packaging) {
        "Two products plan '${entry.mainModule}' differently: '${entry.product}' keeps the plan file, and the baseline class packs it as a simple plugin"
      }
      if (packagingDifference(first, packaging) == null) continue
      check(divergentPackagings.put(divergentPackagingKey(entry.mainModule, entry.product), packaging) == null) {
        "Two plans of '${entry.product}' pack '${entry.mainModule}'"
      }
    }
    simplePackagings.putAll(byPlugin)
    for ((mainModule, draft) in pendingSections) {
      renderedDevSections.put(mainModule, draft.render(simplePackagings.get(mainModule)).body)
    }
    renderedResourceFilegroups.putAll(renderResourceFilegroups())
  }

  /**
   * The resource statements of every package a plan names, keyed by the module whose section carries them.
   *
   * The plan of every registered plugin takes part, whatever its tier. A complex plugin's chain reads the inputs through
   * its plan file, and a simple plugin's `dev_plugin` reads them through its `files`, so both need the same statements.
   */
  private fun renderResourceFilegroups(): Map<String, String> {
    val statements = DevDistResourceStatements()
    for ((absolutePackage, packageRelativePath) in halfStatements.resourceDirectories) {
      statements.addDirectory(absolutePackage = absolutePackage, packageRelativePath = packageRelativePath, requester = "the ${half.name} asset binder")
    }
    for ((key, record) in completedPluginPlans) {
      statements.addPlanInputs(plugin = key.plugin, inputs = record.plan.requiredRawInputs)
    }
    for ((plugin, inputs) in registryResourceInputs) {
      statements.addPlanInputs(plugin = plugin, inputs = inputs)
    }
    resourceStatements = statements
    return statements.render(index)
  }

  /** Whether a plan names a resource filegroup or an exported resource file that the section of [module] declares. */
  fun ownsResourceFilegroup(module: String): Boolean = renderedResourceFilegroups.containsKey(module)

  /**
   * Fails when a plan of this run names a resource filegroup or an exported file of a community package that the section
   * of [upstream] does not declare. The ultimate half under [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES] writes no
   * community section, so the community half must declare every such statement. The message names the package, the
   * missing statements and the layouts that need them.
   */
  fun requireResourcesDeclaredBy(upstream: DevDistBuildSections) {
    val own = checkNotNull(resourceStatements) { "The resource statements render after the plugin plan entries are bound" }
    val declared = checkNotNull(upstream.resourceStatements) { "The upstream resource statements are not rendered" }
    val missing = TreeMap<String, List<String>>()
    for (absolutePackage in own.packages) {
      val owner = index.modulesInPackage(absolutePackage).firstOrNull() ?: continue
      if (index.isCommunity(owner) != true) continue
      val gaps = own.missingIn(declared, absolutePackage)
      if (gaps.isNotEmpty()) missing.put(absolutePackage, gaps)
    }
    check(missing.isEmpty()) {
      missing.entries.joinToString(
        prefix = "The ${half.name} half needs resource statements of community packages that the ${upstream.half.name} half does not declare:\n",
        separator = "\n",
      ) { (absolutePackage, gaps) -> "$absolutePackage: $gaps, for the layouts ${own.requesters(absolutePackage)}" }
    }
  }

  /**
   * The label of the `content_module_jar` target of [module] as the plans of this run name it: a label of the product
   * package for a relocated call, see [relocatedContentModuleJarCalls], and the label of the own package otherwise.
   */
  fun contentModuleJarLabel(module: String): String? {
    return verdicts.contentModuleJarLabels.get(module)?.label ?: index.contentModuleJarLabel(module, dependentIsCommunity = index.planPackageIsCommunity)
  }

  /**
   * Binds the rendered plugin executions to the sections, once, before the first [sectionBody] read.
   *
   * The `dev` section of a complex plugin holds its own `dev_dist_complex_plugin` calls after the `dev_dist_plugin`
   * call, and its package loads the macro in the spelling of its half. The `dev` section of a community complex plugin
   * exports its plan files with `exports_files` when a call in the cross-half package reads one. A cross-half call is not
   * bound here: the plan generator writes it into the cross-half package, see [CrossHalfDescriptorPackages].
   */
  fun bindPluginExecutions(rendering: DevDistPluginExecutionRendering, files: DevDistPluginPlanFiles) {
    check(boundPluginPlanEntries != null) { "The plugin executions bind after the plugin plan entries" }
    check(!pluginExecutionsBound) { "The plugin executions are already bound" }
    for ((mainModule, call) in rendering.calls) {
      check(simplePackaging(mainModule) == null) { "Plugin '$mainModule' has a simple packaging and a complex plugin call" }
      val text = call.sectionText ?: continue
      val isCommunity = index.isCommunity(mainModule) ?: error("Plugin '$mainModule' has no Bazel package, so its section cannot hold a call")
      pluginExecutionCalls.put(mainModule, text)
      val loads = sectionLoadStatements.computeIfAbsent(mainModule) { ArrayList() }
      val remainderRule = if (isCommunity) "//" + DEV_PLUGIN_REMAINDER_RULE.removePrefix(COMMUNITY_REPOSITORY_PREFIX) else DEV_PLUGIN_REMAINDER_RULE
      loads.add(LoadStatement(remainderRule, listOf("dev_dist_complex_plugin")))
      // A divergent product that embeds the frontend declares its helper targets beside the complex plugin call.
      if (mainModule == half.embeddedFrontend?.pluginMainModule && "dev_dist_embedded_product_descriptor(" in text) {
        loads.add(LoadStatement(DEV_DIST_EMBEDDED_PRODUCT_DESCRIPTOR_RULE, listOf("dev_dist_embedded_product_descriptor")))
        loads.add(LoadStatement(DEV_DIST_FRONTEND_APPLICATION_INFO_RULE, listOf("dev_dist_frontend_application_info")))
      }
    }
    for ((mainModule, fileNames) in files.exportedFiles) {
      val call = checkNotNull(rendering.calls.get(mainModule)) { "Plugin '$mainModule' has plan files and no call" }
      if (!call.exportsPlanFiles) continue
      check(files.home(mainModule).exportsPlanFiles) { "Plugin '$mainModule' exports plan files outside a community plan home" }
      check(fileNames.isNotEmpty()) { "Plugin '$mainModule' exports no plan file" }
      planFileExports.put(mainModule, renderResourceFileExports(fileNames))
    }
    pluginExecutionsBound = true
  }

  /**
   * Whether the section of [module] holds a statement that only this half states: a target of the binder of [half] or a
   * frontend root descriptor jar.
   */
  fun ownsHalfStatements(module: String): Boolean {
    return halfStatements.statements.get(module).orEmpty().isNotEmpty() ||
           module == half.embeddedFrontend?.descriptorModule && frontendRootDescriptorJars.isNotEmpty()
  }

  /** Whether the section of [module] holds a `dev_dist_complex_plugin` call or exports a plan file. */
  fun ownsPluginExecution(module: String): Boolean {
    check(pluginExecutionsBound) { "The plugin executions are not bound yet" }
    return pluginExecutionCalls.containsKey(module) || planFileExports.containsKey(module)
  }

  /**
   * The simple packaging of [mainModule] that its own section states, or `null` for a plugin that keeps its plan file.
   * The products of the baseline residue class read it; a divergent product reads [simplePackaging] with its name.
   */
  fun simplePackaging(mainModule: String): DevDistSimplePackaging? {
    check(boundPluginPlanEntries != null) { "The plugin plan entries are not bound yet" }
    return simplePackagings.get(mainModule)
  }

  /**
   * The simple packaging of [mainModule] for [product]: the one its product package states when the product packs the
   * plugin differently from the first product of the baseline class, else the one of its own section. `null` for a
   * plugin that keeps its plan file.
   */
  fun simplePackaging(mainModule: String, product: String): DevDistSimplePackaging? {
    val baseline = simplePackaging(mainModule) ?: return null
    return divergentPackagings.get(divergentPackagingKey(mainModule, product)) ?: baseline
  }

  /** The residue classes of the neutral entry of [mainModule], which is the entry a simple plugin's `dev_plugin` reads. */
  fun neutralResidueClasses(mainModule: String): DescriptorResidueClasses {
    return checkNotNull(residueClasses.get(mainModule)) { "Plugin '$mainModule' has no neutral descriptor entry" }
  }

  /**
   * The `dev_plugin` label of a simple plugin for [product], in the ultimate spelling the component index uses.
   *
   * A product that reads another descriptor leaf or packs the plugin differently reads a product package, see
   * [devPluginPackageProduct]. That package states the product's packaging over the leaf of the product's class.
   */
  fun devPluginLabel(packaging: DevDistSimplePackaging, product: String): String {
    val name = "${packaging.mainModule}$DEV_PLUGIN_TARGET_SUFFIX"
    if (readsProductPackage(packaging.mainModule, product)) {
      return crossHalfPackageLabel(mainModule = packaging.mainModule, product = devPluginPackageProduct(packaging.mainModule, product), name = name)
    }
    if (packaging.crossHalf) {
      return crossHalfPackageLabel(mainModule = packaging.mainModule, product = null, name = name)
    }
    val prefix = index.bazelPackagePrefix(packaging.mainModule) ?: error("Plugin '${packaging.mainModule}' has no Bazel package prefix")
    return "$prefix:$name"
  }

  /**
   * Whether [product] reads the `dev_plugin` of [mainModule] from a product package: it is outside the baseline residue
   * class, so it reads another leaf, or it packs the plugin differently from the first product of the baseline class.
   */
  fun readsProductPackage(mainModule: String, product: String): Boolean {
    return !neutralResidueClasses(mainModule).isBaseline(product) || divergentPackagings.containsKey(divergentPackagingKey(mainModule, product))
  }

  /**
   * The product whose product package holds the `dev_plugin` of [mainModule] for [product], which [readsProductPackage].
   *
   * That is the first product in product order that reads the same descriptor leaf and packs the plugin alike, so one
   * target serves them all, as a divergent class shares its home ([DescriptorResidueClasses.home]).
   */
  fun devPluginPackageProduct(mainModule: String, product: String): String {
    val classes = neutralResidueClasses(mainModule)
    val leafHome = classes.home(product)
    val packaging = checkNotNull(simplePackaging(mainModule, product)) { "Plugin '$mainModule' has no simple packaging for '$product'" }
    for (candidate in half.splitProducts) {
      if (candidate == product) return product
      if (!classes.plans(candidate) || classes.home(candidate) != leafHome) continue
      val other = checkNotNull(simplePackaging(mainModule, candidate)) { "Plugin '$mainModule' has no simple packaging for '$candidate'" }
      if (packagingDifference(other, packaging) == null) return candidate
    }
    return product
  }

  /**
   * Computes one private plan from the original layout and fully bound execution facts, and registers nothing.
   * The owner supplies the index and canonical recipes from its own rendering run.
   * A distribution must use its operating system's built-in libc enum so the retained variant cannot change.
   * A complete [preparationRecipe] must match every selected preparation. A null recipe retains an inert record.
   * The generator computes the plans of different plugins beside each other, then registers each one with
   * [registerPluginPlan] after it binds all callback inputs.
   *
   * The plan itself belongs to a plan unit, see [PluginPlanUnitKey]. The first product of a unit computes it, and every
   * other product of the unit reads it. The record stays per product, over the product's own layout. A run with
   * `verifyPlanUnits` computes the plan of every request too, and fails when it differs from the plan of its unit.
   */
  fun computePluginPlan(
    product: String,
    layout: PluginLayout,
    project: JpsProject,
    descriptorFacts: PluginSymbolicDescriptorFacts,
    preparationFacts: PluginSymbolicPreparationFacts,
    variant: PluginSymbolicVariant,
    catalogueFacts: DevDistPluginCatalogueFacts,
    nativePolicy: PluginSymbolicNativePolicy?,
    preparationRecipe: DevPluginPreparationRecipe? = null,
    preparationRecipeFactory: ((DevDistPluginBuildPlan) -> DevPluginPreparationRecipe)? = null,
  ): DevDistPluginPlanRecord {
    require(product.isNotBlank()) { "A plugin plan requires a product identity" }
    val originalLayout = DevDistPluginOriginalLayout(layout)
    variant.distribution?.let { distribution ->
      val supportedLibc = when (distribution.os) {
        OsFamily.MACOS -> distribution.libcImpl is MacLibcImpl
        OsFamily.WINDOWS -> distribution.libcImpl is WindowsLibcImpl
        OsFamily.LINUX -> distribution.libcImpl is LinuxLibcImpl
      }
      require(supportedLibc) { "Plugin variant '${variant.id}' requires a built-in libc implementation for ${distribution.os}" }
    }
    val inputs = projectInputs.computeIfAbsent(project) { DevDistProjectInputs(it, index) }
    fun computePlan(): DevDistPluginBuildPlan {
      return snapshotDevDistPluginPlan(computeDevDistPluginBuildPlan(
        layout = layout,
        project = project,
        index = index,
        descriptorFacts = descriptorFacts,
        preparationFacts = preparationFacts,
        variant = variant,
        catalogueFacts = catalogueFacts,
        nativePolicy = nativePolicy,
        canonicalOwnerIndex = canonicalOwnerIndex,
        projectInputs = inputs,
      ))
    }
    val key = PluginPlanUnitKey(
      layout = planUnitLayoutKey(layout),
      project = project,
      descriptorFacts = descriptorFacts,
      preparationFacts = preparationFacts,
      variant = variant,
      catalogueFacts = catalogueFacts,
      nativePolicy = nativePolicy,
    )
    val plan = planUnits.computeIfAbsent(key) { lazy(::computePlan) }.value
    if (verifyPlanUnits) {
      check(hasSameBuildPlan(plan, computePlan())) {
        "The plan unit of '${layout.mainModule}' on '${variant.id}' differs from the plan of '$product'. The unit key misses an input."
      }
    }
    originalLayout.requireUnchanged(layout)
    require(preparationRecipe == null || preparationRecipeFactory == null) {
      "A plugin plan must declare one preparation recipe source"
    }
    val selectedRecipe = preparationRecipeFactory?.invoke(plan) ?: preparationRecipe
    return DevDistPluginPlanRecord(variant, plan, selectedRecipe, originalLayout)
  }

  /**
   * Registers [record] under [key]. A repeated graph returns the existing record. A conflicting graph changes no record.
   */
  fun registerPluginPlan(key: DevDistPluginPlanKey, record: DevDistPluginPlanRecord): DevDistPluginPlanRecord {
    val previous = completedPluginPlans.get(key)
    check(previous == null || previous.hasSameGraph(record)) { "Conflicting plugin plan for $key" }
    if (previous != null) {
      previous.requireSameOriginalLayoutState(record)
      return previous
    }
    completedPluginPlans.put(key, record)
    return record
  }

  /**
   * The body of the `dev <module>` section of [module], or `null` when the module states nothing.
   *
   * The call comes first, then the resource filegroup, then the plugin section, then the `dev_dist_complex_plugin`
   * calls of an ultimate complex plugin or the plan file exports of a community one. The statements of the half follow,
   * and the frontend root descriptor jars of the embedded descriptor module come last. One blank line separates the
   * statements, as `BuildFile.render` separates targets. [bindPluginExecutions] runs before the first read.
   */
  fun sectionBody(module: String): String? {
    check(pluginExecutionsBound) { "The dev sections render after the plugin executions are bound" }
    val call = contentModuleJarCalls.get(module)
    val filegroup = renderedResourceFilegroups.get(module)
    val section = devSections.get(module)
    val complexPluginCalls = pluginExecutionCalls.get(module)
    val exports = planFileExports.get(module)
    val frontendRootDescriptors = frontendRootDescriptorJars.takeIf { module == half.embeddedFrontend?.descriptorModule && it.isNotEmpty() }
      ?.joinToString("\n") { it.render() }
    val statements = buildList {
      addAll(listOfNotNull(call, filegroup, section, complexPluginCalls, exports))
      addAll(halfStatements.statements.get(module).orEmpty())
      frontendRootDescriptors?.let(::add)
    }
    if (statements.isEmpty()) {
      return null
    }
    return statements.joinToString(separator = "\n\n") { it.trim() }
  }

  companion object {
    fun compute(
      projectRoot: Path,
      outputProvider: ModuleOutputProvider,
      products: List<DiscoveredProduct>,
      walk: DescriptorWalk,
      derivation: PluginPackingDerivation,
      sourceIndex: DevDistBazelIndex,
      files: DevDistBuildFiles,
      half: DevDistHalf,
      testPlugins: List<TestPluginSpec>,
      verifyPlanUnits: Boolean = false,
      foreignSections: Set<String> = emptySet(),
      writtenContentModuleJarModules: Set<String>? = null,
      ownership: DevDistOwnership = DevDistOwnership.DEFAULT,
      upstream: DevDistBuildSections? = null,
    ): DevDistBuildSections {
      val index = snapshotDevDistBazelIndex(sourceIndex)

      // Every rejection site stops the run, for a bundled and an additional plugin alike. The run-configuration reader
      // rejects a module the project does not have or that has no plugin descriptor. The request enumeration rejects a
      // module without a layout or a platform variant. The layout bindings reject an undeclared callback or resource.
      // The descriptor plan rejects a layout fact that is code.
      val additionalModulesByProduct = derivation.runConfigurations.modulesByProduct
      val pluginRequests = buildSpan("dev sections: plugin requests") { span ->
        enumerateGeneratedDevDistPluginRequests(
          products = products,
          outputProvider = outputProvider,
          additionalModulesByProduct = additionalModulesByProduct,
          half = half,
          testPlugins = testPlugins,
        ).also { span.setAttribute("count", it.size.toLong()) }
      }
      fun testPluginsByProduct(requests: List<DevDistPluginRequest>): Map<String, Map<String, TestPluginSpec>> {
        return requests.groupBy(DevDistPluginRequest::product).mapValues { (_, ofProduct) ->
          val result = LinkedHashMap<String, TestPluginSpec>()
          for (request in ofProduct) {
            val testPlugin = request.testPlugin ?: continue
            val previous = result.putIfAbsent(request.layout.mainModule, testPlugin)
            check(previous == null || previous == testPlugin) {
              "Plugin '${request.layout.mainModule}' has different Product DSL test-plugin specifications"
            }
          }
          result
        }
      }

      // Before the layout bindings, because a layout that embeds the frontend names the descriptor action of its home.
      val embeddedFrontend = half.embeddedFrontend
      val embeddedClasses = if (embeddedFrontend == null) {
        NoDevDistEmbeddedFrontendClasses
      }
      else {
        buildSpan("dev sections: embedded descriptor classes") {
          embeddedFrontend.collectClasses(
            products = products,
            embeddingProducts = pluginRequests.filter { embeddedFrontend.packsEmbeddedFrontend(it.layout) }.mapTo(HashSet()) { it.product },
            productOrder = half.splitProducts,
            outputProvider = outputProvider,
          )
        }
      }

      val layoutBindings = buildSpan("dev sections: layout bindings") { span ->
        bindGeneratedDevDistPluginLayouts(pluginRequests, index, outputProvider, half, embeddedHomeOf = embeddedClasses::home)
          .also { span.setAttribute("count", it.size.toLong()) }
      }
      val halfStatements = half.assetBinder.sectionStatements(pluginRequests, index)
      // A half that writes the community packages declares the resources of every layout of its registry, so that a plan
      // of the other half can name them.
      val registryResourceInputs = if (ownership == DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES && index.planPackageIsCommunity) {
        buildSpan("dev sections: registry layout resources") {
          collectRegistryLayoutResourceInputs(products = products, requests = pluginRequests, index = index, outputProvider = outputProvider, half = half)
        }
      }
      else {
        emptyMap()
      }
      val plans = buildSpan("dev sections: descriptor plans") {
        collectPluginDescriptorPlans(
          walk = walk,
          projectRoot = projectRoot,
          outputProvider = outputProvider,
          products = products,
          targets = index.targets,
          requestLayoutsByProduct = pluginRequests.groupBy(DevDistPluginRequest::product).mapValues { (_, ofProduct) ->
            val seen = Collections.newSetFromMap(IdentityHashMap<PluginLayout, Boolean>())
            ofProduct.mapNotNull { request -> if (seen.add(request.layout)) request.layout else null }
          },
          testPluginsByProduct = testPluginsByProduct(pluginRequests),
          half = half,
          embeddedClasses = embeddedClasses,
          everyStatedMode = ownership == DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES,
        )
      }

      val candidacy = buildSpan("dev sections: content module jar candidacy") {
        deriveContentModuleJarCandidacy(
          pluginCandidacies = derivation.plugins.map { it.packing.candidacy },
          contentVetoes = collectContentVetoModules(products),
          platform = derivation.platformJars,
        )
      }
      files.preload(candidacy.candidates.keys.mapNotNullTo(LinkedHashSet(), files::buildFile))
      val statements = ContentModuleJarStatements(
        candidacy = candidacy,
        outputProvider = outputProvider,
        index = index,
        isBuildSectionSkipped = files::isBuildSectionSkipped,
      )
      val contentModuleJarCalls = TreeMap<String, String>()
      val relocatedCalls = TreeMap<String, String>()
      val contentModuleJarLabels = TreeMap<String, DevDistModuleJarArtifact>()
      val loadStatements = TreeMap<String, MutableList<LoadStatement>>()
      for ((module, loads) in halfStatements.loadStatements) {
        loadStatements.computeIfAbsent(module) { ArrayList() }.addAll(loads)
      }
      buildSpan("dev sections: content module jar calls") {
        for (module in statements.modules) {
          // The community pass names only a call that the ultimate pass writes. It packs any other module in the plugin.
          if (writtenContentModuleJarModules != null && module !in writtenContentModuleJarModules) {
            continue
          }
          val call = statements.renderFor(module) ?: continue
          contentModuleJarCalls.put(module, call)
          loadStatements.computeIfAbsent(module) { ArrayList() }.add(statements.loadStatement(module))
          // The ultimate half reuses the call of the community half when the texts are equal. It writes any other call of
          // a community module into its product package, see `relocatedContentModuleJarCall`.
          val relocated = isRelocatedContentModuleJarCall(module, call, upstream?.contentModuleJarCalls, index)
          if (upstream != null && relocated) {
            val reason = if (upstream.contentModuleJarCalls.containsKey(module)) "the community half states another call" else "the community half states no call"
            println("relocated the content_module_jar call of $module: $reason")
            relocatedCalls.put(module, relocatedContentModuleJarCall(module, checkNotNull(statements.computeJar(module)), index))
          }
          // As a plan package of this pass writes it, which is the spelling the plan files of the pass need.
          val label = if (relocated) {
            relocatedContentModuleJarLabel(module)
          }
          else {
            index.contentModuleJarLabel(module = module, dependentIsCommunity = index.planPackageIsCommunity)
            ?: error("Module '$module' gets a content_module_jar call and has no label")
          }
          val jar = checkNotNull(statements.computeJar(module))
          contentModuleJarLabels.put(module, DevDistModuleJarArtifact(
            label = label,
            canonicalArtifact = ReusableJarArtifact(module = module, recipe = snapshotDevDistJarRecipe(jar.recipe)),
          ))
        }
      }

      // The section of a plugin states the baseline entry of every variant. A divergent product's entry renders into
      // its product package instead, see `collectCrossHalfDescriptorPackages`.
      val residueClasses = buildSpan("dev sections: descriptor residue classes") {
        computeDescriptorResidueClasses(plans = plans, productOrder = half.splitProducts)
      }
      val descriptorEntries = HashMap<String, MutableList<PluginDescriptorEntry>>()
      for (classes in residueClasses.values) {
        val leaf = classes.baseline
        descriptorEntries.computeIfAbsent(leaf.mainModule) { ArrayList() }.add(leaf)
      }
      val pendingSections = TreeMap<String, PendingDevSection>()
      val pluginRecords = TreeMap<String, DevSectionRecord>()
      buildSpan("dev sections: plugin sections") {
        for (plugin in derivation.plugins) {
          val mainModule = plugin.mainModule
          if (index.location(mainModule) == null) {
            continue
          }
          val outcome = computeDevSection(
            mainModule = mainModule,
            packing = plugin.packing,
            descriptorEntries = descriptorEntries.get(mainModule).orEmpty(),
            index = index,
            outputProvider = outputProvider,
          )
          // The pass cannot read the section of a foreign plugin, so the plugin declares no leaf of its own here. Its
          // leaf and its `dev_plugin` go to the generated plugin package, see `collectCrossHalfDescriptorPackages`.
          val record = if (mainModule in foreignSections) DevSectionRecord(descriptorTargets = emptyMap()) else outcome.record
          check(pluginRecords.put(mainModule, record) == null) { "Duplicate plugin declaration '$mainModule'" }
          val draft = outcome.draft ?: continue
          pendingSections.put(mainModule, draft)
          loadStatements.computeIfAbsent(mainModule) { ArrayList() }.addAll(draft.loadStatements)
        }
      }
      val verdicts = DevDistToolVerdicts(
        contentModuleJarLabels = Collections.unmodifiableMap(contentModuleJarLabels),
        pluginRecords = pluginRecords,
        population = derivation.population,
        frontendRootDescriptorJars = embeddedClasses.frontendRootDescriptorJars(),
      )
      val crossHalfDescriptorPackages = buildSpan("dev sections: cross-half descriptor packages") {
        collectCrossHalfDescriptorPackages(verdicts = verdicts, classes = residueClasses)
      }
      val result = DevDistBuildSections(
        contentModuleJarCalls = contentModuleJarCalls,
        pendingSections = pendingSections,
        sectionLoadStatements = loadStatements,
        verdicts = verdicts,
        descriptorPlans = plans,
        residueClasses = residueClasses,
        crossHalfDescriptorPackages = crossHalfDescriptorPackages,
        platformJars = derivation.platformJars,
        pluginRequests = java.util.List.copyOf(pluginRequests),
        index = index,
        half = half,
        halfStatements = halfStatements,
        frontendRootDescriptorJars = embeddedClasses.renderFrontendRootDescriptorJars(),
        verifyPlanUnits = verifyPlanUnits,
        relocatedContentModuleJarCalls = Collections.unmodifiableMap(relocatedCalls),
        registryResourceInputs = registryResourceInputs,
      )
      buildSpan("dev sections: register plugin plans") { span ->
        registerGeneratedDevDistPluginPlans(
          owner = result,
          outputProvider = outputProvider,
          layoutBindings = layoutBindings,
          hasPackageAttribute = walk.collector::hasPackageAttribute,
        )
        span.setAttribute("requests", pluginRequests.size.toLong())
        span.setAttribute("planUnits", result.planUnitCount.toLong())
      }
      return result
    }
  }
}

/** The key of a product's own packaging: the plugin and the product, which together name its product package. */
private fun divergentPackagingKey(mainModule: String, product: String): String = "$mainModule/$product"

/** One folded entry with its classification. [baseline] says whether [entry]'s product is in the baseline residue class. */
private data class ClassifiedPluginPlanEntry(
  @JvmField val entry: DevDistPluginPlanEntry,
  @JvmField val baseline: Boolean,
  @JvmField val packaging: DevDistSimplePackaging?,
)

/** The first packaging fact that differs between two classifications of one plugin, or `null` when they agree. */
private fun packagingDifference(first: DevDistSimplePackaging, second: DevDistSimplePackaging): String? {
  val facts = listOf<Triple<String, Any?, Any?>>(
    Triple("mainModule", first.mainModule, second.mainModule),
    Triple("pluginDirectory", first.pluginDirectory, second.pluginDirectory),
    Triple("jars", first.jars, second.jars),
    Triple("moduleJarPaths", first.moduleJarPaths, second.moduleJarPaths),
    Triple("reusedModules", first.reusedModules, second.reusedModules),
    Triple("crossHalf", first.crossHalf, second.crossHalf),
    Triple("classpathJars", first.classpathJars, second.classpathJars),
    Triple("files", first.files, second.files),
    Triple("filePrefixes", first.filePrefixes, second.filePrefixes),
    Triple("executableFiles", first.executableFiles, second.executableFiles),
  )
  val (name, a, b) = facts.firstOrNull { (_, a, b) -> a != b } ?: return null
  return "$name: $a vs $b"
}

/** The name suffix of a `dev_plugin` target. `dev_dist_plugin.bzl` derives the same name for a plugin's own section. */
internal const val DEV_PLUGIN_TARGET_SUFFIX: String = "_dev_plugin"

/** The `.bzl` file that exports `dev_dist_complex_plugin`. The `dev` section of an ultimate complex plugin loads it. */
internal const val DEV_PLUGIN_REMAINDER_RULE: String = "@community//platform/build-scripts/bazel-rules:dev_plugin_remainder.bzl"

/** The `.bzl` file that exports `dev_dist_embedded_product_descriptor` for the helper of a divergent product that embeds the frontend. */
internal const val DEV_DIST_EMBEDDED_PRODUCT_DESCRIPTOR_RULE: String =
  "@community//platform/build-scripts/bazel-rules:dev_dist_embedded_product_descriptor.bzl"

/** The `.bzl` file that exports `dev_dist_frontend_application_info` for the helper of a divergent product that embeds the frontend. */
internal const val DEV_DIST_FRONTEND_APPLICATION_INFO_RULE: String =
  "@community//platform/build-scripts/bazel-rules:dev_dist_frontend_application_info.bzl"

/**
 * The directory of one generated Bazel package per cross-half plugin, and of one per divergent (plugin, product).
 * `devDistCrossHalfDescriptorPackage.kt` writes them.
 */
internal const val CROSS_HALF_PACKAGE_ROOT: String = "build/dev-dist-descriptors"

/**
 * Renders the `content_module_jar` call and the `dev` section of every module the converter writes them for.
 *
 * [derivation] is the one [derivePluginPackings] result of the run, so a plugin has one
 * [com.intellij.platform.buildScripts.pluginModelTool.DerivedPluginPacking] here and in the plugin table.
 * The labels come from `build/bazel-targets.json` through [index]. The descriptor half reads the plan entries
 * [collectPluginDescriptorPlans] builds for the split products over [walk], which is the one descriptor walk of the run.
 * [files] answers the `build` skip marker of a module. [half] is the half of the run, and [testPlugins] are the Product
 * DSL test plugins that a run-configuration module can name.
 *
 * A module the JSON does not place writes no `BUILD.bazel` section, so it is not in any map. The plan reads the same
 * computation through [DevDistBuildSections.verdicts], so the plan and the sections state one label per target.
 *
 * [foreignSections] names the plugins whose own `dev` section states another leaf or packaging than this run. Only the
 * community pass names any, see [foreignDevSections]. Such a plugin declares no own leaf, so the run writes its leaf and
 * its `dev_plugin` into the generated plugin package. [writtenContentModuleJarModules] names the modules with a
 * `content_module_jar` call on disk, or `null` for the ultimate pass, which writes every call.
 *
 * [ownership] decides which half writes a community package, see [DevDistOwnership]. Under
 * [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES], every descriptor entry states the refusals of every stated mode, and
 * the community half states the resources of every layout of its registry. [upstream] is the result of the community
 * half, which the ultimate half reads. A `content_module_jar` call of a community module that differs from the one of
 * [upstream] goes to the product package, see [DevDistBuildSections.relocatedContentModuleJarCalls].
 */
internal fun computeDevDistBuildSections(
  projectRoot: Path,
  outputProvider: ModuleOutputProvider,
  products: List<DiscoveredProduct>,
  walk: DescriptorWalk,
  derivation: PluginPackingDerivation,
  index: DevDistBazelIndex,
  files: DevDistBuildFiles,
  half: DevDistHalf,
  testPlugins: List<TestPluginSpec>,
  verifyPlanUnits: Boolean = false,
  foreignSections: Set<String> = emptySet(),
  writtenContentModuleJarModules: Set<String>? = null,
  ownership: DevDistOwnership = DevDistOwnership.DEFAULT,
  upstream: DevDistBuildSections? = null,
): DevDistBuildSections {
  return DevDistBuildSections.compute(
    projectRoot, outputProvider, products, walk, derivation, index, files, half, testPlugins, verifyPlanUnits, foreignSections,
    writtenContentModuleJarModules, ownership, upstream,
  )
}

/**
 * The community plugins that [ultimate] plans and whose `dev` section differs from the one of [upstream], sorted.
 *
 * Under [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES], the community half writes every community section. The
 * ultimate half reuses the leaf and the `dev_plugin` of such a section when it renders the same body. Both halves
 * state the refusals of every stated mode, so the whole bodies compare. A plugin that no community product plans has
 * no community section, so it is in the result.
 */
internal fun foreignCommunitySections(upstream: DevDistBuildSections, ultimate: DevDistBuildSections): Set<String> {
  val planned = ultimate.pluginPlanEntries.mapTo(TreeSet()) { it.mainModule }.filter { ultimate.index.isCommunity(it) == true }
  return divergentDevSections(plugins = planned, sections = ultimate.devSections, otherSections = upstream.devSections)
}

/** The plugins of [plugins] whose section body in [sections] differs from the one in [otherSections], sorted. A missing body is `null`. */
internal fun divergentDevSections(plugins: Collection<String>, sections: Map<String, String>, otherSections: Map<String, String>): Set<String> {
  return plugins.filterTo(TreeSet()) { sections.get(it) != otherSections.get(it) }
}

/**
 * Whether the ultimate half writes the `content_module_jar` call [call] of [module] into its product package. That is
 * the case for a community module when [upstreamCalls], the calls of the community half, state no call or another text
 * for it. `null` [upstreamCalls] says that no community result is read, and then no call moves.
 */
internal fun isRelocatedContentModuleJarCall(module: String, call: String, upstreamCalls: Map<String, String>?, index: DevDistBazelIndex): Boolean {
  return upstreamCalls != null && index.isCommunity(module) == true && upstreamCalls.get(module) != call
}

/** The package of the `content_module_jar` calls that the ultimate half relocates, relative to the monorepo root. */
internal const val DEV_DIST_CONTENT_MODULE_JARS_PACKAGE: String = "build/dev-dist-content-module-jars"

/** The name of the relocated `content_module_jar` target of [module]. The JPS name keeps two modules of one target name apart. */
private fun relocatedContentModuleJarTargetName(module: String): String = module + "_content_module_jar"

/** The label of the relocated `content_module_jar` target of [module], see [DEV_DIST_CONTENT_MODULE_JARS_PACKAGE]. */
internal fun relocatedContentModuleJarLabel(module: String): String {
  return "//$DEV_DIST_CONTENT_MODULE_JARS_PACKAGE:${relocatedContentModuleJarTargetName(module)}"
}

/**
 * The `content_module_jar` call of the community module [module] in the product package of the ultimate half.
 *
 * [jar] spells its labels for the package of the module, so this call spells each `//` label as `@community//`. The
 * call names the module by its full label and states the name, because the macro derives the name from the target of
 * the module, and two modules of one target name share the package.
 */
internal fun relocatedContentModuleJarCall(module: String, jar: ContentModuleJarTarget, index: DevDistBazelIndex): String {
  val location = checkNotNull(index.location(module)) { "Module '$module' has no Bazel package, so it gets no call" }
  check(location.half == RepositoryHalf.COMMUNITY) { "Only the call of a community module moves to $DEV_DIST_CONTENT_MODULE_JARS_PACKAGE: '$module'" }
  fun respell(label: String): String = if (label.startsWith("//")) "@community$label" else label
  val target = Target("content_module_jar")
  target.option("name", relocatedContentModuleJarTargetName(module))
  if (jar.libraryTargetLabels.isNotEmpty()) {
    target.option("libraries", jar.libraryTargetLabels.map(::respell).unsorted())
  }
  target.option("module", "${location.absolutePackage}:${location.targetName}")
  if (jar.modulesAfter.isNotEmpty()) {
    target.option("modules_after", jar.modulesAfter.map(::respell).unsorted())
  }
  if (jar.modulesBefore.isNotEmpty()) {
    target.option("modules_before", jar.modulesBefore.map(::respell).unsorted())
  }
  jar.nativeLib?.let { target.option("native_lib", it) }
  jar.nativeLibDir?.let { target.option("native_lib_dir", it) }
  return target.render()
}

/**
 * The `BUILD.bazel` of [DEV_DIST_CONTENT_MODULE_JARS_PACKAGE] over [calls], keyed by module, or `null` when no call is
 * relocated.
 */
internal fun renderRelocatedContentModuleJarPackage(calls: Map<String, String>): String? {
  if (calls.isEmpty()) {
    return null
  }
  return buildString {
    append(GENERATED_BY_HEADER)
    append("#\n")
    append("# The `content_module_jar` calls of the community modules that the ultimate products pack differently from the\n")
    append("# community products. The community half writes the call of the community products in the package of the module.\n")
    append("\n")
    append("load(\"@community").append(CONTENT_MODULE_JAR_BZL).append("\", \"content_module_jar\")\n")
    for (module in calls.keys.sorted()) {
      append("\n")
      append(calls.getValue(module))
    }
  }
}

/**
 * The `withResource*` inputs of every layout of the registry of [half] that no request of [requests] plans, keyed by
 * main module and sorted.
 *
 * The registry is the plugin layouts of the split products of [half]. Each layout binds for the first platform variant
 * of its product. A layout that the half cannot bind prints one census line and states no input, because the half
 * cannot plan it either.
 */
internal fun collectRegistryLayoutResourceInputs(
  products: List<DiscoveredProduct>,
  requests: List<DevDistPluginRequest>,
  index: DevDistBazelIndex,
  outputProvider: ModuleOutputProvider,
  half: DevDistHalf,
): Map<String, List<DevDistPluginRawInput>> {
  val requested = requests.mapTo(HashSet()) { it.layout.mainModule }
  val resources = DevDistResourceSources(index, outputProvider)
  val result = TreeMap<String, List<DevDistPluginRawInput>>()
  for ((product, _, productProperties) in products) {
    if (product !in half.splitDistributions) continue
    val properties = productProperties as? ProductProperties ?: continue
    val variant = requests.firstOrNull { it.product == product }?.variant ?: continue
    for (layout in properties.productLayout.pluginLayouts.value) {
      val mainModule = layout.mainModule
      if (mainModule in requested || mainModule in result || index.location(mainModule) == null) continue
      val request = DevDistPluginRequest(product = product, properties = properties, tier = DevDistPluginTier.ADDITIONAL, variant = variant, layout = layout)
      val bindings = try {
        bindDevDistPluginLayout(request, variant, index, outputProvider, resources, half)
      }
      catch (e: DevDistUnplannableLayoutException) {
        println("registry layout $mainModule states no resources: ${e.message}")
        continue
      }
      val inputs = bindings.libraryLayout?.catalogueFacts?.additionalInputs.orEmpty() + bindings.assets.catalogueFacts.additionalInputs
      result.put(mainModule, inputs.filter { isModuleResourceInputId(it.id) })
    }
  }
  return result
}

/**
 * The plugins of [community] whose `dev` section differs from the one of [ultimate], sorted.
 *
 * The ultimate pass owns every `dev` section, so the community pass reads a section only when it states the same text.
 * A plugin that the community products plan compares its section body. Both passes render a section body for the
 * package of the plugin, so the two bodies are equal exactly when the leaf and the packaging are equal. The two JSON
 * files place the same community modules, so no name of an ultimate body is one that the `dev_dist_plugin` macro drops
 * in a community checkout.
 *
 * The ultimate body keeps only the mode refusals of the community product modes. A leaf applies the refusals of the
 * mode of its product, so the refusals of a mode that no community product has change no output of the leaf.
 */
internal fun foreignDevSections(community: DevDistBuildSections, ultimate: DevDistBuildSections): Set<String> {
  val planned = community.pluginPlanEntries.mapTo(HashSet()) { it.mainModule }
  val modes = community.productModes
  val result = TreeSet<String>()
  for (mainModule in planned) {
    if (community.devSections.get(mainModule) != ultimate.devSectionBody(mainModule, modes)) {
      result.add(mainModule)
    }
  }
  return result
}

/**
 * The modules whose `content_module_jar` call differs between [community] and [ultimate], sorted. The community pass
 * names the target of such a call, and the ultimate pass writes the call, so the two must agree.
 */
internal fun divergentContentModuleJarCalls(community: DevDistBuildSections, ultimate: DevDistBuildSections): List<String> {
  return community.contentModuleJarCalls.entries
    .filter { (module, call) -> ultimate.contentModuleJarCalls.get(module) != call }
    .map { it.key }
    .sorted()
}

/**
 * The inputs of one plugin plan that are not the same for the whole run.
 *
 * Products that pack a plugin alike give equal keys, so the plan of the plugin is computed once for all of them. The
 * product itself is no input: a plan reads it only to require that it is set. [layout] is [planUnitLayoutKey], because
 * each product holds its own layout objects. The facts are values, so equal facts of two products give one key.
 */
private data class PluginPlanUnitKey(
  @JvmField val layout: Any?,
  @JvmField val project: JpsProject,
  @JvmField val descriptorFacts: PluginSymbolicDescriptorFacts,
  @JvmField val preparationFacts: PluginSymbolicPreparationFacts,
  @JvmField val variant: PluginSymbolicVariant,
  @JvmField val catalogueFacts: DevDistPluginCatalogueFacts,
  @JvmField val nativePolicy: PluginSymbolicNativePolicy?,
)

/** Whether two plans state the same graph, which is what a plan unit must give every product of the unit. */
private fun hasSameBuildPlan(a: DevDistPluginBuildPlan, b: DevDistPluginBuildPlan): Boolean {
  return a.projection == b.projection &&
         a.catalogue == b.catalogue &&
         a.requiredRawInputs == b.requiredRawInputs &&
         a.requiredLibraries == b.requiredLibraries &&
         a.reusableArtifacts == b.reusableArtifacts &&
         a.layoutSignature == b.layoutSignature
}

/**
 * Writes the `dev <module>` section of every placed module into its `BUILD.bazel`, through a deferred writer.
 *
 * The result holds every visited file, the unchanged ones included, so the generation summary can state how many
 * files it covers. [finish][DevDistPlanCompute.finish] writes or reports the changed files.
 *
 * [writesPackage] answers whether the run writes the package of a directory relative to [projectRoot], see
 * [DevDistGenerationRoot.writesPackage]. The run leaves the file of another package alone.
 */
internal fun writeDevDistBuildSectionFiles(
  projectRoot: Path,
  sections: DevDistBuildSections,
  index: DevDistBazelIndex,
  files: DevDistBuildFiles,
  writesPackage: (String) -> Boolean = { true },
): DevDistPlanCompute {
  val updater = DeferredFileUpdater(projectRoot)
  val results = writeDevDistBuildSections(
    projectRoot = projectRoot,
    sections = sections,
    index = index,
    files = files,
    updater = updater,
    writesPackage = writesPackage,
  )
  return DevDistPlanCompute(updater = updater, files = results)
}

/**
 * Edits the `dev <module>` section of every module [index] places, one `BUILD.bazel` at a time.
 *
 * A module with a section body gets its section stated. A module without one gets a stale section removed, and so does
 * a module the JSON no longer places. A module whose file carries the `dev` skip marker is left alone. The load lines follow the converter's rule, see
 * [DevDistBuildFileEditor.result]. A package whose `BUILD.bazel` does not exist is reported and skipped: the converter
 * creates the file, and the tool only edits it.
 */
private fun writeDevDistBuildSections(
  projectRoot: Path,
  sections: DevDistBuildSections,
  index: DevDistBazelIndex,
  files: DevDistBuildFiles,
  updater: DeferredFileUpdater,
  writesPackage: (String) -> Boolean,
): List<DevDistPlanFileResult> {
  val modulesByFile = TreeMap<Path, MutableList<String>>()
  for (module in index.targets.modules.keys) {
    val file = files.buildFile(module) ?: continue
    modulesByFile.computeIfAbsent(file) { ArrayList() }.add(module)
  }
  // The other half writes the sections of its packages. A statement that only this half states cannot sit there.
  for ((file, modules) in modulesByFile.entries.toList()) {
    val relativePath = projectRoot.relativize(file).invariantSeparatorsPathString
    if (writesPackage(relativePath.substringBeforeLast('/', missingDelimiterValue = ""))) {
      continue
    }
    val owners = modules.filter(sections::ownsHalfStatements).sorted()
    check(owners.isEmpty()) { "The ${sections.half.name} half cannot write $relativePath, and the sections of $owners need its statements there" }
    modulesByFile.remove(file)
  }

  files.preload(modulesByFile.keys)
  val results = ArrayList<DevDistPlanFileResult>(modulesByFile.size)
  for ((file, modules) in modulesByFile) {
    val relativePath = projectRoot.relativize(file).invariantSeparatorsPathString
    val editor = files.editor(file)
    if (editor == null) {
      val owners = modules.filter(sections::ownsResourceFilegroup).sorted()
      check(owners.isEmpty()) { "$relativePath does not exist, and a plan names the resource filegroup of $owners" }
      val complexPlugins = modules.filter(sections::ownsPluginExecution).sorted()
      check(complexPlugins.isEmpty()) { "$relativePath does not exist, and the complex plugins $complexPlugins state their calls or plan files in it" }
      println("WARN: $relativePath does not exist, so the dev sections of ${modules.sorted()} are not written")
      continue
    }
    val loadSymbols = HashMap<String, MutableSet<String>>()
    for (module in modules.sorted()) {
      val sectionName = "dev $module"
      if (editor.isSectionSkipped(sectionName)) {
        check(!sections.ownsResourceFilegroup(module)) { "The section `$sectionName` is skipped, and a plan names its resource filegroup" }
        check(!sections.ownsPluginExecution(module)) { "The section `$sectionName` is skipped, and the complex plugin states its calls or plan files in it" }
        continue
      }
      val body = sections.sectionBody(module)
      if (body == null) {
        editor.removeSection(sectionName)
        continue
      }
      editor.setSection(sectionName = sectionName, body = body)
      for ((bzlFile, symbols) in sections.loadStatements.get(module).orEmpty()) {
        loadSymbols.computeIfAbsent(bzlFile) { HashSet() }.addAll(symbols)
      }
    }
    // A module that left the project keeps no section. The converter drops its `build` and `iml` sections, and the tool
    // drops the `dev` one, so a renamed or deleted module leaves no statement behind.
    val placed = modules.toHashSet()
    for (stale in editor.sectionModules(DEV_SECTION_PREFIX).filterNot { it in placed }) {
      editor.removeSection("$DEV_SECTION_PREFIX$stale")
    }
    val newContent = editor.result(loadSymbols)
    results.add(DevDistPlanFileResult(
      relativePath = relativePath,
      status = updater.writeIfChanged(path = file, oldContent = editor.original, newContent = newContent),
    ))
  }
  return results
}

/** The name of the filegroup a plan names for the `withResource*` directories of a package. `dev_plugin_remainder.bzl` reads its files. */
internal const val DEV_DIST_RESOURCES_TARGET: String = "dev_dist_resources"

/** The name of the filegroup of one filtered package-relative directory: [DEV_DIST_RESOURCES_TARGET], `_`, and the path with `_` for `/`. */
internal fun filteredResourcesTarget(packageRelativePath: String): String {
  return "${DEV_DIST_RESOURCES_TARGET}_${packageRelativePath.replace('/', '_')}"
}

/**
 * The package-relative directory a source tree prefix names, or `null` when the prefix is not below the package.
 * [absolutePackage] is `@community//pkg` or `//pkg`; a root package has an empty path.
 */
@ApiStatus.Internal
fun resourceDirectoryBelowPackage(absolutePackage: String, sourceTreePrefix: String): String? {
  val packagePath = absolutePackage.substringAfter("//")
  return when {
    packagePath.isEmpty() -> sourceTreePrefix
    sourceTreePrefix.startsWith("$packagePath/") -> sourceTreePrefix.removePrefix("$packagePath/")
    else -> null
  }
}

/** Whether [id] is the raw input of a `withResource*` declaration: `module-resource:<index>:source`. */
private fun isModuleResourceInputId(id: String): Boolean = id.startsWith("module-resource:") && id.endsWith(":source")

/**
 * The resource statements the plans of one run name, collected per package.
 *
 * A plan names `<package>:dev_dist_resources` for each `withResource*` directory, and the reader takes the files below
 * the prefix of each id. The filegroup globs those directories. A plan names a `withResource*` file by its source-file
 * label, and `exports_files` makes it a public target. So a plan names only what a section declares. The tier of the
 * plugin plays no part: a complex plugin's chain and a simple plugin's `files` read the same labels. A package without
 * a module has no section: it declares its filegroup by hand, and [DevDistResourceSources] admits a directory of such
 * a package only when the filegroup is there.
 *
 * A directory with exclusions gets a filegroup of its own, see [filteredResourcesTarget]. Its glob leaves the excluded
 * files out, so they are no inputs of the plan, and the plan copies the tree as it is.
 */
internal class DevDistResourceStatements {
  private val directoriesByPackage = TreeMap<String, TreeSet<String>>()
  private val filteredDirectoriesByPackage = TreeMap<String, TreeMap<String, DevPluginResourceExclusions>>()
  private val filesByPackage = TreeMap<String, TreeSet<String>>()
  private val requestersByPackage = TreeMap<String, TreeSet<String>>()

  /** Every package that a statement of this collection names, `@community//pkg` or `//pkg`, sorted. */
  val packages: Set<String>
    get() = TreeSet<String>().also {
      it.addAll(directoriesByPackage.keys)
      it.addAll(filteredDirectoriesByPackage.keys)
      it.addAll(filesByPackage.keys)
    }

  /** The layouts and the binders that added a statement of [absolutePackage], sorted. */
  fun requesters(absolutePackage: String): Set<String> = requestersByPackage.get(absolutePackage).orEmpty()

  /**
   * The statements of [absolutePackage] that this collection holds and [declared] does not, as text, in the order
   * directories, filtered directories, files. [declared] may spell the package of a community module as `//pkg`.
   */
  fun missingIn(declared: DevDistResourceStatements, absolutePackage: String): List<String> {
    val declaredPackage = when {
      declared.packages.contains(absolutePackage) -> absolutePackage
      absolutePackage.startsWith(COMMUNITY_REPOSITORY_PREFIX) -> "//" + absolutePackage.removePrefix(COMMUNITY_REPOSITORY_PREFIX)
      else -> absolutePackage
    }
    val result = ArrayList<String>()
    val directories = declared.directoriesByPackage.get(declaredPackage).orEmpty()
    directoriesByPackage.get(absolutePackage).orEmpty().filterNot { it in directories }.mapTo(result) { "directory $it" }
    val filtered = declared.filteredDirectoriesByPackage.get(declaredPackage).orEmpty()
    for ((directory, exclusions) in filteredDirectoriesByPackage.get(absolutePackage).orEmpty()) {
      if (filtered.get(directory) != exclusions) result.add("filtered directory $directory")
    }
    val files = declared.filesByPackage.get(declaredPackage).orEmpty()
    filesByPackage.get(absolutePackage).orEmpty().filterNot { it in files }.mapTo(result) { "file $it" }
    return result
  }

  /** Adds the directory [packageRelativePath] of [absolutePackage] to the package filegroup. [requester] names who needs it. */
  fun addDirectory(absolutePackage: String, packageRelativePath: String, requester: String? = null) {
    directoriesByPackage.computeIfAbsent(absolutePackage) { TreeSet() }.add(packageRelativePath)
    requester?.let { requestersByPackage.computeIfAbsent(absolutePackage) { TreeSet() }.add(it) }
  }

  /** Adds every `withResource*` input of the plan of [plugin]. An input of another kind is not a package resource. */
  fun addPlanInputs(plugin: String, inputs: List<DevDistPluginRawInput>) {
    for (input in inputs) {
      if (!isModuleResourceInputId(input.id)) {
        continue
      }
      val absolutePackage = input.label.substringBeforeLast(':')
      requestersByPackage.computeIfAbsent(absolutePackage) { TreeSet() }.add(plugin)
      val prefix = input.sourceTreePrefix
      if (prefix == null) {
        filesByPackage.computeIfAbsent(absolutePackage) { TreeSet() }.add(input.label.substringAfterLast(':'))
        continue
      }
      val directory = resourceDirectoryBelowPackage(absolutePackage = absolutePackage, sourceTreePrefix = prefix)
                      ?: error("The source tree '${input.id}' of '$plugin' has the prefix '$prefix' outside its package '$absolutePackage'")
      val exclusions = input.sourceTreeExclusions
      val target = if (exclusions.isEmpty()) DEV_DIST_RESOURCES_TARGET else filteredResourcesTarget(directory)
      check(input.label.substringAfterLast(':') == target) {
        "The source tree '${input.id}' of '$plugin' names '${input.label}' instead of the filegroup '$target' of its package"
      }
      if (exclusions.isEmpty()) {
        addDirectory(absolutePackage = absolutePackage, packageRelativePath = directory)
        continue
      }
      val previous = filteredDirectoriesByPackage.computeIfAbsent(absolutePackage) { TreeMap() }.putIfAbsent(directory, exclusions)
      check(previous == null || previous == exclusions) {
        "The directory '$directory' of '$absolutePackage' has two sets of exclusions: $previous and, from '$plugin', $exclusions"
      }
    }
  }

  /**
   * The statements of every package, keyed by the module whose section carries them. A package with two modules gets
   * one set of statements, in the section of its first module by name. A package without a module gets none: its
   * hand-written filegroup serves the directories, and a file of it has no owner.
   */
  fun render(index: DevDistBazelIndex): Map<String, String> {
    val result = TreeMap<String, String>()
    val packages = TreeSet<String>()
    packages.addAll(directoriesByPackage.keys)
    packages.addAll(filteredDirectoriesByPackage.keys)
    packages.addAll(filesByPackage.keys)
    for (absolutePackage in packages) {
      val owner = index.modulesInPackage(absolutePackage).firstOrNull()
      val filtered = filteredDirectoriesByPackage.get(absolutePackage).orEmpty()
      if (owner == null) {
        check(!filesByPackage.containsKey(absolutePackage)) {
          "The package '$absolutePackage' holds a plugin resource file and has no module, so no dev section can export it"
        }
        check(filtered.isEmpty()) {
          "The package '$absolutePackage' holds a filtered plugin resource directory and has no module, so no dev section can declare its filegroup"
        }
        continue
      }
      val names = filtered.keys.groupBy(::filteredResourcesTarget).filterValues { it.size > 1 }
      check(names.isEmpty()) { "The filtered directories of '$absolutePackage' share a filegroup name: $names" }
      val statements = buildList {
        directoriesByPackage.get(absolutePackage)?.let { add(renderResourceFilegroup(it)) }
        for ((directory, exclusions) in filtered) {
          add(renderFilteredResourceFilegroup(directory, exclusions))
        }
        filesByPackage.get(absolutePackage)?.let { add(renderResourceFileExports(it)) }
      }
      result.put(owner, statements.joinToString(separator = "\n"))
    }
    return result
  }
}

/**
 * The `exports_files` call over the given package-relative files, sorted. A plan names a checkout file by its
 * source-file label, and a source file is a target only when its package exports it or a rule of the package reads it.
 */
@ApiStatus.Internal
fun renderResourceFileExports(files: Collection<String>): String {
  val sorted = files.sorted()
  return buildString {
    appendLine("exports_files(")
    if (sorted.size == 1) {
      appendLine("$INDENT[${quoteStarlarkString(sorted.single())}],")
    }
    else {
      appendLine("$INDENT[")
      for (file in sorted) {
        appendLine("$INDENT$INDENT${quoteStarlarkString(file)},")
      }
      appendLine("$INDENT],")
    }
    appendLine("${INDENT}visibility = [\"//visibility:public\"],")
    appendLine(")")
  }
}

/** The `filegroup` call over the given package-relative directories, sorted, one recursive glob pattern each. */
@ApiStatus.Internal
fun renderResourceFilegroup(directories: Collection<String>): String {
  val target = Target("filegroup")
  target.option("name", DEV_DIST_RESOURCES_TARGET)
  target.option("srcs", GlobCall(directories.sorted().map { "$it/**" }))
  target.visibility(arrayOf("//visibility:public"))
  return target.render()
}

/** The `filegroup` call over one package-relative [directory] without the files its [exclusions] leave out. */
@ApiStatus.Internal
fun renderFilteredResourceFilegroup(directory: String, exclusions: DevPluginResourceExclusions): String {
  val target = Target("filegroup")
  target.option("name", filteredResourcesTarget(directory))
  target.option("srcs", GlobCall(listOf("$directory/**"), exclude = exclusions.bazelExcludes(directory)))
  target.visibility(arrayOf("//visibility:public"))
  return target.render()
}
