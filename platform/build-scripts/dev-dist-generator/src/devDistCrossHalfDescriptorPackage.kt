// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.TreeMap
import java.util.TreeSet

/** One declared label and an immutable snapshot of the entry its owner consumed. This records no action or producer. */
internal class DevDistDescriptorDeclaration(
  @JvmField val label: String,
  entry: PluginDescriptorEntry,
) {
  @JvmField val entry: PluginDescriptorEntry = snapshotDescriptorEntry(entry)
  private val state = descriptorEntryState(this.entry)

  internal fun requireUnchanged(entry: PluginDescriptorEntry) {
    check(state == descriptorEntryState(this.entry) && state == descriptorEntryState(entry)) {
      "The descriptor entry for '$label' changed after its declaration"
    }
  }
}

private fun <T> immutableDescriptorList(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

private fun snapshotDescriptorEntry(entry: PluginDescriptorEntry): PluginDescriptorEntry {
  return PluginDescriptorEntry(
    mainModule = entry.mainModule,
    variant = entry.variant,
    moduleTarget = entry.moduleTarget,
    descriptor = entry.descriptor,
    descriptorInTestOutput = entry.descriptorInTestOutput,
    refusedContentModules = immutableDescriptorList(entry.refusedContentModules),
    separateJar = immutableDescriptorList(entry.separateJar),
    descriptors = immutableDescriptorList(entry.descriptors.map { DeclaredDescriptor(it.loadPath, it.label, it.testOutput, it.moduleName) }),
    includeDescriptors = immutableDescriptorList(entry.includeDescriptors.map { DescriptorPathRow(it.loadPath, it.relativePath) }),
    libraryDescriptorRows = immutableDescriptorList(entry.libraryDescriptorRows.map { LibraryDescriptorRow(it.loadPath, it.moduleName, it.libraryName) }),
    libraryDescriptors = immutableDescriptorList(entry.libraryDescriptors.map { DeclaredLibraryDescriptor(it.loadPath, it.containerLabel) }),
    markers = immutableDescriptorList(entry.markers),
    versionSuffix = entry.versionSuffix,
    compatibleBuildRange = entry.compatibleBuildRange,
    derivesOsArchStamps = entry.derivesOsArchStamps,
    embedsContentModules = entry.embedsContentModules,
    exactVersion = entry.exactVersion,
    retainProductDescriptor = entry.retainProductDescriptor,
    directoryName = entry.directoryName,
    layout = entry.layout,
    contentModules = immutableDescriptorList(entry.contentModules.map { DeclaredContentModule(it.name, it.isOptional, it.loading) }),
    modeRefusedContentModules = Collections.unmodifiableMap(entry.modeRefusedContentModules.mapValuesTo(TreeMap()) { immutableDescriptorList(it.value) }),
  )
}

/**
 * The directory that holds one Bazel package per cross-half descriptor leaf.
 *
 * Beside `//build/dev-dist-descriptors` itself, which declares the product's set targets. A subpackage rather than a
 * target of that package, because that package loads the plan file, and a leaf must not be re-analysed when another
 * plugin's plan row moves.
 */
private const val CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT: String = CROSS_HALF_PACKAGE_ROOT

/**
 * What a `dev_dist_plugin_descriptor` target's name ends in.
 *
 * Three owners spell it: `dev_dist_plugin_descriptor_target_name` of `dev_dist_plugin_descriptor.bzl`, which composes
 * the name, `DEV_DESCRIPTOR_TARGET_SUFFIX` of `devDistBazelIndex.kt`, and this one.
 */
private const val DESCRIPTOR_TARGET_SUFFIX: String = "_dev_descriptor"

/** One plan entry's `dev_dist_plugin_descriptor` target name, which carries the plugin and the layout variant. */
private fun descriptorTargetName(entry: PluginDescriptorEntry): String = when {
  entry.variant.isEmpty() -> entry.mainModule + DESCRIPTOR_TARGET_SUFFIX
  else -> "${entry.mainModule}_${entry.variant}$DESCRIPTOR_TARGET_SUFFIX"
}

/**
 * Which cross-half descriptor packages one run states, and which packages on disk no plugin needs any more.
 *
 * [files] is keyed by project-relative path. [stale] lists every package under the root that [files] does not hold.
 * The caller deletes them. [planLabel] spells a label in the recorded form for a package of the half of the run, see
 * [DevDistBazelIndex.planLabel].
 */
internal class CrossHalfDescriptorPackages(
  private val planLabel: (String) -> String,
  /** The half of the run. Its header opens each package file. */
  private val half: DevDistHalf,
  /** The rendered descriptor targets of each package, keyed by project-relative path. */
  private val descriptorTargets: Map<String, List<String>>,
  /** The declaration of every baseline entry a plugin package holds, keyed by [planEntryKey]. */
  private val shared: Map<String, DevDistDescriptorDeclaration>,
  /** The declaration of every divergent entry, keyed by the product and then by [planEntryKey]. */
  private val perProduct: Map<String, Map<String, DevDistDescriptorDeclaration>>,
) {
  /** The declaration the plugin package holds for [entry], or `null` when the plugin's own package declares the leaf. */
  fun sharedDeclaration(entry: PluginDescriptorEntry): DevDistDescriptorDeclaration? = shared.get(planEntryKey(entry))

  /** The declaration the product package of [product] holds for [entry], or `null` when [product] is in the baseline class. */
  fun productDeclaration(product: String, entry: PluginDescriptorEntry): DevDistDescriptorDeclaration? {
    return perProduct.get(product)?.get(planEntryKey(entry))
  }

  /**
   * Every package file with its final text. [pluginTargets] adds the rendered `dev_plugin` target of a simple plugin to
   * its package, keyed by the package path. [complexPluginCalls] adds the rendered `dev_dist_complex_plugin` calls of a
   * community complex plugin to its plugin package, keyed the same way. A package without a descriptor leaf gets one
   * for the target or the calls.
   */
  fun files(pluginTargets: Map<String, String>, complexPluginCalls: Map<String, String> = emptyMap()): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val paths = LinkedHashSet(descriptorTargets.keys)
    paths.addAll(pluginTargets.keys)
    paths.addAll(complexPluginCalls.keys)
    for (path in paths.sorted()) {
      val calls = complexPluginCalls.get(path)
      check(calls == null || productOfPackagePath(path) == null) { "The product package '$path' cannot hold a complex plugin call" }
      check(calls == null || !pluginTargets.containsKey(path)) { "The package '$path' holds a dev_plugin target and a complex plugin call" }
      result.put(path, renderCrossHalfPackage(
        planLabel = planLabel,
        half = half,
        product = productOfPackagePath(path),
        descriptorTargets = descriptorTargets.get(path).orEmpty(),
        pluginTarget = pluginTargets.get(path),
        complexPluginCalls = calls,
      ))
    }
    return Collections.unmodifiableMap(result)
  }

  /**
   * The path of every package under [CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT] of [projectRoot] that [files] does not hold,
   * sorted. That is the package of a plugin whose own `BUILD.bazel` now declares the leaf and whose call moved into its
   * own section. It is also the product package of a product that states the plugin like the baseline again. And it is
   * every package of a product that left the split products. The root package itself is never listed.
   */
  fun stale(projectRoot: Path, pluginTargets: Map<String, String>, complexPluginCalls: Map<String, String> = emptyMap()): List<String> {
    val present = files(pluginTargets, complexPluginCalls).keys
    return crossHalfDescriptorPackagesOnDisk(projectRoot).filterNot { it in present }.sorted()
  }
}

/** The project-relative path of every plugin package and every product package on disk, in no order. */
private fun crossHalfDescriptorPackagesOnDisk(projectRoot: Path): List<String> {
  val root = projectRoot.resolve(CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT)
  if (!Files.isDirectory(root)) {
    return emptyList()
  }
  val result = ArrayList<String>()
  for (moduleDirectory in listDirectories(root)) {
    val mainModule = moduleDirectory.fileName.toString()
    if (Files.isRegularFile(moduleDirectory.resolve("BUILD.bazel"))) {
      result.add(crossHalfPackagePath(mainModule = mainModule, product = null))
    }
    for (productDirectory in listDirectories(moduleDirectory)) {
      if (Files.isRegularFile(productDirectory.resolve("BUILD.bazel"))) {
        result.add(crossHalfPackagePath(mainModule = mainModule, product = productDirectory.fileName.toString()))
      }
    }
  }
  return result
}

private fun listDirectories(directory: Path): List<Path> {
  return Files.list(directory).use { entries -> entries.filter { Files.isDirectory(it) }.toList() }
}

/**
 * The `dev_dist_plugin_descriptor` target of every plugin whose own Bazel package cannot declare one, and of every
 * product that states a plugin differently from the baseline product.
 *
 * ### Why a package here at all
 *
 * Every other bundled plugin declares this target in its own `BUILD.bazel`, in the `dev` section the
 * JPS-to-Bazel converter writes. About 35 plugins cannot. Most are community plugins, and their patch reads a file
 * that only the main repository can name. The converter generates and sweeps BUILD files one repository half at a time,
 * so a community package that named an ultimate label would be a section a community-only checkout cannot compute.
 * A descriptor also cannot be completed by a second target the way plugin content can, because one action writes one
 * file.
 *
 * So the leaf moves to the one half that can name both repositories, and the plan generator writes it. Which plugins
 * those are is not derived here: [DevDistToolVerdicts.ownDescriptorTarget] carries the converter's own answer, and the
 * complement is this set.
 *
 * ### Why one package per plugin
 *
 * `intellij.java.plugin` and `intellij.kotlin.plugin` are among the most edited plugins of this repository. One shared
 * file would put all 25 into one merge-conflict domain, which is the property this whole arc removes. One package per
 * plugin gives each one its own file, and [CrossHalfDescriptorPackages.stale] sweeps a package per directory.
 *
 * ### Why a package per divergent class
 *
 * One leaf produces one text. A product outside the baseline class of [classes] needs a leaf other than the baseline's,
 * and every product of its class reads the same one. A `dev` section must not name a product, because a community
 * section cannot name an ultimate one. So the leaf goes into `build/dev-dist-descriptors/<module>/<product>/BUILD.bazel`
 * under its plain name, whatever half the plugin is in, and `<product>` is the home of the class
 * ([DescriptorResidueClasses.home]).
 *
 * A declaration keeps the labels of its entry in the recorded form. [planLabel] spells them for a package of the half
 * of the run when a leaf renders, see [DevDistBazelIndex.planLabel]. The header of [half] opens each package file.
 * [bridgeLabel] gives the entry of a module name in the descriptor index of the bridge of [half], see
 * [bridgeDescriptorLabel]. A leaf states no row that it derives from that index.
 */
internal fun collectCrossHalfDescriptorPackages(
  verdicts: DevDistToolVerdicts,
  classes: Map<String, DescriptorResidueClasses>,
  planLabel: (String) -> String,
  bridgeLabel: (moduleName: String) -> String?,
  half: DevDistHalf,
): CrossHalfDescriptorPackages {
  val targetsByPackage = TreeMap<String, MutableList<CrossHalfDescriptorTarget>>()
  val shared = LinkedHashMap<String, DevDistDescriptorDeclaration>()
  val perProduct = TreeMap<String, MutableMap<String, DevDistDescriptorDeclaration>>()
  for ((key, residue) in classes) {
    val baseline = residue.baseline
    // One leaf per (plugin, variant), and one plugin's leaves in one package. Equal entries of several products render
    // this one leaf once.
    if (verdicts.ownDescriptorTarget(mainModule = baseline.mainModule, variant = baseline.variant) == null) {
      val target = CrossHalfDescriptorTarget(entry = baseline, product = null)
      check(shared.put(key, target.declaration) == null) { "Duplicate descriptor declaration for '$key'" }
      targetsByPackage.computeIfAbsent(crossHalfPackagePath(baseline.mainModule, product = null)) { ArrayList() }.add(target)
    }
    // One leaf per divergent class, in the product package of its home. Every product of the class states the same
    // residue, so each one reads that leaf through a declaration of its own entry.
    for (divergentClass in residue.classes.drop(1)) {
      val home = divergentClass.first()
      val target = CrossHalfDescriptorTarget(entry = home.entry, product = home.product)
      targetsByPackage.computeIfAbsent(crossHalfPackagePath(home.entry.mainModule, product = home.product)) { ArrayList() }.add(target)
      for (member in divergentClass) {
        val declaration = DevDistDescriptorDeclaration(label = target.declaration.label, entry = member.entry)
        check(perProduct.computeIfAbsent(member.product) { LinkedHashMap() }.put(key, declaration) == null) {
          "Duplicate descriptor declaration for '$key' of '${member.product}'"
        }
      }
    }
  }
  val files = LinkedHashMap<String, List<String>>()
  for ((path, targets) in targetsByPackage) {
    files.put(path, targets.sortedBy { it.declaration.entry.variant }.map { it.render(planLabel = planLabel, bridgeLabel = bridgeLabel) })
  }
  return CrossHalfDescriptorPackages(
    planLabel = planLabel,
    half = half,
    descriptorTargets = Collections.unmodifiableMap(files),
    shared = Collections.unmodifiableMap(shared),
    perProduct = Collections.unmodifiableMap(perProduct.mapValues { Collections.unmodifiableMap(it.value) }),
  )
}

/**
 * The project-relative path of one cross-half descriptor package: the plugin package for a `null` [product], and the
 * product package of a divergent product otherwise.
 */
internal fun crossHalfPackagePath(mainModule: String, product: String?): String = "${crossHalfPackageDirectory(mainModule, product)}/BUILD.bazel"

/** The label of the target [name] in the package [crossHalfPackagePath] names. */
internal fun crossHalfPackageLabel(mainModule: String, product: String?, name: String): String = "//${crossHalfPackageDirectory(mainModule, product)}:$name"

/** The project-relative directory of the package [crossHalfPackagePath] names. A plan home of a community plugin reuses it. */
internal fun crossHalfPackageDirectory(mainModule: String, product: String?): String {
  return if (product == null) "$CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT/$mainModule" else "$CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT/$mainModule/$product"
}

/** The product of a product package path, or `null` for a plugin package path. */
private fun productOfPackagePath(path: String): String? {
  val segments = path.removePrefix("$CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT/").removeSuffix("/BUILD.bazel").split('/')
  return when (segments.size) {
    1 -> null
    2 -> segments[1]
    else -> error("'$path' is no cross-half descriptor package path")
  }
}

/**
 * One cross-half package. [complexPluginCalls] is the rendered `dev_dist_complex_plugin` calls of a community complex
 * plugin, or `null`. A call names the product info of the main repository, so the community section cannot hold it.
 * The plan file it reads sits in the community package, exported, or in this package. [planLabel] spells the label of a
 * load line for the package.
 */
private fun renderCrossHalfPackage(
  planLabel: (String) -> String,
  half: DevDistHalf,
  product: String?,
  descriptorTargets: List<String>,
  pluginTarget: String?,
  complexPluginCalls: String?,
): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  when {
    product == null -> {
      append("# The dev-distribution targets of one plugin, in a package of its own.\n")
      append("#\n")
      append("# A bundled plugin normally declares these targets in its own `BUILD.bazel`. This plugin cannot: its `dev`\n")
      append("# section holds no production descriptor, or it is a community plugin whose descriptor patch, packaging or\n")
      append("# plugin chain reads a file only the main repository can name, so the community half of the JPS-to-Bazel\n")
      append("# converter emits no section for it. One package per plugin, because a shared file would put the most edited\n")
      append("# plugins of this repository into one merge-conflict domain.\n")
    }
    else -> {
      append("# The dev-distribution targets of one plugin for the product `").append(product).append("` and every product that\n")
      append("# states the plugin like it, in a package of its own.\n")
      append("#\n")
      append("# These products state the plugin's descriptor differently from the baseline product, and one leaf produces one\n")
      append("# text. So their leaf lives here under the plain name, and so does the `dev_plugin` of every one of them that\n")
      append("# packs the plugin like `").append(product).append("`. A `dev` section cannot hold it, because a community section must not\n")
      append("# name a product of the main repository.\n")
    }
  }
  append("#\n")
  append("# Every target is `manual`, which the macros add. A set target names the descriptor and states the product of\n")
  append("# the stamps. The component index names the `dev_plugin` target or the component of a `dev_dist_complex_plugin`\n")
  append("# call.\n")
  if (descriptorTargets.isNotEmpty()) {
    append("#\n")
    append("# A descriptor leaf derives its conventional descriptor rows from the bridge index of its half.\n")
  }
  append("\n")
  // The bridge of the half exports `dev_dist_plugin_descriptor` bound to its descriptor index.
  val loads = ArrayList<Pair<String, String>>()
  if (descriptorTargets.isNotEmpty()) {
    loads.add("@${half.jpsBridge}//:targets.bzl" to "dev_dist_plugin_descriptor")
  }
  if (pluginTarget != null) {
    loads.add(planLabel(DEV_PLUGIN_RULE) to "dev_plugin")
  }
  if (complexPluginCalls != null) {
    loads.add(planLabel(DEV_PLUGIN_REMAINDER_RULE) to "dev_dist_complex_plugin")
  }
  // The load lines in the order buildifier sorts them: a file of an explicit repository before a file of this one.
  val loadOrder = BazelLabelComparator(forLoadStatements = true)
  for ((file, symbol) in loads.sortedWith { a, b -> loadOrder.compare(a.first, b.first) }) {
    append("load(\"").append(file).append("\", \"").append(symbol).append("\")\n")
  }
  for (target in descriptorTargets) {
    append("\n")
    append(target)
  }
  if (pluginTarget != null) {
    append("\n")
    append(pluginTarget)
  }
  if (complexPluginCalls != null) {
    append("\n")
    append(complexPluginCalls)
  }
}

/**
 * The `dev_plugin` target of a cross-half simple plugin. Every label is explicit, because the ultimate package has no
 * JPS bridge map for community modules. Every label is spelled for a package of the half of [index], see
 * [DevDistBazelIndex.planLabel]. The component index names this target. [contentModuleJarLabel] gives the label of the
 * jar of a reused module, which is a label of the product package of the ultimate half for a relocated call, see
 * [DevDistBuildSections.relocatedContentModuleJarCalls].
 *
 * `modules` names the production target of each module token. `test_module_jars` names the test jar of each module of
 * [DevDistSimplePackaging.testModules] instead.
 */
internal fun renderCrossHalfDevPluginTarget(
  packaging: DevDistSimplePackaging,
  descriptorLabel: String,
  index: DevDistBazelIndex,
  contentModuleJarLabel: (String) -> String? = { index.contentModuleJarLabel(it, dependentIsCommunity = index.planPackageIsCommunity) },
): String = buildString {
  val mainModule = packaging.mainModule
  fun moduleLabel(module: String): String {
    return index.dependencyLabel(module = module, dependentIsCommunity = index.planPackageIsCommunity)
           ?: error("Module '$module' of '$mainModule' has no Bazel target")
  }
  append("dev_plugin(\n")
  appendStarlarkString(name = "name", value = mainModule + DEV_PLUGIN_TARGET_SUFFIX)
  appendStarlarkStringList(name = "classpath_jars", values = packaging.classpathJars.map(index::planLabel))
  appendStarlarkStringList(
    name = "content_module_jars",
    values = packaging.reusedModules.map { contentModuleJarLabel(it) ?: error("Module '$it' has no content_module_jar label") }.sorted(),
  )
  appendStarlarkString(name = "descriptor", value = index.planLabel(descriptorLabel))
  appendStarlarkStringList(name = "executable_files", values = packaging.executableFiles.sorted())
  appendStarlarkStringDict(name = "file_prefixes", rows = packaging.filePrefixes)
  appendStarlarkStringDict(name = "files", rows = packaging.files.mapValues { index.planLabel(it.value) })
  append("    jars = {\n")
  for ((destination, tokens) in packaging.jars) {
    append("        \"").append(destination).append("\": [\n")
    for (token in tokens) {
      append("            \"").append(if (isLabelToken(token)) index.planLabel(token) else token).append("\",\n")
    }
    append("        ],\n")
  }
  append("    },\n")
  appendStarlarkStringList(name = "libraries", values = packaging.labelTokens.sorted().map(index::planLabel))
  appendStarlarkString(name = "main_module", value = mainModule)
  appendStarlarkStringDict(name = "module_jar_paths", rows = packaging.moduleJarPaths)
  val testModules = packaging.testModules.toSet()
  appendStarlarkStringDict(name = "modules", rows = packaging.moduleTokens.filter { it !in testModules }.associateBy(::moduleLabel))
  appendStarlarkString(name = "plugin_directory", value = "plugins/${packaging.pluginDirectory}")
  appendStarlarkStringDict(
    name = "test_module_jars",
    rows = packaging.testModules.associateBy { module ->
      val jar = testModuleJarTarget(module = module, targets = index.targets)
                ?: error("Module '$module' of '$mainModule' has no single test jar")
      index.planLabel(jar)
    },
  )
  appendStarlarkStringList(name = "tree_files", values = packaging.treeFiles.sorted())
  append(")\n")
}

/** One leaf and its declaration. [product] is `null` for a leaf of the plugin package. */
private class CrossHalfDescriptorTarget(entry: PluginDescriptorEntry, product: String?) {
  val declaration = DevDistDescriptorDeclaration(
    label = crossHalfPackageLabel(mainModule = entry.mainModule, product = product, name = descriptorTargetName(entry)),
    entry = entry,
  )

  /**
   * The leaf with every label spelled by [planLabel].
   *
   * The leaf names the declared content modules of the plugin. It derives the row of each one [bridgeLabel] knows, so
   * `descriptors` states only the other rows, see [isBridgeDerivedDescriptor].
   */
  fun render(planLabel: (String) -> String, bridgeLabel: (moduleName: String) -> String?): String = buildString {
    val entry = declaration.entry
    val contentModules = entry.contentModules.mapTo(TreeSet()) { it.name }
    append("dev_dist_plugin_descriptor(\n")
    appendStarlarkStringList(name = "content_modules", values = contentModules.toList())
    if (entry.descriptorInTestOutput) {
      appendStarlarkString(name = "descriptor_entry", value = entry.descriptor)
      appendStarlarkString(name = "descriptor_jar", value = planLabel(entry.moduleTarget + ".jar"))
    }
    else {
      appendStarlarkString(name = "descriptor", value = planLabel(entry.moduleTarget.substringBeforeLast(':') + ":" + entry.descriptor))
    }
    appendStarlarkStringDict(
      name = "descriptor_jars",
      rows = entry.descriptors.filter(DeclaredDescriptor::testOutput).associate { planLabel(it.label) to it.loadPath },
    )
    val statedDescriptors = entry.descriptors.filterNot { declared ->
      declared.testOutput ||
      isBridgeDerivedDescriptor(label = declared.label, loadPath = declared.loadPath, contentModules = contentModules, bridgeLabel = bridgeLabel)
    }
    appendStarlarkStringDict(name = "descriptors", rows = statedDescriptors.associate { planLabel(it.label) to it.loadPath })
    if (!entry.embedsContentModules) {
      append("    embed_content_modules = False,\n")
    }
    if (entry.exactVersion) {
      append("    exact_version = True,\n")
    }
    // One row per library container, and a container that answers two load paths states both, space separated. The rule
    // takes the container because a per-jar label carries the artifact version. A load path that holds a space would
    // become two rows, so it is refused where the value is composed.
    for (declared in entry.libraryDescriptors) {
      check(' ' !in declared.loadPath) {
        "The load path '${declared.loadPath}' of '${entry.mainModule}' holds a space," +
        " and the rule separates load paths by one"
      }
    }
    appendStarlarkStringDict(
      name = "library_descriptors",
      rows = entry.libraryDescriptors.groupBy { planLabel(it.containerLabel) }
        .mapValues { (_, declared) -> declared.joinToString(" ") { it.loadPath } },
    )
    appendStarlarkString(name = "main_module", value = entry.mainModule)
    // A variant of one operating system and one architecture states no row and no version suffix. The leaf derives both
    // from the product's `marketplace_names`, which reaches it through the configuration.
    if (!entry.derivesOsArchStamps) {
      appendStarlarkStringList(name = "markers", values = entry.markers)
    }
    appendStarlarkStringListDict(name = "mode_refused_content_modules", rows = entry.modeRefusedContentModules)
    appendStarlarkStringList(name = "refused_content_modules", values = entry.refusedContentModules)
    if (entry.retainProductDescriptor) {
      append("    retain_product_descriptor = True,\n")
    }
    appendStarlarkStringList(name = "separate_jar", values = entry.separateJar)
    if (entry.variant.isNotEmpty()) {
      appendStarlarkString(name = "variant", value = entry.variant)
    }
    if (!entry.derivesOsArchStamps && entry.versionSuffix.isNotEmpty()) {
      appendStarlarkString(name = "version_suffix", value = entry.versionSuffix)
    }
    if (entry.compatibleBuildRange != null) {
      appendStarlarkString(name = "compatible_build_range", value = entry.compatibleBuildRange)
    }
    append(")\n")
  }
}

private fun StringBuilder.appendStarlarkString(name: String, value: String) {
  append("    ").append(name).append(" = ").append(quoteStarlarkString(value)).append(",\n")
}

/** One list attribute. A marker row carries the raw descriptor text, so every value renders through the quote. */
private fun StringBuilder.appendStarlarkStringList(name: String, values: List<String>) {
  if (values.isEmpty()) {
    return
  }
  append("    ").append(name).append(" = [\n")
  for (value in values) {
    append("        ").append(quoteStarlarkString(value)).append(",\n")
  }
  append("    ],\n")
}

/** One dict attribute, keyed the way the rule wants it and sorted, which is the form the formatter leaves alone. */
/** A dict of string lists in buildifier form, or nothing for an empty dict. */
private fun StringBuilder.appendStarlarkStringListDict(name: String, rows: Map<String, List<String>>) {
  if (rows.isEmpty()) return
  append("    ").append(name).append(" = {\n")
  for ((key, values) in rows.toSortedMap()) {
    append("        ").append(quoteStarlarkString(key)).append(": ")
    if (values.size <= 1) {
      append(values.joinToString(", ", "[", "]", transform = ::quoteStarlarkString)).append(",\n")
    }
    else {
      append("[\n")
      for (value in values) append("            ").append(quoteStarlarkString(value)).append(",\n")
      append("        ],\n")
    }
  }
  append("    },\n")
}

private fun StringBuilder.appendStarlarkStringDict(name: String, rows: Map<String, String>) {
  if (rows.isEmpty()) {
    return
  }
  append("    ").append(name).append(" = {\n")
  for (key in rows.keys.sorted()) {
    append("        ").append(quoteStarlarkString(key)).append(": ").append(quoteStarlarkString(rows.getValue(key))).append(",\n")
  }
  append("    },\n")
}
