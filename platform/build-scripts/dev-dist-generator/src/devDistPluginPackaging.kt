@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.impl.PluginLayout
import java.nio.file.Path
import java.util.Collections

/**
 * The plans of one plugin in one product, with the platform axis folded.
 *
 * [records] and [layouts] are keyed by the variant id. A plugin whose six platform plans agree holds one record keyed
 * by the empty string. A plugin with a platform-specific asset or layout holds one record per platform.
 */
internal class DevDistPluginPlanEntry(
  @JvmField val product: String,
  @JvmField val tier: DevDistPluginTier,
  @JvmField val mainModule: String,
  layouts: Map<String, PluginLayout>,
  records: Map<String, DevDistPluginPlanRecord>,
) {
  @JvmField val layouts: Map<String, PluginLayout> = Collections.unmodifiableMap(LinkedHashMap(layouts))
  @JvmField val records: Map<String, DevDistPluginPlanRecord> = Collections.unmodifiableMap(LinkedHashMap(records))

  init {
    require(layouts.keys == records.keys && records.isNotEmpty()) { "Plugin '$mainModule' has records and layouts for different variants" }
    require(layouts.values.all { it.mainModule == mainModule }) { "Plugin '$mainModule' has a layout of another plugin" }
  }

  val isNeutral: Boolean
    get() = records.keys == setOf("")

  fun key(variant: String): DevDistPluginPlanKey = DevDistPluginPlanKey(product, mainModule, variant)

  fun layout(variant: String): PluginLayout = requireNotNull(layouts.get(variant)) { "Plugin '$mainModule' has no layout for variant '$variant'" }
}

/**
 * The packaging of a plugin that Bazel declares in Starlark and packs without a plan file.
 *
 * [jars] maps each packed destination to its ordered source tokens. A token is a JPS module name, a library container
 * label, or one jar file label. Labels use the ultimate spelling. [moduleJarPaths] names the destination of a reused
 * content module jar that is not `lib/modules/<module>.jar`. [reusedModules] are the content modules whose
 * `content_module_jar` jar the plugin takes as is.
 *
 * [files] maps the destination of each verbatim copy to the label of its source, in the ultimate spelling. A destination
 * in [filePrefixes] is a directory copy, and the value is the repository-relative prefix of the source tree. Every other
 * destination is a single file, and [executableFiles] names the ones of mode 493.
 */
internal class DevDistSimplePackaging(
  @JvmField val mainModule: String,
  @JvmField val pluginDirectory: String,
  @JvmField val jars: Map<String, List<String>>,
  @JvmField val moduleJarPaths: Map<String, String>,
  @JvmField val reusedModules: List<String>,
  /** Whether the plugin's own package cannot state a token, so the ultimate cross-half package declares the packaging. */
  @JvmField val crossHalf: Boolean,
  /**
   * The label of each reused jar whose `content_module_jar` call is relocated, keyed by module and sorted. The own
   * section states it, because the macro derives any other label from the package of the module.
   */
  @JvmField val contentModuleJarLabels: Map<String, String>,
  /**
   * The classpath order of every jar, or empty when the rule's default order sorts to the same classpath.
   * The default order is the packed jars in `jars` order, then the reused jars in their declaration order.
   */
  @JvmField val classpathJars: List<String>,
  @JvmField val files: Map<String, String>,
  @JvmField val filePrefixes: Map<String, String>,
  @JvmField val executableFiles: List<String>,
) {
  /** Every module a token names, the main module included. */
  val moduleTokens: List<String>
    get() = jars.values.flatten().filter { !isLabelToken(it) }.distinct()

  /** Every library container or jar file label a token names. */
  val labelTokens: List<String>
    get() = jars.values.flatten().filter(::isLabelToken).distinct()

  /** Every label a copy reads, in the ultimate spelling. */
  val fileLabels: List<String>
    get() = files.values.distinct()
}

internal fun isLabelToken(token: String): Boolean = "//" in token

/** The plugin directory the descriptor rule derives when the layout states none. */
internal fun derivedPluginDirectoryName(mainModule: String): String = mainModule.removePrefix("intellij.").replace('.', '-')

/** The writer every dev-distribution jar takes: entities merged, a manifest kept only for one meaningful source. */
private val DEFAULT_WRITER = JarWriterRecipe(mergeEntities = true)

/**
 * Classifies one folded plan as simple, or returns `null` for a plugin that keeps its plan file.
 *
 * Simple means: one neutral record, no preparation, every asset a jar of mode 420 on the classpath or a plain copy, the
 * default writer, sources that are single-root modules, library containers, single archives and one descriptor patch,
 * and a reuse set that covers the section's content modules minus the modules the jars name. The last rule lets the
 * macro infer reuse from `content_modules` without a second list. A plugin that reuses more is declared cross-half.
 * A plain copy is a `withResource*` file or directory, or a one-file layout callback, see [plainCopy].
 * No destination sits below another, because the rule refuses a copy that overlaps a jar or another copy.
 *
 * [baseline] says whether the product is in the baseline residue class of the plugin. A divergent product states its
 * packaging in its product package, which lists every reused jar, so its reuse set may cover fewer content modules than
 * the section names: a frontend product packs no backend content module.
 *
 * [refusedContentModules] are content modules the baseline descriptor refuses. They stay in the section list, and the
 * packer does not ship them. They are not part of the reuse set the section must cover.
 *
 * [relocatedModules] are the modules whose `content_module_jar` call sits in the product package of the ultimate half,
 * see [DevDistBuildSections.relocatedContentModuleJarCalls]. An ultimate section states the label of such a jar in
 * [DevDistSimplePackaging.contentModuleJarLabels]. A community section cannot name that package, so a community plugin
 * that reuses such a jar is declared cross-half. [contentModuleJarLabel] gives the label of the jar of a reused module.
 */
internal fun classifySimplePluginPackaging(
  entry: DevDistPluginPlanEntry,
  descriptorInput: String,
  contentModuleNames: List<String>,
  ownDescriptorDeclared: Boolean,
  contentModuleJarModules: Set<String>,
  index: DevDistBazelIndex,
  baseline: Boolean,
  refusedContentModules: Set<String> = emptySet(),
  relocatedModules: Set<String> = emptySet(),
  contentModuleJarLabel: (String) -> String? = { index.contentModuleJarLabel(it, dependentIsCommunity = index.planPackageIsCommunity) },
): DevDistSimplePackaging? {
  if (!entry.isNeutral) return null
  val record = entry.records.getValue("")
  val plan = record.plan
  val projection = plan.projection
  if (projection.preparations.isNotEmpty() || projection.preparationRoots.isNotEmpty() || plan.catalogue.testModules.isNotEmpty()) return null
  val mainModule = entry.mainModule
  val jars = LinkedHashMap<String, List<String>>()
  val moduleJarPaths = LinkedHashMap<String, String>()
  val reused = ArrayList<String>()
  val files = LinkedHashMap<String, String>()
  val filePrefixes = LinkedHashMap<String, String>()
  val executableFiles = ArrayList<String>()
  val rawInputs = plan.requiredRawInputs.associateBy { it.id }
  var descriptorJars = 0
  for (planned in plan.selectedPlan().assets) {
    val asset = planned.asset
    val recipe = asset.recipe
    if (recipe == null) {
      val copy = plainCopy(asset, rawInputs) ?: return null
      files.put(copy.destination, copy.label)
      copy.prefix?.let { filePrefixes.put(copy.destination, it) }
      if (copy.executable) executableFiles.add(copy.destination)
      continue
    }
    if (asset.kind != "file" || asset.mode != 420 || asset.symlinkTarget != null || !asset.classPath) return null
    val artifact = planned.artifact
    if (artifact != null) {
      val owner = artifact.module.takeIf { it in contentModuleJarModules } ?: return null
      if (asset.destination != "lib/modules/$owner.jar") moduleJarPaths.put(owner, asset.destination)
      reused.add(owner)
      continue
    }
    if (recipe.writer != DEFAULT_WRITER) return null
    val tokens = ArrayList<String>()
    var sawLibrary = false
    var hasDescriptor = false
    for ((position, source) in recipe.sources.withIndex()) {
      if (source.options.isNotEmpty() && source.kind != "file") return null
      if (source.preparedManifest != null) return null
      when (source.kind) {
        "module" -> {
          if (source.filter != "module-v1" || source.entry.isNotEmpty() || ':' in source.input) return null
          if (index.location(source.input) == null) return null
          // The writer puts every module output before the libraries, so a module after a library is not a token order.
          if (sawLibrary) return null
          tokens.add(source.input)
        }
        "library", "archive" -> {
          if (source.filter != "library-v1" || source.entry.isNotEmpty() || !isLabelToken(source.input)) return null
          sawLibrary = true
          if (source.kind == "archive" && !source.input.endsWith(".jar")) return null
          if (source.kind == "library" && source.input.endsWith(".jar")) return null
          tokens.add(source.input)
        }
        "file" -> {
          val next = recipe.sources.getOrNull(position + 1)
          if (source.input != descriptorInput || source.entry != PLUGIN_XML_RELATIVE_PATH || source.options != listOf("patch") ||
              next == null || next.kind != "module" || next.input != mainModule) return null
          hasDescriptor = true
        }
        else -> return null
      }
    }
    if (hasDescriptor) descriptorJars++
    if (tokens.isEmpty()) return null
    jars.put(asset.destination, tokens)
  }
  if (descriptorJars != 1) return null
  val moduleTokens = jars.values.flatten().filterTo(LinkedHashSet()) { !isLabelToken(it) }
  if (mainModule !in moduleTokens) return null
  fun reusedDestination(module: String): String = moduleJarPaths.get(module) ?: "lib/modules/$module.jar"
  // The rule refuses a destination below another one: a copy under a copied directory, or a copied directory over a jar.
  val destinations = jars.keys + reused.map(::reusedDestination) + files.keys
  if (destinations.any { below -> destinations.any { above -> below.startsWith("$above/") } }) return null
  // The section infers reuse as its content modules minus the modules the jars name. A reused module the section
  // cannot name is a cross-repository member, and the cross-half package then lists every reused jar explicitly.
  // A refused module stays in the section list and is not packed, so it is not part of the reuse set.
  val sectionReuse = contentModuleNames.filterTo(HashSet()) { it !in moduleTokens && it !in refusedContentModules }
  val reusedSet = reused.toSet()
  if (reused.size != reusedSet.size) return null
  if (baseline && !reusedSet.containsAll(sectionReuse)) return null
  val sectionIsCommunity = index.isCommunity(mainModule) ?: return null
  val labelTokens = jars.values.flatten().filter(::isLabelToken)
  val relocatedReuse = reused.filter { it in relocatedModules }.sorted()
  val crossHalf = !ownDescriptorDeclared ||
                  reusedSet != sectionReuse ||
                  sectionIsCommunity && (
                    relocatedReuse.isNotEmpty() ||
                    moduleTokens.any { index.isCommunity(it) != true } ||
                    labelTokens.any { !index.canName(it, dependentIsCommunity = true) } ||
                    files.values.any { !index.canName(it, dependentIsCommunity = true) }
                  )
  val pluginDirectory = entry.layout("").directoryName
  // The cross-half package lists the reused jars by label, sorted. The own section lists them in content order.
  val declaredReuse = if (crossHalf) {
    reused.sortedBy { contentModuleJarLabel(it) ?: error("Module '$it' has no content_module_jar label") }
  }
  else {
    contentModuleNames.filter { it in reusedSet }
  }
  // A copy never enters the classpath, so only the jars take part in the order.
  val planDestinations = projection.assets.filter { it.classPath }.map { it.destination }
  val planOrder = orderedClasspath(pluginDirectory, planDestinations)
  val defaultOrder = orderedClasspath(pluginDirectory, jars.keys.toList() + declaredReuse.map(::reusedDestination))
  return DevDistSimplePackaging(
    mainModule = mainModule,
    pluginDirectory = pluginDirectory,
    jars = Collections.unmodifiableMap(jars),
    moduleJarPaths = Collections.unmodifiableMap(moduleJarPaths),
    reusedModules = java.util.List.copyOf(reused),
    crossHalf = crossHalf,
    contentModuleJarLabels = if (crossHalf) {
      emptyMap()
    }
    else {
      Collections.unmodifiableMap(relocatedReuse.associateWithTo(LinkedHashMap()) {
        contentModuleJarLabel(it) ?: error("Module '$it' has no content_module_jar label")
      })
    },
    classpathJars = if (planOrder == defaultOrder) emptyList() else java.util.List.copyOf(planDestinations),
    files = Collections.unmodifiableMap(files),
    filePrefixes = Collections.unmodifiableMap(filePrefixes),
    executableFiles = java.util.List.copyOf(executableFiles),
  )
}

/** One verbatim copy of a simple plugin. [prefix] is set for a directory copy. */
private class PlainCopy(
  @JvmField val destination: String,
  @JvmField val label: String,
  @JvmField val prefix: String?,
  @JvmField val executable: Boolean,
)

/**
 * The copy a recipe-free asset states, or `null` when the packer must resolve the asset.
 *
 * A plain file copy is a file of mode 420 or 493 over one raw input that is one file, an archive included, without a
 * prefix. A plain tree copy is a tree that keeps the source modes over one raw directory input with a prefix. A symlink,
 * a transform output and an overlay of two trees are not plain, because a preparation or the packer resolves them.
 */
private fun plainCopy(asset: PluginPackingAsset, rawInputs: Map<String, DevDistPluginRawInput>): PlainCopy? {
  if (asset.recipe != null || asset.symlinkTarget != null || asset.classPath || asset.destination.isEmpty()) {
    return null
  }
  val input = asset.inputs.singleOrNull()?.let(rawInputs::get) ?: return null
  val prefix = input.sourceTreePrefix
  return when (asset.kind) {
    "file" -> {
      if (asset.mode != 420 && asset.mode != 493 || input.kind == "directory" || prefix != null) return null
      PlainCopy(destination = asset.destination, label = input.label, prefix = null, executable = asset.mode == 493)
    }
    "tree" -> {
      if (asset.normalizeTreeModes || input.kind != "directory" || prefix == null) return null
      PlainCopy(destination = asset.destination, label = input.label, prefix = prefix, executable = false)
    }
    else -> null
  }
}

/**
 * The plugin classpath the collector writes for [destinations] given in that input order.
 * Only a `lib/<name>.jar` enters the classpath, so only such jars take part in the order.
 */
private fun orderedClasspath(pluginDirectory: String, destinations: List<String>): List<String> {
  val pluginDir = Path.of("plugins", pluginDirectory)
  val files = destinations.distinct()
    .filter { it.startsWith("lib/") && it.endsWith(".jar") && it.count { character -> character == '/' } == 1 }
    .mapTo(ArrayList()) { pluginDir.resolve(it) }
  if (files.size > 1) {
    putMoreLikelyPluginJarsFirst(pluginDirName = pluginDirectory, filesInLibUnderPluginDir = files)
  }
  return files.map { pluginDir.relativize(it).toString().replace('\\', '/') }
}
