// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.buildScripts.concurrency.withLockInterruptibly
import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.PluginContentReport
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.devDist.DEV_DIST_ON_DEMAND_PLUGIN_MODULES
import org.jetbrains.intellij.build.devDist.DerivedPluginJars
import org.jetbrains.intellij.build.devDist.deriveDevDistPlatformJars
import org.jetbrains.intellij.build.devDist.derivePluginJars
import org.jetbrains.intellij.build.productLayout.discoverAllProducts
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Compares the jars the derivation states for each plugin against the jars this build packed.
 *
 * The derivation reads the layout facts and the JPS model, and never a build; see `derivePluginJars`. This build
 * packed the same plugins from the product layout. The two sides share no producer, so a difference is a real
 * disagreement: the derivation is wrong for that plugin. A comparison of the derivation against itself could not
 * fail, and this one can.
 *
 * The suite derives once. The products come from `build/dev-build.json` through `discoverAllProducts`, and every
 * target of the suite reads the one result.
 *
 * A jar the derivation states and this product did not pack is held out when every member of it is packed in another
 * jar of the same plugin here. Two products can pack one member into two different jars, and the derivation states
 * both, so the absent jar is the other product's and not a defect. A member packed nowhere in this product is reported.
 * A library wrapper module counts as packed where the build wrote its library as a bare jar, because such a module has
 * no output of its own to pack.
 *
 * Only a jar under the plugin's `lib/` that names a module is compared, and the members of a jar are compared as one
 * set. The report splits a member into `modules` or `contentModules` by the inclusion reason the packer recorded,
 * and the derivation states the members in merge order. The jar holds the same module output either way. A bare
 * library jar names no module, and the derivation states none.
 *
 * The libraries of a jar are compared where the derivation states a set: the jars a packing target packs. A jar the
 * derivation states no set for skips the field.
 *
 * A packed plugin the derivation has no jar for is outside the population. The failure names the plugin and the one
 * source-derived population that must include each bundled or compatible published plugin.
 *
 * A difference this target already knows about sits in the target's own divergence table; see [DivergenceTable]. The
 * table is a snapshot of one run, so a run that measures anything else reports the table out of date, and that failure
 * carries a patch over the file.
 */
@Internal
fun createDerivedPluginJarsValidation(targetId: String): PackagingTargetValidationSpec = PackagingTargetValidationSpec(
  targetId = targetId,
  name = "derived-plugin-jars",
  problemMessage = "the derivation states different plugin jars than the build packed",
  validator = { context ->
    validateDerivedPluginJars(
      projectHome = context.projectHome,
      targetId = context.target.id,
      content = context.content(),
      outputProvider = context.outputProvider,
    )
  },
)

private fun validateDerivedPluginJars(
  projectHome: Path,
  targetId: String,
  content: ParsedContentReport,
  outputProvider: ModuleOutputProvider,
): List<PackagingCheckFailure> {
  val records = derivedJarsOf(derivedPluginJars(projectHome = projectHome, outputProvider = outputProvider))
  val reports = content.bundled + content.nonBundled
  val failures = ArrayList<PackagingCheckFailure>()

  val file = divergenceTableFile(projectHome = projectHome, targetId = targetId)
  val currentText = if (file.exists()) file.readText() else ""
  val table = readDivergenceTable(currentText)
  val comparison = compareDerivedPluginJars(records = records, reports = reports, divergences = table.jarsByPlugin)
  failures.addAll(comparison.failures)
  val desiredText = writeDivergenceTable(comments = table.comments, divergences = comparison.measured)
  if (desiredText != currentText) {
    failures.add(createFilePatchFailure(
      name = "divergences-out-of-date: ${projectHome.relativize(file)}",
      file = file,
      projectHome = projectHome,
      currentText = currentText,
      desiredText = desiredText,
      context = DIVERGENCE_TABLE_REPAIR,
    ))
  }
  if (comparison.unrecorded.isNotEmpty()) {
    failures.add(PackagingCheckFailure(
      name = "derived-plugin-jars: plugins outside the population",
      error = AssertionError(
        "This build packed plugins the derivation has no jar for:\n" +
        comparison.unrecorded.joinToString(separator = "\n") { "  $it" } + "\n" + POPULATION_REPAIR
      ),
    ))
  }
  return failures
}

/** The one derivation of a suite, by project home. A suite runs its targets beside each other, and each reads this. */
private val derivations = HashMap<Path, DerivedPluginJars>()
private val derivationsLock = ReentrantLock()

/**
 * The derivation over every product of `build/dev-build.json` under [projectHome], derived once per project home.
 *
 * The product discovery loads every `ProductProperties` class from compiled build modules, which [outputProvider]
 * names. The derivation reads the product declarations and source descriptors.
 */
private fun derivedPluginJars(projectHome: Path, outputProvider: ModuleOutputProvider): DerivedPluginJars {
  return derivationsLock.withLockInterruptibly {
    derivations.getOrPut(projectHome) {
      val products = discoverAllProducts(projectRoot = projectHome, outputProvider = outputProvider)
      derivePluginJars(
        products = products.mapNotNull { it.properties as? ProductProperties },
        extraPopulation = DEV_DIST_ON_DEMAND_PLUGIN_MODULES,
        platformJars = deriveDevDistPlatformJars(products, outputProvider),
        outputProvider = outputProvider,
      )
    }
  }
}

/** The path a plugin's report gives every jar of the plugin, before the path the derivation states. */
private const val LIB_PREFIX = "lib/"

/** The directory of the divergence tables, one file per packaging target. */
private const val DIVERGENCE_TABLE_DIRECTORY = "build/expected/derived-plugin-jars"

/** The divergence table of one packaging target. An absent file and an empty one both state no divergence. */
private fun divergenceTableFile(projectHome: Path, targetId: String): Path {
  return projectHome.resolve(DIVERGENCE_TABLE_DIRECTORY).resolve("$targetId.txt")
}

private const val REPAIR =
  "The derivation reads the layout facts and the JPS model, never this build. A difference is a defect of the" +
  " derivation for this plugin; see `derivePluginPacking` in" +
  " community/platform/build-scripts/src/org/jetbrains/intellij/build/devDist/PluginJarDerivation.kt."

private const val DIVERGENCE_TABLE_REPAIR =
  "The known-divergence table of this target no longer states what this run measured. The patch below states it: it " +
  "adds a row for a difference the table does not hold, and it drops a row the run no longer measures.\n" +
  "A dropped row needs the patch and nothing else.\n" +
  "An added row is a new disagreement, so repair the derivation first. This validation reports beside this failure " +
  "what differs. Where the disagreement stays, apply the patch and name the divergence class in a comment above the " +
  "row.\n" + REPAIR

private const val POPULATION_REPAIR =
  "The source-derived population must include all bundled, published, compatible, and on-demand plugins. " +
  "Check the product declarations and source descriptors, then repair deriveDevDistPlatformJars or derivePluginPopulation."

/**
 * The jars this target's build is known to pack differently from what the derivation states.
 *
 * `[<plugin main module>]` opens a section, every other line is one jar path under the plugin's `lib/`, and a `#` line
 * is a comment. The comment block above a section names the divergence class of that section. The shape is the shape
 * of a `[<name>]` section file.
 *
 * The table is a measurement of one product and not of the derivation, because two products pack one plugin
 * differently. So each target owns its own file, and a patch over one file leaves every other target alone.
 *
 * A comment line that sits anywhere but above a section header is not kept, because [writeDivergenceTable] writes a
 * comment block back above its own section.
 */
@Internal
class DivergenceTable(
  @JvmField val jarsByPlugin: Map<String, Set<String>>,
  @JvmField val comments: Map<String, List<String>>,
)

/** Parses [text] as a [DivergenceTable]. */
@Internal
fun readDivergenceTable(text: String): DivergenceTable {
  val jarsByPlugin = LinkedHashMap<String, MutableSet<String>>()
  val comments = LinkedHashMap<String, List<String>>()
  val commentBlock = ArrayList<String>()
  var jars: MutableSet<String>? = null
  for (raw in text.lineSequence()) {
    val line = raw.trim()
    if (line.isEmpty()) {
      continue
    }
    if (line.startsWith('#')) {
      commentBlock.add(line)
      continue
    }
    if (line.startsWith('[') && line.endsWith(']')) {
      val plugin = line.substring(1, line.length - 1)
      jars = jarsByPlugin.computeIfAbsent(plugin) { LinkedHashSet() }
      if (commentBlock.isNotEmpty()) {
        comments[plugin] = commentBlock.toList()
      }
      commentBlock.clear()
      continue
    }
    val current = requireNotNull(jars) {
      "The divergence table states the jar '$line' above the first `[<plugin main module>]` line, so no plugin owns it"
    }
    current.add(line)
    commentBlock.clear()
  }
  return DivergenceTable(jarsByPlugin = jarsByPlugin, comments = comments)
}

/**
 * Writes the table [divergences] states, and keeps the comment block of a section that [comments] names.
 *
 * The sections come in plugin name order, and the jars of one section in path order, so the text of one measurement is
 * one text. A plugin with no jar gets no section, and its comment block goes with it.
 */
@Internal
fun writeDivergenceTable(comments: Map<String, List<String>>, divergences: Map<String, Set<String>>): String {
  return buildString {
    for ((plugin, jars) in divergences.toSortedMap()) {
      if (jars.isEmpty()) {
        continue
      }
      if (isNotEmpty()) {
        appendLine()
      }
      comments[plugin]?.forEach { appendLine(it) }
      appendLine("[$plugin]")
      for (jar in jars.sorted()) {
        appendLine(jar)
      }
    }
  }
}

/**
 * One jar the derivation states for a plugin, as the comparison reads it.
 *
 * [relativeOutputFile] and [members] come from a `DerivedPluginJar`. [libraries] is the set a packing target merges
 * into the jar, and `null` where the derivation states no set.
 */
@Internal
class DerivedJar(
  @JvmField val relativeOutputFile: String,
  @JvmField val members: List<String>,
  @JvmField val libraries: List<String>? = null,
)

/** The jars of every plugin of [derived] as [DerivedJar] lists, by plugin main module. */
@Internal
fun derivedJarsOf(derived: DerivedPluginJars): Map<String, List<DerivedJar>> {
  val result = LinkedHashMap<String, List<DerivedJar>>()
  for (plugin in derived.plugins) {
    result[plugin.mainModule] = plugin.packing.jars.map { DerivedJar(relativeOutputFile = it.relativeOutputFile, members = it.members, libraries = it.libraries) }
  }
  return result
}

/** One packed jar of one plugin, narrowed to what the derivation states. */
private class PackedJar(
  @JvmField val members: Set<String>,
  @JvmField val libraries: Set<String>,
)

/**
 * The packed jars of one plugin that name a module, every module the plugin packs anywhere, and the modules whose only
 * packed form is a bare library jar.
 */
private class PackedPlugin(
  @JvmField val jars: Map<String, PackedJar>,
  @JvmField val members: Set<String>,
  @JvmField val bareLibraryModules: Set<String>,
)

/** One line of a plugin's report, with the jar it is about, so a hold-out can take it out by path. */
private class Difference(@JvmField val path: String, @JvmField val text: String)

/**
 * The failures of one comparison, with the divergences the same run measured and the plugins the table has no row for.
 *
 * [measured] is every difference the run found, and not the reported ones alone. It is what the divergence table of
 * this target must state, so the caller renders it and compares the text against the file. [unrecorded] is every
 * packed plugin the records state no jar for, in plugin name order.
 */
@Internal
class DerivedJarComparison(
  @JvmField val failures: List<PackagingCheckFailure>,
  @JvmField val measured: Map<String, Set<String>>,
  @JvmField val unrecorded: List<String>,
)

/**
 * One failure per plugin whose derived jars differ from the packed ones, in plugin name order.
 *
 * A jar [divergences] names for a plugin is measured and not reported. A plugin the build packed and [records] state
 * no jar for is not compared: it is outside the population, and the caller reports it.
 */
@Internal
fun compareDerivedPluginJars(
  records: Map<String, List<DerivedJar>>,
  reports: List<PluginContentReport>,
  divergences: Map<String, Set<String>> = emptyMap(),
): DerivedJarComparison {
  val failures = ArrayList<PackagingCheckFailure>()
  val measured = LinkedHashMap<String, Set<String>>()
  val unrecorded = ArrayList<String>()
  for ((mainModule, variants) in reports.groupBy { it.mainModule }.toSortedMap()) {
    val heldOut = divergences[mainModule].orEmpty()
    val derived = records[mainModule].orEmpty()
    if (derived.isEmpty()) {
      // The table says nothing, so this run measures nothing. The divergence table keeps what it holds.
      if (heldOut.isNotEmpty()) {
        measured[mainModule] = heldOut
      }
      unrecorded.add(mainModule)
      continue
    }

    val packed = packedJars(mergePerOsPluginContent(variants))
    val differences = compareOnePlugin(packed = packed, derived = derived)
    if (differences.isNotEmpty()) {
      measured[mainModule] = differences.mapTo(LinkedHashSet()) { it.path }
    }
    val reported = differences.filter { it.path !in heldOut }
    if (reported.isNotEmpty()) {
      failures.add(PackagingCheckFailure(
        name = "derived-plugin-jars: $mainModule",
        error = AssertionError(
          "Plugin '$mainModule': the derivation and this build name different jars under `lib/`.\n" +
          reported.joinToString(separator = "\n") { "  ${it.text}" } + "\n" + REPAIR
        ),
      ))
    }
  }
  return DerivedJarComparison(failures = failures, measured = measured, unrecorded = unrecorded)
}

private fun packedJars(entries: List<FileEntry>): PackedPlugin {
  val jars = LinkedHashMap<String, PackedJar>()
  val packedMembers = HashSet<String>()
  val bareLibraryModules = HashSet<String>()
  for (entry in entries) {
    if (!entry.name.startsWith(LIB_PREFIX)) {
      continue
    }
    // A bare library jar taken out of a module's own jar. The module is a library wrapper with no output to pack.
    entry.module?.let(bareLibraryModules::add)
    if (entry.modules.isEmpty() && entry.contentModules.isEmpty()) {
      continue
    }
    val members = LinkedHashSet<String>()
    val libraries = LinkedHashSet<String>()
    for (module in entry.modules + entry.contentModules) {
      members.add(module.name)
      libraries.addAll(module.libraries.keys)
    }
    packedMembers.addAll(members)
    jars[entry.name.removePrefix(LIB_PREFIX)] = PackedJar(members = members, libraries = libraries)
  }
  bareLibraryModules.removeAll(packedMembers)
  return PackedPlugin(jars = jars, members = packedMembers + bareLibraryModules, bareLibraryModules = bareLibraryModules)
}

private fun compareOnePlugin(packed: PackedPlugin, derived: List<DerivedJar>): List<Difference> {
  val differences = ArrayList<Difference>()
  val derivedByPath = derived.associateBy { it.relativeOutputFile }
  for ((path, jar) in packed.jars) {
    if (path !in derivedByPath) {
      differences.add(Difference(path, "packed, not derived: $path ${describe(jar.members)}"))
    }
  }
  for (record in derived) {
    val path = record.relativeOutputFile
    // A wrapper module the build wrote as a bare library jar has no output in any jar, so its place in a derived jar
    // is nothing to compare.
    val members = record.members.filter { it !in packed.bareLibraryModules }
    val jar = packed.jars[path]
    if (jar == null) {
      if (members.all { it in packed.members }) {
        // Another product packs this jar; every member of it is in another jar of this plugin here.
        continue
      }
      differences.add(Difference(path, "derived, not packed: $path ${describe(members)}"))
      continue
    }
    addDifference(differences = differences, path = path, field = "members", derived = members, packed = jar.members)
    val libraries = record.libraries
    if (libraries != null) {
      addDifference(differences = differences, path = path, field = "libraries", derived = libraries, packed = jar.libraries)
    }
  }
  return differences
}

private fun describe(members: Collection<String>): String = "[${members.sorted().joinToString()}]"

private fun addDifference(differences: MutableList<Difference>, path: String, field: String, derived: Collection<String>, packed: Collection<String>) {
  val derivedSet = derived.toSet()
  val packedSet = packed.toSet()
  if (derivedSet == packedSet) {
    return
  }
  differences.add(Difference(
    path,
    "$path: $field only derived: ${(derivedSet - packedSet).sorted().joinToString()};" +
    " only packed: ${(packedSet - derivedSet).sorted().joinToString()}",
  ))
}
