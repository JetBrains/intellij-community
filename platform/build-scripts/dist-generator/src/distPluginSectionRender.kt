// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.distGenerator

import com.intellij.platform.buildScripts.pluginModelTool.DerivedPluginPacking
import com.intellij.platform.buildScripts.pluginModelTool.PLUGIN_XML_LOAD_PATH
import com.intellij.platform.buildScripts.pluginModelTool.descriptorFiles
import com.intellij.platform.buildScripts.pluginModelTool.walkContentModules
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import java.util.TreeMap
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

/**
 * The `dev <main module>` section of one plugin, rendered as the converter renders it.
 *
 * [body] holds one `dist_plugin(...)` call.
 * [loadStatements] names `dist_plugin` of `dist_plugin.bzl` in the spelling of the plugin's half. The section writer
 * merges the load into the file head.
 */
internal class DevSectionRender(
  @JvmField val body: String,
  @JvmField val record: DevSectionRecord,
  @JvmField val loadStatements: List<LoadStatement>,
)

/**
 * The descriptor labels of one plugin, which the section writer and the plan files read.
 *
 * The record exists whether or not a section is rendered.
 */
internal class DevSectionRecord(
  /** The absolute `dist_plugin_descriptor` label of every variant, keyed by the variant token, sorted. */
  @JvmField val descriptorTargets: Map<String, String>,
)

/**
 * Both answers for one plugin: the draft of the section, when the emptiness rule admits one, and the record, always.
 * The section writer renders the draft once the packaging is known, and the JSON writer takes the record.
 */
internal class DevSectionOutcome(
  @JvmField val draft: PendingDevSection?,
  @JvmField val record: DevSectionRecord,
)

/** A section whose content and descriptor halves are computed. [render] adds the packaging half. */
internal class PendingDevSection(
  private val context: DevSectionContext,
  private val content: PluginContentModules?,
  private val descriptors: List<PluginDescriptorLeaf>,
  @JvmField val record: DevSectionRecord,
  @JvmField val loadStatements: List<LoadStatement>,
) {
  /** The content modules the section states, in order. */
  val contentModuleNames: List<String>
    get() = content?.contentModuleNames.orEmpty()

  /** Content modules the baseline descriptor refuses. The section still lists them; the packer does not ship them. */
  val refusedContentModules: Set<String>
    get() = descriptors.flatMapTo(HashSet()) { it.refusedContentModules }

  /** The section with the packaging [packaging]. */
  fun render(packaging: DistSimplePackaging?): DevSectionRender {
    val body = checkNotNull(renderBody(context = context, content = content, descriptors = descriptors, packaging = packaging))
    return DevSectionRender(body = body, record = record, loadStatements = loadStatements)
  }
}

/**
 * The `dev` section and the JSON record of one plugin, from the in-memory derivation.
 *
 * The converter once read a plugin table the tool wrote from [packing], rebuilt the rows the writer left out and
 * re-sorted. This function computes that reader's view in memory first, and then runs the converter's own rules over
 * it. Every label comes from [index], so the bytes match the package the converter writes into.
 *
 * [descriptorEntries] are the baseline plan entries of this plugin, one per layout variant, see
 * [DescriptorResidueClasses]. An empty list is a plugin outside the descriptor population, which renders no descriptor
 * half. A divergent product's entry renders into its product package and never into the section.
 */
internal fun computeDevSection(
  mainModule: String,
  packing: DerivedPluginPacking,
  descriptorEntries: List<PluginDescriptorEntry>,
  index: DistBazelIndex,
  outputProvider: ModuleOutputProvider,
  warn: (String) -> Unit = ::println,
): DevSectionOutcome {
  val context = DevSectionContext(
    mainModule = mainModule,
    isCommunity = index.isCommunity(mainModule) ?: error("Plugin '$mainModule' has no Bazel package, so it writes no section"),
    index = index,
    outputProvider = outputProvider,
    warn = warn,
  )
  val entries = descriptorEntries.sortedBy { it.variant }
  check(entries.map { it.variant }.distinct().size == entries.size) { "Plugin '$mainModule' has two baseline entries of one variant" }
  val contentModuleNames = sectionContentModuleNames(context = context, memberNames = packing.content.memberNames)
  // The leaf takes the conventional row of the main module and of each content module from the module record.
  val descriptors = computePluginDescriptors(
    context = context,
    entries = entries,
    conventionalModules = setOf(mainModule) + contentModuleNames,
  )
  // A main module without a production jar gets no section, so the run still repairs every other plugin.
  val hasMainLabel = index.productionModuleLabel(mainModule) != null
  if (!hasMainLabel) {
    warn("WARN: $mainModule dev section: no production jar target for the main module, so the plugin gets no section")
  }
  val statesSection = hasMainLabel && (contentModuleNames.isNotEmpty() || descriptors.isNotEmpty())
  val content = if (statesSection) PluginContentModules(contentModuleNames = contentModuleNames) else null
  val packagePrefix = index.bazelPackagePrefix(mainModule) ?: error("Plugin '$mainModule' has no Bazel package prefix")
  val descriptorTargets = TreeMap<String, String>()
  for (descriptor in descriptors) {
    descriptorTargets.put(descriptor.variant, "$packagePrefix:${index.pluginDescriptorTargetName(mainModule, descriptor.variant)}")
  }
  val record = DevSectionRecord(descriptorTargets = descriptorTargets)
  // The section states the labels of its own modules, so it loads the macro file and no target list.
  val macroFile = index.respellLibraryLabel(DIST_PLUGIN_RULE, dependentIsCommunity = context.isCommunity)
  val loadStatements = listOf(LoadStatement(bzlFile = macroFile, symbols = listOf("dist_plugin")))
  return DevSectionOutcome(
    draft = if (statesSection) PendingDevSection(context, content, descriptors, record, loadStatements) else null,
    record = record,
  )
}

/** The facts every rule of one plugin reads: the plugin, its repository half, and the two models. */
internal class DevSectionContext(
  @JvmField val mainModule: String,
  @JvmField val isCommunity: Boolean,
  @JvmField val index: DistBazelIndex,
  @JvmField val outputProvider: ModuleOutputProvider,
  @JvmField val warn: (String) -> Unit,
) {
  /** Whether the converter converted [module]: the JSON records a production target for it. A skipped module is not known. */
  fun isKnown(module: String): Boolean = index.location(module) != null

  fun jpsModule(name: String): JpsModule {
    return outputProvider.findModule(name) ?: error("Module '$name' is in bazel-targets.json and not in the project model")
  }

  /** [DistBazelIndex.libraryLabel] spelled for this plugin's package. */
  fun libraryLabel(jpsName: String, owner: String?): String? {
    return index.libraryLabel(jpsName = jpsName, owner = owner, dependentIsCommunity = isCommunity)
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// The content modules.

/** The ordered content module names the section states. */
internal class PluginContentModules(
  @JvmField val contentModuleNames: List<String>,
)

/**
 * The content modules the section states, in order.
 *
 * A community section cannot name an ultimate member, so this function skips the member. The packed components state it.
 * A member without a production jar is skipped with a warning, because the section states a label for each member.
 */
internal fun sectionContentModuleNames(context: DevSectionContext, memberNames: Collection<String>): List<String> {
  val contentModuleNames = LinkedHashSet<String>()
  for (memberName in memberNames) {
    if (!context.isKnown(memberName)) {
      context.warn("WARN: ${context.mainModule} content modules: no Bazel target for member module $memberName")
      continue
    }
    if (context.index.productionModuleLabel(memberName) == null) {
      context.warn("WARN: ${context.mainModule} content modules: no production jar target for member module $memberName")
      continue
    }
    if (context.isCommunity && context.index.isCommunity(memberName) == false) {
      continue
    }
    contentModuleNames.add(memberName)
  }
  return contentModuleNames.toList()
}

// ---------------------------------------------------------------------------------------------------------------------
// The descriptor half, as `pluginDescriptor.kt` of the converter computes it.

/** The converter's `PluginDescriptor`: one variant's descriptor leaf. */
internal class PluginDescriptorLeaf(
  @JvmField val variant: String,
  @JvmField val descriptor: String,
  /** Label to load path, in label order, less the conventional rows. The leaf takes them from the module records. */
  @JvmField val descriptors: Map<String, String>,
  /** Container label to space-joined load paths, in first-appearance order. */
  @JvmField val libraryDescriptors: Map<String, String>,
  @JvmField val refusedContentModules: List<String>,
  @JvmField val modeRefusedContentModules: Map<String, List<String>>,
  @JvmField val separateJar: List<String>,
  @JvmField val markers: List<String>,
  @JvmField val versionSuffix: String,
  /** The `CompatibleBuildRange` name the layout states, or `null` for the writer's default. */
  @JvmField val compatibleBuildRange: String?,
  @JvmField val directoryName: String,
  @JvmField val embedContentModules: Boolean,
  @JvmField val exactVersion: Boolean,
  @JvmField val retainProductDescriptor: Boolean,
  @JvmField val embeddedProductDescriptor: EmbeddedProductDescriptorPlan?,
)

/**
 * One leaf per variant, or an empty list when the plugin gets no descriptor leaf.
 *
 * A leaf holds no conventional row of [conventionalModules], see [isConventionalDescriptorRow]. The descriptor rule
 * takes each one from the `<module>_model_files` group of the record of the module, which names the same file.
 *
 * Empty when the plugin is outside the population. Empty when the plugin's own package holds no `META-INF/plugin.xml`.
 * Empty when a community plugin would have to name an ultimate label, and that case discards every variant, the leaves
 * already built included.
 */
private fun computePluginDescriptors(
  context: DevSectionContext,
  entries: List<PluginDescriptorEntry>,
  conventionalModules: Set<String>,
): List<PluginDescriptorLeaf> {
  if (entries.isEmpty()) {
    return emptyList()
  }
  val mainModule = context.jpsModule(context.mainModule)
  val descriptorPath = descriptorPackagePaths(context = context, module = mainModule, loadPath = PLUGIN_XML_LOAD_PATH).firstOrNull() ?: return emptyList()
  val result = ArrayList<PluginDescriptorLeaf>(entries.size)
  for (entry in entries) {
    val rowLabels = residueDescriptorLabels(context = context, entry = entry) ?: return emptyList()
    val contentLabels = contentDescriptorLabels(context = context, mainModule = mainModule, entry = entry) ?: return emptyList()
    val libraryLabels = libraryDescriptorLabels(context = context, entry = entry) ?: return emptyList()
    val descriptors = TreeMap(contentLabels + rowLabels)
    descriptors.entries.removeIf { (label, loadPath) ->
      isConventionalDescriptorRow(
        label = label,
        loadPath = loadPath,
        modules = conventionalModules,
        conventionalLabel = context.index::conventionalDescriptorLabel,
      )
    }
    result.add(PluginDescriptorLeaf(
      variant = entry.variant,
      descriptor = descriptorPath,
      descriptors = descriptors,
      libraryDescriptors = libraryLabels,
      refusedContentModules = entry.refusedContentModules,
      modeRefusedContentModules = entry.modeRefusedContentModules,
      separateJar = entry.separateJar,
      markers = statedMarkers(entry),
      versionSuffix = statedVersionSuffix(entry),
      compatibleBuildRange = entry.compatibleBuildRange,
      directoryName = entry.directoryName ?: "",
      embedContentModules = entry.embedsContentModules,
      exactVersion = entry.exactVersion,
      retainProductDescriptor = entry.retainProductDescriptor,
      embeddedProductDescriptor = entry.embeddedProductDescriptor,
    ))
  }
  return result
}

/** The files at [loadPath] inside the module's Bazel package, in JPS root order. */
private fun descriptorPackagePaths(context: DevSectionContext, module: JpsModule, loadPath: String): Sequence<String> {
  val packageDir = context.index.packageDir(module.name) ?: return emptySequence()
  return descriptorFiles(module = module, loadPath = loadPath)
    .map { it.relativeTo(packageDir).invariantSeparatorsPathString }
    .filter { !it.startsWith("../") }
}

/** The include rows of the residue as label to load path, or `null` for a community plugin with a row outside `community/`. */
private fun residueDescriptorLabels(context: DevSectionContext, entry: PluginDescriptorEntry): Map<String, String>? {
  val result = LinkedHashMap<String, String>()
  for (row in entry.includeDescriptors) {
    if (context.isCommunity && context.index.communityRelativePath(row.relativePath) == null) {
      return null
    }
    val label = containingPackageLabel(context = context, projectRelativePath = row.relativePath)
    if (label == null) {
      context.warn("WARN: ${context.mainModule} descriptor target: no Bazel package exports ${row.relativePath}")
      continue
    }
    result.put(label, row.loadPath)
  }
  return result
}

/**
 * The label of [projectRelativePath] from the first ancestor directory that holds a `BUILD.bazel`, walked from the root
 * of the path's repository half. A community path is `//pkg:rest` for a community plugin and `@community//pkg:rest` for
 * an ultimate one. `null` when no ancestor is a package.
 */
private fun containingPackageLabel(context: DevSectionContext, projectRelativePath: String): String? {
  val communityPath = context.index.communityRelativePath(projectRelativePath)
  val inCommunity = communityPath != null
  val root = if (inCommunity) context.index.communityRoot else context.index.projectRoot
  val insideRoot = communityPath ?: projectRelativePath
  val segments = insideRoot.split('/')
  for (depth in segments.size - 1 downTo 0) {
    val packageSegments = segments.subList(0, depth)
    val packageDirectory = packageSegments.fold(root) { directory, segment -> directory.resolve(segment) }
    if (!packageDirectory.resolve("BUILD.bazel").exists()) {
      continue
    }
    val prefix = when {
      inCommunity && !context.isCommunity -> COMMUNITY_REPOSITORY_PREFIX + packageSegments.joinToString("/")
      else -> "//" + packageSegments.joinToString("/")
    }
    return prefix + ":" + segments.subList(depth, segments.size).joinToString("/")
  }
  return null
}

/**
 * The descriptor of every content module the plugin's own `<content>` names, as label to load path, or `null` when a
 * community plugin names a content module that is not a known community module.
 *
 * The walk follows only the includes the residue rows state, and it is not filtered by any product's content filter.
 * A content row is keyed by the declaring module's package prefix, `@community//` in a community package too.
 */
private fun contentDescriptorLabels(
  context: DevSectionContext,
  mainModule: JpsModule,
  entry: PluginDescriptorEntry,
): Map<String, String>? {
  val result = LinkedHashMap<String, String>()
  val descriptor = descriptorFiles(module = mainModule, loadPath = PLUGIN_XML_LOAD_PATH).firstOrNull() ?: return result
  val fileByLoadPath = entry.includeDescriptors.associate { it.loadPath to residueRowFile(context = context, row = it.relativePath) }
  val contentModules = walkContentModules(descriptor = descriptor) { fileByLoadPath.get(it) }.moduleNames
  for (contentModule in contentModules) {
    val loadPath = contentModule.replace('/', '.') + ".xml"
    val declaringName = contentModule.substringBeforeLast('/')
    if (!context.isKnown(declaringName)) {
      if (context.isCommunity) {
        return null
      }
      context.warn("WARN: ${context.mainModule} descriptor target: no Bazel target for content module $declaringName")
      continue
    }
    if (context.isCommunity && context.index.isCommunity(declaringName) != true) {
      return null
    }
    val path = descriptorPackagePaths(context = context, module = context.jpsModule(declaringName), loadPath = loadPath).firstOrNull()
    if (path == null) {
      context.warn("WARN: ${context.mainModule} descriptor target: no production resource root of $declaringName holds $loadPath")
      continue
    }
    val prefix = context.index.bazelPackagePrefix(declaringName) ?: error("Module '$declaringName' has no Bazel package prefix")
    result.put("$prefix:$path", loadPath)
  }
  return result
}

/** A residue row path as a file: a community path against the community root, any other against the project root. */
private fun residueRowFile(context: DevSectionContext, row: String): Path {
  val communityPath = context.index.communityRelativePath(row) ?: return context.index.projectRoot.resolve(row)
  return context.index.communityRoot.resolve(communityPath)
}

/**
 * The library descriptor rows as container label to space-joined load paths, keys in first-appearance order, or `null`
 * for a community plugin whose row resolves to a repository a community package cannot name.
 */
private fun libraryDescriptorLabels(context: DevSectionContext, entry: PluginDescriptorEntry): Map<String, String>? {
  if (entry.libraryDescriptorRows.isEmpty()) {
    return emptyMap()
  }
  val result = LinkedHashMap<String, MutableList<String>>()
  for (row in entry.libraryDescriptorRows) {
    val containerLabel = context.libraryLabel(jpsName = row.libraryName, owner = row.moduleName)
                         ?: context.libraryLabel(jpsName = row.libraryName, owner = null)
    if (containerLabel == null) {
      context.warn("WARN: ${context.mainModule} descriptor target: no Bazel target for library `${row.libraryName}` of ${row.moduleName}")
      continue
    }
    if (context.isCommunity && !context.index.canName(containerLabel, dependentIsCommunity = true)) {
      return null
    }
    check(' ' !in row.loadPath) {
      "The load path '${row.loadPath}' of library '${row.libraryName}' holds a space, and the rule separates load paths by one"
    }
    result.computeIfAbsent(containerLabel) { ArrayList() }.add(row.loadPath)
  }
  return result.mapValues { it.value.joinToString(" ") }
}

/**
 * The one leaf every variant agrees on, or `null` for no leaf. Two variants that state a fact differently fail, because
 * `dist_plugin` states a plugin's descriptor facts once for every variant.
 */
private fun commonPluginDescriptor(mainModule: String, descriptors: List<PluginDescriptorLeaf>): PluginDescriptorLeaf? {
  val first = descriptors.firstOrNull() ?: return null
  for (other in descriptors) {
    val differing = other.deviatesFrom(first)
    check(differing.isEmpty()) {
      "$mainModule: layout variant '${other.variant}' states $differing differently from '${first.variant}'," +
      " and `dist_plugin` states a plugin's descriptor facts once for every variant"
    }
  }
  return first
}

private fun PluginDescriptorLeaf.deviatesFrom(other: PluginDescriptorLeaf): List<String> {
  val differing = ArrayList<String>()
  fun compare(name: String, own: Any?, theirs: Any?) {
    if (own != theirs) {
      differing.add(name)
    }
  }
  compare("descriptor", descriptor, other.descriptor)
  compare("descriptors", descriptors, other.descriptors)
  compare("library_descriptors", libraryDescriptors, other.libraryDescriptors)
  compare("refused_content_modules", refusedContentModules, other.refusedContentModules)
  compare("mode_refused_content_modules", modeRefusedContentModules, other.modeRefusedContentModules)
  compare("separate_jar", separateJar, other.separateJar)
  compare("markers", markers, other.markers)
  compare("version_suffix", versionSuffix, other.versionSuffix)
  compare("compatible_build_range", compatibleBuildRange, other.compatibleBuildRange)
  compare("directory_name", directoryName, other.directoryName)
  compare("embed_content_modules", embedContentModules, other.embedContentModules)
  compare("embedded_product_descriptor", embeddedProductDescriptor, other.embeddedProductDescriptor)
  compare("exact_version", exactVersion, other.exactVersion)
  compare("retain_product_descriptor", retainProductDescriptor, other.retainProductDescriptor)
  return differing
}

// ---------------------------------------------------------------------------------------------------------------------
// Emission. This file is the one writer of the section.

/**
 * The section body, or `null` when the plugin states no content and no descriptor.
 *
 * [packaging] adds `jars`, `module_jar_paths`, `content_module_jar_labels` and the copies for a plugin Bazel packs from the declaration. A
 * cross-half packaging is declared in the ultimate package instead, so the section states nothing for it.
 */
private fun renderBody(
  context: DevSectionContext,
  content: PluginContentModules?,
  descriptors: List<PluginDescriptorLeaf>,
  packaging: DistSimplePackaging?,
): String? {
  if (content == null && descriptors.isEmpty()) {
    return null
  }
  val descriptor = commonPluginDescriptor(mainModule = context.mainModule, descriptors = descriptors)
  val call = Target("dist_plugin")
  val declaredPackaging = packaging?.takeIf { !it.crossHalf && packedModulesHaveLabels(context, it) }
  // The attributes in alphabetical order, each only where it states something.
  declaredPackaging?.classpathJars?.ifNotEmpty { call.option("classpath_jars", it.unsorted()) }
  descriptor?.compatibleBuildRange?.let { call.option("compatible_build_range", it) }
  declaredPackaging?.contentModuleJarLabels?.ifNotEmpty { call.option("content_module_jar_labels", LinkedHashMap(it)) }
  content?.contentModuleNames?.ifNotEmpty { names ->
    call.option("content_modules", names.associateWithTo(LinkedHashMap()) { sectionModuleLabel(context, it) })
  }
  descriptor?.descriptor?.ifNotEmpty { call.option("descriptor", it) }
  // The leaf takes the conventional row of the main module and of each content module from the record of the module.
  val descriptorRows = descriptor?.let { splitDescriptorRows(it.descriptors) }
  descriptorRows?.descriptors?.ifNotEmpty { call.option("descriptors", LinkedHashMap(it)) }
  descriptor?.directoryName?.ifNotEmpty { call.option("directory_name", it) }
  if (descriptor != null && !descriptor.embedContentModules) {
    call.option("embed_content_modules", false)
  }
  // The embedded product descriptor is a standalone call of the section, see `renderEmbeddedFrontendHelpers`.
  if (descriptor?.exactVersion == true) {
    call.option("exact_version", true)
  }
  declaredPackaging?.executables?.ifNotEmpty { call.option("executables", it.sorted()) }
  declaredPackaging?.filePrefixes?.ifNotEmpty { call.option("file_prefixes", LinkedHashMap(TreeMap(it))) }
  declaredPackaging?.let { filesOption(context, it) }?.let { call.option("files", it) }
  descriptor?.embeddedProductDescriptor?.frontendApplicationInfo?.let { frontend ->
    call.option("frontend_application_info", frontend.clientApplicationInfo)
    call.option("frontend_product_application_info", frontend.productApplicationInfo)
  }
  declaredPackaging?.let { jarsOption(context, it) }?.let { call.option("jars", it) }
  descriptor?.libraryDescriptors?.ifNotEmpty { call.option("library_descriptors", LinkedHashMap(it)) }
  call.option("main_module", context.mainModule)
  descriptor?.markers?.ifNotEmpty { call.option("markers", it) }
  descriptor?.modeRefusedContentModules?.ifNotEmpty { refusals ->
    call.option("mode_refused_content_modules", LinkedHashMap(refusals.toSortedMap().mapValues { NestedStringList(it.value) }))
  }
  descriptorRows?.moduleDescriptors?.ifNotEmpty { call.option("module_descriptors", it) }
  declaredPackaging?.moduleJarPaths?.ifNotEmpty { call.option("module_jar_paths", LinkedHashMap(it)) }
  call.option("module_labels", moduleLabelsOption(context = context, content = content, packaging = declaredPackaging))
  descriptor?.refusedContentModules?.ifNotEmpty { call.option("refused_content_modules", it) }
  if (descriptor?.retainProductDescriptor == true) {
    call.option("retain_product_descriptor", true)
  }
  descriptor?.separateJar?.ifNotEmpty { call.option("separate_jar", it) }
  declaredPackaging?.treeFiles?.ifNotEmpty { call.option("tree_files", it.sorted()) }
  descriptors.map { it.variant }.filter { it.isNotEmpty() }.ifNotEmpty { call.option("variants", it) }
  descriptor?.versionSuffix?.ifNotEmpty { call.option("version_suffix", it) }

  return call.render().trim()
}

/**
 * The `module_labels` attribute: the label of the main module and of each module token of `jars` that is no content
 * module, sorted by module name. `content_modules` states the label of each content module, see [sectionModuleLabel].
 */
private fun moduleLabelsOption(
  context: DevSectionContext,
  content: PluginContentModules?,
  packaging: DistSimplePackaging?,
): LinkedHashMap<String, String> {
  val names = sortedSetOf(context.mainModule)
  packaging?.jars?.values?.forEach { tokens -> tokens.filterNotTo(names, ::isLabelToken) }
  content?.contentModuleNames?.let(names::removeAll)
  return names.associateWithTo(LinkedHashMap()) { sectionModuleLabel(context, it) }
}

/**
 * The label of [module] as the converter writes a dependency of the plugin's package, see
 * [DistBazelIndex.dependencyLabel]. The section and its packaging leave out a module without a production jar, so each
 * name has a label. The label names the production target of the targets JSON, or the run fails.
 */
private fun sectionModuleLabel(context: DevSectionContext, module: String): String {
  val recorded = checkNotNull(context.index.productionModuleLabel(module)) {
    "Plugin '${context.mainModule}' names the module '$module' without a production jar, and the section filters such a name"
  }
  val label = checkNotNull(context.index.dependencyLabel(module = module, dependentIsCommunity = context.isCommunity)) {
    "Plugin '${context.mainModule}' names the module '$module', which its package cannot name"
  }
  val withTarget = if (':' in label) label else label + ":" + label.substringAfterLast('/')
  val absolute = if (context.isCommunity && withTarget.startsWith("//")) COMMUNITY_REPOSITORY_PREFIX + withTarget.removePrefix("//") else withTarget
  check(absolute == recorded) {
    "Plugin '${context.mainModule}' names the module '$module' as '$label', and the targets JSON records the production" +
    " target '$recorded'"
  }
  return label
}

/**
 * Whether each module token of [packaging] has a production jar. A token without one drops the packaging with a
 * warning, so the section declares the descriptor and no component, and the run still repairs every other plugin.
 */
private fun packedModulesHaveLabels(context: DevSectionContext, packaging: DistSimplePackaging): Boolean {
  val missing = packaging.jars.values.asSequence()
    .flatten()
    .filterNot(::isLabelToken)
    .filter { context.index.productionModuleLabel(it) == null }
    .toSortedSet()
  if (missing.isEmpty()) {
    return true
  }
  context.warn("WARN: ${context.mainModule} packaging: no production jar target for $missing, so the section declares no component")
  return false
}

/** The `jars` attribute: each destination with its ordered source tokens, labels spelled for this package's half. */
private fun jarsOption(context: DevSectionContext, packaging: DistSimplePackaging): LinkedHashMap<String, NestedStringList> {
  check(packaging.mainModule == context.mainModule) { "The packaging of '${packaging.mainModule}' does not belong to '${context.mainModule}'" }
  val result = LinkedHashMap<String, NestedStringList>()
  for ((destination, tokens) in packaging.jars) {
    result.put(destination, NestedStringList(tokens.map { token ->
      if (isLabelToken(token)) context.index.respellLibraryLabel(token, dependentIsCommunity = context.isCommunity) else token
    }))
  }
  return result
}

/** The `files` attribute: each destination with its source label spelled for this package's half, sorted, or `null` for no copy. */
private fun filesOption(context: DevSectionContext, packaging: DistSimplePackaging): LinkedHashMap<String, String>? {
  if (packaging.files.isEmpty()) {
    return null
  }
  val result = LinkedHashMap<String, String>()
  for (destination in packaging.files.keys.sorted()) {
    result.put(destination, context.index.respellLibraryLabel(packaging.files.getValue(destination), dependentIsCommunity = context.isCommunity))
  }
  return result
}

private inline fun <T : Collection<*>> T.ifNotEmpty(action: (T) -> Unit) {
  if (isNotEmpty()) {
    action(this)
  }
}

private inline fun <T : Map<*, *>> T.ifNotEmpty(action: (T) -> Unit) {
  if (isNotEmpty()) {
    action(this)
  }
}

private inline fun String.ifNotEmpty(action: (String) -> Unit) {
  if (isNotEmpty()) {
    action(this)
  }
}
