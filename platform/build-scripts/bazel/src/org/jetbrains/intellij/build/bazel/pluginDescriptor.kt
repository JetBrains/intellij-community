// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import org.jdom.Element
import java.nio.file.Path
import java.util.TreeMap
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * What one plugin's `dev_dist_plugin_descriptor` target declares, as Bazel labels.
 *
 * The target patches the plugin's `META-INF/plugin.xml` the way a dev-distribution assembly does, and it is emitted
 * beside the plugin for the reason `dev_dist_plugin_content` is: a plugin's descriptor changes when that plugin's own
 * `plugin.xml` changes, so the fact belongs in the package its owner already edits.
 *
 * Almost everything is derived. The plugin's own descriptor comes from its production resource roots, the descriptor of
 * every content module comes from the plugin's own `<content>`, and the plugin directory and the main jar name come from
 * the main module's name. `dev-dist-descriptor.yaml` is the remainder, and it sits beside the plugin only where the
 * convention fails - see [parsePluginDescriptorReport].
 */
internal class PluginDescriptor(
  @JvmField val mainModule: String,
  /** The layout variant, empty for a plugin whose one layout serves every platform. It names the target. */
  @JvmField val variant: String,
  /** The plugin's own `META-INF/plugin.xml`, as a path inside the module's own Bazel package. */
  @JvmField val descriptor: String,
  /** Every other descriptor the patch reads, keyed by label and valued by load path. */
  @JvmField val descriptors: Map<String, String>,
  /** A descriptor a library jar answers, keyed by the library container and valued by its load paths, space separated. */
  @JvmField val libraryDescriptors: Map<String, String>,
  /** The content modules the product's filter refuses, in descriptor order. Normally empty. */
  @JvmField val refusedContentModules: List<String>,
  @JvmField val separateJar: List<String>,
  @JvmField val markers: List<String>,
  @JvmField val versionSuffix: String,
  @JvmField val directoryName: String,
  @JvmField val mainJarName: String,
  @JvmField val embedContentModules: Boolean,
  @JvmField val exactVersion: Boolean,
  @JvmField val retainProductDescriptor: Boolean,
)

internal const val PLUGIN_DESCRIPTOR_REPORT_FILE_NAME: String = "dev-dist-descriptor.yaml"

/** The population the plan generator states; see [readPluginDescriptorPopulation]. */
internal const val PLUGIN_DESCRIPTOR_POPULATION_FILE_NAME: String = "dev_dist_plugin_descriptor_population.txt"

/**
 * Which layout variants a descriptor target exists for, by plugin main module.
 *
 * One `<main module>` or `<main module>/<variant>` per line, and a `#` line is a comment. The empty string is the
 * variant of a plugin whose one layout serves every platform.
 *
 * Fail-open on an absent file: an empty map emits no descriptor target anywhere, and a checkout whose plan generator
 * has never run is exactly the case that must still convert.
 */
internal fun readPluginDescriptorPopulation(file: Path): Map<String, List<String>> {
  if (!file.exists()) {
    return emptyMap()
  }
  val result = LinkedHashMap<String, MutableList<String>>()
  for (raw in file.readText().lineSequence()) {
    val line = raw.trim()
    if (line.isEmpty() || line.startsWith('#')) {
      continue
    }
    result.computeIfAbsent(line.substringBefore('/')) { ArrayList() }.add(line.substringAfter('/', ""))
  }
  return result
}

/**
 * One section of a plugin's descriptor report - what the convention does not give about one (plugin, layout variant).
 *
 * Every field defaults, and an absent section means every default. So a plugin with no report at all is pure
 * convention, which is what keeps this file beside about 30 plugins instead of beside every one.
 */
@Serializable
internal data class PluginDescriptorReportSection(
  /** Every descriptor an `xi:include` of this plugin's closure reaches. */
  @JvmField val descriptors: List<ReportDescriptorRow> = emptyList(),
  @JvmField @SerialName("library_descriptors") val libraryDescriptors: List<ReportLibraryDescriptorRow> = emptyList(),
  /** The content modules the product's `ContentModuleFilter` refuses, in descriptor order. */
  @JvmField @SerialName("refused_content_modules") val refusedContentModules: List<String> = emptyList(),
  @JvmField @SerialName("separate_jar") val separateJar: List<String> = emptyList(),
  @JvmField val markers: List<String> = emptyList(),
  @JvmField @SerialName("version_suffix") val versionSuffix: String = "",
  @JvmField @SerialName("directory_name") val directoryName: String = "",
  @JvmField @SerialName("main_jar_name") val mainJarName: String = "",
  @JvmField @SerialName("no_embedding") val noEmbedding: Boolean = false,
  @JvmField @SerialName("exact_version") val exactVersion: Boolean = false,
  @JvmField @SerialName("retain_product_descriptor") val retainProductDescriptor: Boolean = false,
)

/** One descriptor of a report: the load path a resolver asks for, and the project-relative path of the file. */
@Serializable
internal data class ReportDescriptorRow(
  @JvmField @SerialName("load_path") val loadPath: String,
  @JvmField val path: String,
)

/** One library descriptor of a report, by name. A jar file name carries the artifact version, so no report states one. */
@Serializable
internal data class ReportLibraryDescriptorRow(
  @JvmField @SerialName("load_path") val loadPath: String,
  @JvmField val module: String,
  @JvmField val library: String,
)

/**
 * The name of the target that patches one plugin's descriptor, in the plugin's own package.
 *
 * `dev_dist_plugin_descriptor_target_name` of `dev_dist_plugin_descriptor.bzl` names the target, and this mirrors it.
 * Its own function because the name is written in two places that must agree: the section the converter emits, which
 * lets the macro derive the name, and the label `build/bazel-targets.json` records for the plan generator.
 */
internal fun pluginDescriptorTargetName(mainModule: String, variant: String): String {
  val suffix = if (variant.isEmpty()) "" else "_$variant"
  return "$mainModule$suffix$DEV_DESCRIPTOR_TARGET_SUFFIX"
}

/** What `dev_dist_plugin_descriptor_target_name` appends. `descriptorTargetOf` of the plan generator spells it too. */
private const val DEV_DESCRIPTOR_TARGET_SUFFIX: String = "_dev_descriptor"

/**
 * Writes [descriptors] into the plugin's own `BUILD.bazel`.
 *
 * One target per layout variant. A plugin whose descriptor differs by operating system or architecture has one target
 * per variant, and the variant names the target as well as the output's directory.
 *
 * `manual` is not written here: the `dev_dist_plugin_descriptor` macro adds it, for the reason `content_module_jar` has
 * one. These are per-plugin targets of a measurement, and `bazel build //...` must run none of their actions.
 */
internal fun BuildFile.emitPluginDescriptor(module: ModuleDescriptor, descriptors: List<PluginDescriptor>) {
  load("@community//platform/build-scripts/bazel-rules:dev_dist_plugin_descriptor.bzl", "dev_dist_plugin_descriptor")
  for (descriptor in descriptors) {
    target("dev_dist_plugin_descriptor") {
      // Emitted in the order the Starlark formatter sorts them - alphabetical - so that a regeneration needs no
      // reformat. `name` is absent: the macro derives it from `main_module` and `variant`.
      if (descriptor.descriptor.isNotEmpty()) {
        option("descriptor", descriptor.descriptor)
      }
      option("descriptor_module", ":${module.targetName}")
      if (descriptor.descriptors.isNotEmpty()) {
        option("descriptors", LinkedHashMap(descriptor.descriptors))
      }
      if (descriptor.directoryName.isNotEmpty()) {
        option("directory_name", descriptor.directoryName)
      }
      if (!descriptor.embedContentModules) {
        option("embed_content_modules", false)
      }
      if (descriptor.exactVersion) {
        option("exact_version", true)
      }
      if (descriptor.libraryDescriptors.isNotEmpty()) {
        option("library_descriptors", LinkedHashMap(descriptor.libraryDescriptors))
      }
      if (descriptor.mainJarName.isNotEmpty()) {
        option("main_jar_name", descriptor.mainJarName)
      }
      option("main_module", descriptor.mainModule)
      if (descriptor.markers.isNotEmpty()) {
        option("markers", descriptor.markers)
      }
      if (descriptor.refusedContentModules.isNotEmpty()) {
        option("refused_content_modules", descriptor.refusedContentModules)
      }
      if (descriptor.retainProductDescriptor) {
        option("retain_product_descriptor", true)
      }
      if (descriptor.separateJar.isNotEmpty()) {
        option("separate_jar", descriptor.separateJar)
      }
      if (descriptor.variant.isNotEmpty()) {
        option("variant", descriptor.variant)
      }
      if (descriptor.versionSuffix.isNotEmpty()) {
        option("version_suffix", descriptor.versionSuffix)
      }
    }
  }
}

/**
 * The directory of every cross-half descriptor package, so the ultimate half's sweep covers it.
 *
 * A plugin no `dev descriptor` section names needs its leaf somewhere, and `plugin-model-tool` writes one Bazel package
 * per such plugin under `build/dev-dist-descriptors/`. Those packages are generated files of the main repository, so
 * `deleteOldFiles` has to know them: without this, a package a plugin stopped needing would stay in the tree.
 *
 * The complement of what this run emitted, which is the same rule the plan generator applies to `descriptorTargets` of
 * `bazel-targets.json`. The two read one run apart, so a plugin that just crossed the line is swept by the next run.
 *
 * Never a community directory. The whole point of these packages is a label only the main repository can name, and this
 * resolves every one of them against [ultimateRoot].
 *
 * An empty population answers what the main repository's list already registers, and not the empty list. The
 * population read is fail-open, so an absent file states nothing rather than states none, and the complement of
 * nothing is every package. `deleteOldFiles` deletes what the list names and this run does not, so the complement
 * would delete all 25 checked-in packages. A bisect, a half revert and a rebase all reach that state.
 */
internal fun crossHalfDescriptorPackageDirectories(
  ultimateRoot: Path,
  population: Map<String, List<String>>,
  moduleTargets: List<BazelBuildFileGenerator.ModuleTargets>,
): List<Path> {
  if (population.isEmpty()) {
    return registeredCrossHalfDescriptorPackageDirectories(ultimateRoot)
  }
  val emitted = HashSet<String>()
  for (moduleTarget in moduleTargets) {
    for (descriptor in moduleTarget.pluginDescriptors) {
      emitted.add(descriptor.mainModule)
    }
  }
  return population.keys.asSequence()
    .filterNot { it in emitted }
    .sorted()
    .map { ultimateRoot.resolve("$CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT/$it") }
    .toList()
}

/**
 * The cross-half packages the main repository's generated-file list already registers.
 *
 * A checkout whose plan generator has never run holds no such package, so this answers an empty list there, which is
 * the verdict the population states as well. A checkout that holds them keeps every one registered, so the sweep
 * deletes none of them and one absent file costs no checked-in work.
 */
private fun registeredCrossHalfDescriptorPackageDirectories(ultimateRoot: Path): List<Path> {
  val fileListFile = ultimateRoot.resolve(BAZEL_GENERATED_FILE_LIST_RELATIVE_PATH)
  if (!fileListFile.exists()) {
    return emptyList()
  }
  return fileListFile.readText().lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("$CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT/") }
    .sorted()
    .map { ultimateRoot.resolve(it) }
    .toList()
}

/** Where `collectCrossHalfDescriptorPackages` of `devDistCrossHalfDescriptorPackage.kt` writes such a package. */
private const val CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT: String = "build/dev-dist-descriptors"

/**
 * [ModuleDescriptor.pluginDescriptorReportFile] as a path inside the module's own Bazel package, so that it can be
 * exported and named by a label. `null` when the module has no report, or when the report is outside the package -
 * `../` is not a label.
 */
internal fun pluginDescriptorReportPackagePath(module: ModuleDescriptor): String? {
  val file = module.pluginDescriptorReportFile ?: return null
  return file.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString.takeIf { !it.startsWith("../") }
}

/**
 * Parses [ModuleDescriptor.pluginDescriptorReportFile]; reached only through [ModuleDescriptor.pluginDescriptorReport].
 *
 * A keyed map and not a list, unlike `plugin-content.yaml`: the key is one (plugin, layout variant), and a deviation is
 * a fact about one variant rather than about the plugin. A section may state nothing, which is how a plugin whose only
 * deviation is having a variant at all is expressed.
 */
internal fun parsePluginDescriptorReport(file: Path?): Map<String, PluginDescriptorReportSection?>? {
  if (file == null) {
    return null
  }
  val text = file.readText()
  if (text.isBlank()) {
    return null
  }
  return recipeYaml.decodeFromString(
    MapSerializer(String.serializer(), PluginDescriptorReportSection.serializer().nullable),
    text,
  ).takeIf { it.isNotEmpty() }
}

/**
 * Reads the descriptor report beside [module] and resolves it into Bazel labels, or answers an empty list.
 *
 * An empty list means this package declares no descriptor target. Three cases reach it. No product's plan names
 * [module], which is the common one - see [readPluginDescriptorPopulation]. Or its own Bazel package holds no
 * `META-INF/plugin.xml`, so no label names the file this target has to patch. Or this would be a community target that
 * has to name something only the main repository can name.
 *
 * That third case is silent and it is the local decision rule the report exists for. `JpsModuleToBazel` generates and
 * sweeps BUILD files one repository half at a time, so a community-only checkout has to reach the same verdict as the
 * community half of an ultimate checkout. Every input of the verdict is therefore either a path in the report - a path
 * outside `community/` is a path a community package cannot name - or a member module the community half does not
 * have. A checkout that has the module and one that does not both answer no.
 *
 * Anything the report names and this generator cannot label is dropped with a warning rather than failing the run, for
 * the reason `computePluginContent` gives: an under-declared target fails its own action by name, and no target at all
 * would be silent.
 */
internal fun computePluginDescriptor(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): List<PluginDescriptor> {
  val moduleName = module.module.name
  val variants = context.pluginDescriptorPopulation.get(moduleName) ?: return emptyList()
  val descriptorPath = ownDescriptorPackagePath(module) ?: return emptyList()

  val report = module.pluginDescriptorReport.orEmpty()
  for (key in report.keys) {
    if (key.substringBefore('/') != moduleName) {
      println("WARN: $moduleName descriptor target: $PLUGIN_DESCRIPTOR_REPORT_FILE_NAME states the section '$key'," +
              " which names another plugin")
    }
  }
  val result = ArrayList<PluginDescriptor>(variants.size)
  for (variant in variants) {
    val key = if (variant.isEmpty()) moduleName else "$moduleName/$variant"
    val section = report.get(key) ?: PluginDescriptorReportSection()
    val rowLabels = reportDescriptorLabels(module = module, section = section, context = context) ?: return emptyList()
    val contentLabels = when {
      section.noEmbedding -> emptyMap()
      else -> contentDescriptorLabels(module = module, section = section, moduleList = moduleList, context = context)
                ?: return emptyList()
    }
    val libraryLabels = libraryDescriptorLabels(module = module, section = section, context = context) ?: return emptyList()
    result.add(PluginDescriptor(
      mainModule = moduleName,
      variant = variant,
      descriptor = descriptorPath,
      descriptors = TreeMap(contentLabels + rowLabels),
      libraryDescriptors = libraryLabels,
      refusedContentModules = section.refusedContentModules,
      separateJar = section.separateJar,
      markers = section.markers,
      versionSuffix = section.versionSuffix,
      directoryName = section.directoryName,
      mainJarName = section.mainJarName,
      embedContentModules = !section.noEmbedding,
      exactVersion = section.exactVersion,
      retainProductDescriptor = section.retainProductDescriptor,
    ))
  }
  return result
}

/** `META-INF/plugin.xml` inside [module]'s own Bazel package, or `null` when no production resource root holds it. */
private fun ownDescriptorPackagePath(module: ModuleDescriptor): String? {
  return descriptorPackagePath(module = module, loadPath = PLUGIN_XML_LOAD_PATH)
}

/** The plugin's own descriptor, at the load path every plugin uses. */
internal const val PLUGIN_XML_LOAD_PATH: String = "META-INF/plugin.xml"

/**
 * Every file a production resource root of [module] holds at [loadPath], in resource-root order.
 *
 * Only a resource root the jar takes at its own root is asked, because a load path is jar-relative. A root the layout
 * maps into a subdirectory answers another path, and no descriptor of this project needs that.
 */
internal fun descriptorFiles(module: ModuleDescriptor, loadPath: String): Sequence<Path> {
  return module.resources.asSequence()
    .filter { it.relativeOutputPath.isEmpty() }
    .map { it.root.resolve(loadPath) }
    .filter { it.isRegularFile() }
}

/**
 * [loadPath] inside [module]'s own Bazel package, or `null` when no production resource root of it holds the file.
 *
 * The path is composed exactly as `exportDescriptorFiles` composes the `exports_files` entry - relative to the module's
 * Bazel package - so the label this yields is one that package really exports.
 */
private fun descriptorPackagePath(module: ModuleDescriptor, loadPath: String): String? {
  return descriptorFiles(module = module, loadPath = loadPath)
    .map { it.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString }
    .firstOrNull { !it.startsWith("../") }
}

/**
 * The report's own rows as `label` to `load path`, or `null` when this target may not exist.
 *
 * `null` for a community plugin one of whose rows names a path outside `community/`. That is the whole local rule: the
 * community half cannot name a main-repository label, and it reaches this verdict from the report's text alone.
 */
private fun reportDescriptorLabels(
  module: ModuleDescriptor,
  section: PluginDescriptorReportSection,
  context: BazelBuildFileGenerator,
): Map<String, String>? {
  val result = LinkedHashMap<String, String>()
  for (row in section.descriptors) {
    if (module.isCommunity && !row.path.startsWith(COMMUNITY_PATH_PREFIX)) {
      return null
    }
    val label = containingPackageLabel(projectRelativePath = row.path, isCommunityDependent = module.isCommunity, context = context)
    if (label == null) {
      println("WARN: ${module.module.name} descriptor target: no Bazel package exports ${row.path}")
      continue
    }
    result.put(label, row.loadPath)
  }
  return result
}

/** The project-relative path prefix of the community repository inside the ultimate monorepo. */
private const val COMMUNITY_PATH_PREFIX: String = "community/"

/**
 * The descriptor of every content module the plugin's own `<content>` names, as `label` to `load path`, or `null` when
 * this target may not exist.
 *
 * The convention, and the one reason 1 600 of the plan's 1 655 rows are checked in nowhere: a content module named
 * `intellij.foo/bar` puts its descriptor at `intellij.foo.bar.xml` in a production resource root of `intellij.foo`.
 *
 * `null` for a community plugin one of whose content modules is not a community module this generator converts. A
 * checkout without the ultimate half does not have the module either, so both checkouts answer the same.
 */
private fun contentDescriptorLabels(
  module: ModuleDescriptor,
  section: PluginDescriptorReportSection,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): Map<String, String>? {
  val result = LinkedHashMap<String, String>()
  for (contentModule in resolveContentModules(module = module, section = section, context = context)) {
    val loadPath = contentModule.replace('/', '.') + ".xml"
    val declaringName = contentModule.substringBeforeLast('/')
    val declaring = moduleList.getModuleDescriptorOrNull(declaringName)
    if (declaring == null || moduleList.skippedModules.contains(declaringName)) {
      if (module.isCommunity) {
        return null
      }
      println("WARN: ${module.module.name} descriptor target: no Bazel target for content module $declaringName")
      continue
    }
    if (module.isCommunity && !declaring.isCommunity) {
      return null
    }
    val path = descriptorPackagePath(module = declaring, loadPath = loadPath)
    if (path == null) {
      println("WARN: ${module.module.name} descriptor target: no production resource root of $declaringName holds $loadPath")
      continue
    }
    val prefix = bazelPackagePrefix(module = declaring, communityRoot = context.communityRoot, ultimateRoot = context.ultimateRoot)
    result.put("$prefix:$path", loadPath)
  }
  return result
}

/**
 * [walkContentModules] over this plugin's own descriptor, with the report answering every `xi:include`.
 *
 * The report is what makes an include resolvable here. It states the project-relative path of every descriptor an
 * `xi:include` reaches, so this walk needs no module output and no search scope. A row the report does not state leaves
 * the include unfollowed, which this caller accepts: a descriptor target with no report is pure convention, and the
 * population holds out the plugins whose closure needs one.
 */
private fun resolveContentModules(
  module: ModuleDescriptor,
  section: PluginDescriptorReportSection,
  context: BazelBuildFileGenerator,
): List<String> {
  val descriptor = descriptorFiles(module = module, loadPath = PLUGIN_XML_LOAD_PATH).firstOrNull() ?: return emptyList()
  val fileByLoadPath = section.descriptors.associate { it.loadPath to reportFile(row = it.path, context = context) }
  return walkContentModules(descriptor = descriptor) { fileByLoadPath.get(it) }.moduleNames
}

/**
 * One project-relative report path as a file of this checkout.
 *
 * A path under `community/` is resolved against the community root, and every other path against the ultimate root. So
 * a community-only checkout, whose own root **is** the community root, reads the same row as the community half of an
 * ultimate checkout. `null` is a path this checkout does not have.
 */
internal fun reportFile(row: String, context: BazelBuildFileGenerator): Path? {
  if (row.startsWith(COMMUNITY_PATH_PREFIX)) {
    return context.communityRoot.resolve(row.removePrefix(COMMUNITY_PATH_PREFIX))
  }
  return context.ultimateRoot?.resolve(row)
}

/**
 * What one walk of a plugin's descriptor closure found, and what it could not follow.
 *
 * The two refusal lists exist because a caller that only measures has to tell an empty closure from a closure it failed
 * to read. [unresolvedIncludes] is the second case: the walk knows the load path and has no file for it, so the content
 * modules behind that include are missing and the caller must hold the plugin out rather than state a short list.
 * [selectiveIncludes] is a pointer that selects a subtree, which the walk declines by design.
 */
internal class WalkedContentModules(
  @JvmField val moduleNames: List<String>,
  @JvmField val unresolvedIncludes: List<String>,
  @JvmField val selectiveIncludes: List<String>,
)

/**
 * Every `<module/>` of the resolved `<content>` of [descriptor], in descriptor order, with [resolveInclude] answering
 * the file behind each `xi:include` load path.
 *
 * `resolveXIncludeElement` replaces a root-level `xi:include` with the included root's children **at the include's own
 * position**, so a `<content>` block an included file states belongs where the include sat. Reading the plugin's own
 * `<content>` blocks alone is therefore both short and out of order, and `intellij.database.plugin` is the case: four
 * top-level includes splice in 63 `<module>` elements.
 *
 * `appendContentModulesOf` of `devDistPlanGenerator.kt` is the authority this mirrors, and it is the walk the checked-in
 * plan was built with.
 */
internal fun walkContentModules(descriptor: Path, resolveInclude: (String) -> Path?): WalkedContentModules {
  val result = ArrayList<String>()
  val unresolved = ArrayList<String>()
  val selective = ArrayList<String>()
  appendContentModules(
    root = JDOMUtil.load(descriptor),
    resolveInclude = resolveInclude,
    visited = HashSet(),
    out = result,
    unresolved = unresolved,
    selective = selective,
  )
  return WalkedContentModules(moduleNames = result, unresolvedIncludes = unresolved, selectiveIncludes = selective)
}

private fun appendContentModules(
  root: Element,
  resolveInclude: (String) -> Path?,
  visited: MutableSet<String>,
  out: MutableList<String>,
  unresolved: MutableList<String>,
  selective: MutableList<String>,
) {
  for (child in root.children) {
    if (child.name == "content") {
      for (moduleElement in child.getChildren("module")) {
        moduleElement.getAttributeValue("name")?.let(out::add)
      }
      continue
    }
    if (child.name != "include" || child.namespace != JDOMUtil.XINCLUDE_NAMESPACE) {
      continue
    }
    val href = child.getAttributeValue("href") ?: continue
    // `resolveElement` answers `null` for an optional or a dynamic include, so it contributes no child at all.
    if (child.getChild("fallback", child.namespace) != null ||
        child.getAttribute("includeIf") != null ||
        child.getAttribute("includeUnless") != null) {
      continue
    }
    // Any other pointer selects a subtree instead of the included root's children, and the position a `<content>` block
    // lands at is then not the include's. The plan holds such a plugin out by name, so no such plugin is here.
    if (child.getAttributeValue("xpointer").let { it != null && it != DEFAULT_XPOINTER }) {
      selective.add(href)
      continue
    }
    val loadPath = toLoadPath(href)
    if (!visited.add(loadPath)) {
      continue
    }
    val file = resolveInclude(loadPath)?.takeIf { it.isRegularFile() }
    if (file == null) {
      unresolved.add(loadPath)
      continue
    }
    val included = JDOMUtil.load(file)
    // `extractNeededChildrenFor` returns nothing when the included root is not the pointer's root tag, so such an
    // include splices no child and contributes no content module.
    if (included.name != "idea-plugin") {
      continue
    }
    appendContentModules(
      root = included,
      resolveInclude = resolveInclude,
      visited = visited,
      out = out,
      unresolved = unresolved,
      selective = selective,
    )
  }
}

/** The `xpointer` `extractNeededChildrenFor` assumes when an `xi:include` states none. It selects every child. */
private const val DEFAULT_XPOINTER: String = "xpointer(/idea-plugin/*)"

/**
 * The load path an `xi:include` href asks the descriptor cache for.
 *
 * `LoadPathUtil.toLoadPath` is the authority, and the plan generator calls it to compose every report row's load path.
 * This restates the rule, because `intellij.platform.pluginSystem.parser.impl` is no dependency of this converter.
 * `PluginDescriptorLoadPathTest` pins every shape, and `TestTheLoadPathOfAnHref` pins the same shapes for the Go
 * executor.
 *
 * Three prefixes name a module descriptor at a resource root. `kotlin.` is the third one, and KTIJ-29799 owns it.
 */
internal fun toLoadPath(href: String): String {
  return when {
    href.startsWith("/") -> href.substring(1)
    href.startsWith("intellij.") || href.startsWith("fleet.") || href.startsWith("kotlin.") -> href
    else -> "META-INF/$href"
  }
}

/**
 * The report's library descriptors as `container label` to its load paths, or `null` when this target may not exist.
 *
 * The container and not one jar of it, for the reason `computeLibraryContainerLabels` gives: a per-jar label carries the
 * artifact version, so a Maven bump rewrote every checked-in file that named the jar. The rule expands the container
 * back into its jars. A container that answers two load paths states both, separated by a space.
 */
private fun libraryDescriptorLabels(
  module: ModuleDescriptor,
  section: PluginDescriptorReportSection,
  context: BazelBuildFileGenerator,
): Map<String, String>? {
  if (section.libraryDescriptors.isEmpty()) {
    return emptyMap()
  }
  val result = LinkedHashMap<String, MutableList<String>>()
  for (row in section.libraryDescriptors) {
    val library = context.getLibraryByJpsIdentity(jpsName = row.library, moduleLibraryModuleName = row.module)
                  ?: context.getLibraryByJpsIdentity(jpsName = row.library, moduleLibraryModuleName = null)
    if (library == null) {
      println("WARN: ${module.module.name} descriptor target: no Bazel target for library `${row.library}` of ${row.module}")
      continue
    }
    val containerLabel = libraryTargetLabel(
      library = library,
      communityRoot = context.communityRoot,
      ultimateRoot = context.ultimateRoot,
      isCommunityDependent = module.isCommunity,
    )
    if (module.isCommunity && containerLabel.substringBefore("//") !in nameableLibraryRepositories(context)) {
      return null
    }
    // The rule reads one value as several load paths separated by a space, so a load path that holds one would become
    // two rows and the action would then fail with "no declared jar has the entry".
    check(' ' !in row.loadPath) {
      "The load path '${row.loadPath}' of library '${row.library}' holds a space, and the rule separates load paths by one"
    }
    result.computeIfAbsent(containerLabel) { ArrayList() }.add(row.loadPath)
  }
  return result.mapValues { it.value.joinToString(" ") }
}

/** The label repositories a community package may name a library jar from - the counterpart in `pluginContent.kt`. */
private fun nameableLibraryRepositories(context: BazelBuildFileGenerator): Set<String> {
  return setOf("", "@community", context.getLibraryContainer(isCommunity = true).repoLabel)
}

/**
 * The label of [projectRelativePath], composed from the Bazel package that holds the file.
 *
 * The tree is the authority Bazel itself uses: the first ancestor directory with a `BUILD.bazel` is the package.
 * `containingBazelPackageLabel` of `devDistPluginDescriptorPlan.kt` asks the tree the same question, and the two have to
 * answer the same, because the checked-in plan and this section name one file.
 */
private fun containingPackageLabel(
  projectRelativePath: String,
  isCommunityDependent: Boolean,
  context: BazelBuildFileGenerator,
): String? {
  val inCommunity = projectRelativePath.startsWith(COMMUNITY_PATH_PREFIX)
  val root = (if (inCommunity) context.communityRoot else context.ultimateRoot) ?: return null
  val insideRoot = if (inCommunity) projectRelativePath.removePrefix(COMMUNITY_PATH_PREFIX) else projectRelativePath
  val segments = insideRoot.split('/')
  for (depth in segments.size - 1 downTo 0) {
    val packageSegments = segments.subList(0, depth)
    val packageDirectory = packageSegments.fold(root) { directory, segment -> directory.resolve(segment) }
    if (!packageDirectory.resolve("BUILD.bazel").exists()) {
      continue
    }
    val prefix = when {
      inCommunity && !isCommunityDependent -> "@community//" + packageSegments.joinToString("/")
      else -> "//" + packageSegments.joinToString("/")
    }
    return prefix + ":" + segments.subList(depth, segments.size).joinToString("/")
  }
  return null
}

