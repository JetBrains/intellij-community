// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "KotlinPrintToLogpoint")

package org.jetbrains.intellij.build.bazel

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * One jar of a plugin, as the project model states it before any build runs.
 *
 * The generated counterpart of one `kind: jar` row of a fragment's executed `<fragment>.plan.yaml`. Three facts, and
 * every one of them is what a per-jar packing action has to declare: where the jar goes, and whose module output it
 * holds. `DevDistRecipe` records the same three off the run that packed it.
 *
 * The ordered source list is deliberately not here. A source list is the packer's half of a recipe, and
 * `./build/dev-dist.cmd replay` already gates that half: it packs a fragment's own recipe again with the Go packer and
 * compares the bytes. What no gate covered until now is the other half - whether the *model* names the same jars, with
 * the same members, as the layout evaluation a fragment runs. [comparePluginJarPlan] is that gate.
 */
internal class DerivedPluginJar(
  /** The jar's path relative to the distribution root, which is how a plan row names itself. */
  @JvmField val name: String,
  /** The plugin's main module, and every member the layout packs from its raw output; `FileEntry.modules`. */
  @JvmField val modules: List<String>,
  /** The members that come from the plugin's own `<content>`; `FileEntry.contentModules`. */
  @JvmField val contentModules: List<String>,
  /**
   * Whether a `content_module_jar` target packs this jar, so no fragment packs it.
   *
   * A handed-over jar reaches the composed distribution through the packed-plugin-jars component, so a fragment's plan
   * holds no row for it. `./build/dev-dist.cmd jars` is that jar's own byte gate.
   */
  @JvmField val isHandedOver: Boolean,
  /**
   * Whether this is the plugin's main jar, which is where the derivation co-packs a member with no jar of its own.
   *
   * A member the residue names a jar for is not co-packed, so it leaves this jar; see
   * [ContentResidueSection.memberJars]. The comparison reads the field to tell a jar the layout named from a jar nothing
   * knows about; see [PlanHoldOutReason.UNSTATED_MEMBER_JAR_NAME].
   */
  @JvmField val isMainJar: Boolean = false,
)

/**
 * Every jar the plugin [module] puts in its own directory, derived from the project model.
 *
 * Four derivations meet here, and each one already existed:
 *
 * 1. [derivePluginContent] gives the members - the plugin's own `<content>` with every `xi:include` followed, plus the
 *    `extra_members` rows of `dev-dist.yaml` - and where the plugin puts each member's jar. [deriveMemberJarPath]
 *    answers the jar, as the convention with the three corrections the residue states;
 * 2. [DevDistPluginModelTables.pluginJarPlacement] gives the plugin's directory and main jar name, which are a
 *    `PluginLayout` decision the model does not hold. A plugin with no row takes [pluginJarPlacementConvention];
 * 3. [isPrepackedPluginContentModule] says which of those jars a `content_module_jar` target already packs;
 * 4. [ContentResidueSection.memberJars] gives the jars the layout names itself, which no rule derives. A row states the
 *    member's whole jar set, so it wins over the path of 1 and over the main-jar co-pack below.
 *
 * A member with neither a row nor a jar of its own is co-packed into the plugin's main jar. [deriveMemberJarPath] states
 * that by answering the main jar's own name, which is what `getDefaultJarName` returns for such a member.
 *
 * Empty for a module the dev distribution states no content for. This walks the closure a second time, so only the
 * comparison mode calls it - a generation run must not pay for it.
 */
internal fun derivePluginJars(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): List<DerivedPluginJar> {
  if (!isDevDistContentPlugin(module = module, context = context)) {
    return emptyList()
  }
  val mainModule = module.module.name
  val placement = pluginJarPlacementOf(mainModule = mainModule, context = context)
  val derived = derivePluginContent(module = module, moduleList = moduleList, context = context)
  // Which members the plugin's own `<content>` names, so a member reaches `contentModules` and a `withModule` member
  // reaches `modules`. `DevDistRecipe.record` splits the two on the inclusion reason, which is the same distinction.
  val closureMembers = derivePluginContentClosure(module = module, moduleList = moduleList, context = context)
    ?.moduleNames
    ?.mapTo(HashSet()) { it.substringBeforeLast('/') }
    ?: emptySet()

  // The jar of each member this project holds a module for, and which of those jars a packing target serves.
  val derivedJars = LinkedHashMap<String, String>()
  val handedOverMembers = HashSet<String>()
  for ((memberName, relativeOutputFile) in derived.memberPaths) {
    val member = moduleList.getModuleDescriptorOrNull(memberName) ?: continue
    derivedJars.put(memberName, relativeOutputFile)
    if (memberName in derived.prepackedPaths &&
        isPrepackedPluginContentModule(module = member, moduleList = moduleList, context = context)) {
      handedOverMembers.add(memberName)
    }
  }

  return composeDerivedPluginJars(
    libDir = "plugins/${placement.directory}/lib/",
    mainJarName = placement.mainJarName,
    mainModule = mainModule,
    // A member with a derived jar this project holds no module for gets no jar at all. Nothing can say who packs a path
    // with no module behind it, and the plugin's main jar does not hold the member either.
    memberNames = derived.memberNames.filter { it !in derived.memberPaths || it in derivedJars },
    derivedJars = derivedJars,
    handedOverMembers = handedOverMembers,
    closureMembers = closureMembers,
    memberJars = contentResidueOf(module).memberJars,
  )
}

/**
 * The jars of one plugin, from the four facts [derivePluginJars] gathers and nothing else.
 *
 * The whole rule, and it reads no project model. Every fact is a parameter, so a caller states them directly.
 */
internal fun composeDerivedPluginJars(
  libDir: String,
  mainJarName: String,
  mainModule: String,
  /**
   * The plugin's members, in the order the jars take. The caller already dropped a member that the derivation states a
   * jar for and this project holds no module for. Such a member gets no jar at all.
   */
  memberNames: List<String>,
  /**
   * Where the derivation puts each member's jar, relative to the plugin's `lib/`.
   *
   * [mainJarName] is one of the values it may hold, and it means the plugin co-packs the member. A member absent from the
   * map has no derivable jar, and the main jar holds it too.
   */
  derivedJars: Map<String, String>,
  /** The members of [derivedJars] whose jar a `content_module_jar` target packs. */
  handedOverMembers: Set<String>,
  /** The members the plugin's own `<content>` names, which is what splits `contentModules` from `modules`. */
  closureMembers: Set<String>,
  /** See [ContentResidueSection.memberJars]. */
  memberJars: Map<String, Set<String>>,
): List<DerivedPluginJar> {
  val result = ArrayList<DerivedPluginJar>()
  val mainJarContentModules = ArrayList<String>()
  val mainJarModules = ArrayList<String>()
  mainJarModules.add(mainModule)
  // The members of each jar the residue names, by the jar's path under the plugin's `lib/`. One jar can hold several
  // members, so the rows are grouped rather than turned into one jar each.
  val statedJarMembers = LinkedHashMap<String, MutableList<String>>()
  for (memberName in memberNames) {
    val statedJars = memberJars.get(memberName)
    if (statedJars != null) {
      for (path in statedJars) {
        if (path == mainJarName) {
          (if (memberName in closureMembers) mainJarContentModules else mainJarModules).add(memberName)
        }
        else {
          statedJarMembers.computeIfAbsent(path) { ArrayList() }.add(memberName)
        }
      }
      continue
    }
    val relativeOutputFile = derivedJars.get(memberName)
    if (relativeOutputFile == null || relativeOutputFile == mainJarName) {
      (if (memberName in closureMembers) mainJarContentModules else mainJarModules).add(memberName)
      continue
    }
    result.add(
      DerivedPluginJar(
        name = libDir + relativeOutputFile,
        modules = if (memberName in closureMembers) emptyList() else listOf(memberName),
        contentModules = if (memberName in closureMembers) listOf(memberName) else emptyList(),
        isHandedOver = memberName in handedOverMembers,
      )
    )
  }
  for ((path, members) in statedJarMembers) {
    result.add(
      DerivedPluginJar(
        name = libDir + path,
        modules = members.filter { it !in closureMembers },
        contentModules = members.filter { it in closureMembers },
        // A jar the layout names holds a raw module output, so no `content_module_jar` target packs it.
        isHandedOver = false,
      )
    )
  }
  result.add(
    DerivedPluginJar(
      name = libDir + mainJarName,
      modules = mainJarModules,
      contentModules = mainJarContentModules,
      // The plugin's main jar holds the plugin's own descriptor, so it is a jar only a fragment packs.
      isHandedOver = false,
      isMainJar = true,
    )
  )
  return result
}

/**
 * One row of a fragment's executed `<fragment>.plan.yaml`, narrowed to what the jar comparison joins on.
 *
 * A schema of its own rather than a field added to [RecipeEntry]. The two answer different questions, and [RecipeEntry]
 * says so where it lists the fields it ignores: it reads a *content report*, which states what a distribution packs,
 * while this reads the record of one assembly run. `ContentReportSchemaTest` mirrors both against
 * `com.intellij.platform.distributionContent.FileEntry`, which is what keeps either one from drifting.
 *
 * [sources] is not declared. The source list is the packer's half of the recipe and
 * `./build/dev-dist.cmd replay` gates it; see [DerivedPluginJar].
 */
@Serializable
internal data class ExecutedPlanEntry(
  val name: String = "",
  /**
   * How the assembly produced the output: `jar` for one it packed, `placed` for one another writer put there, `link`
   * for a symlink, `reused` for a declared input it put on the classpath as it was, `dir` for a directory.
   *
   * Only `jar` is compared. A `placed` or `link` row is a file the packer did not produce, so the recipe states no
   * member for it and there is nothing to derive; those are the residue S4 has to name.
   */
  val kind: String = "",
  val modules: List<RecipeModule> = emptyList(),
  val contentModules: List<RecipeModule> = emptyList(),
)

/** The `jar` rows of one executed fragment plan, by output name. */
internal fun readExecutedPlanJars(file: Path): Map<String, ExecutedPlanEntry> {
  val entries = recipeYaml.decodeFromString(ListSerializer(ExecutedPlanEntry.serializer()), file.readText())
  check(entries.isNotEmpty()) {
    "$file states no output. A fragment's plan states every output it produced, so this is not one of them"
  }
  val jars = entries.filter { it.kind == JAR_OUTPUT_KIND }
  check(jars.isNotEmpty()) {
    "$file states ${entries.size} outputs and no `$JAR_OUTPUT_KIND` among them. Every fragment packs jars, so the file" +
    " is not a fragment plan, or `ExecutedPlanEntry.kind` no longer matches what `DevDistRecipe` writes"
  }
  return jars.associateBy { it.name }
}

/** `DevDistRecipe`'s word for an output the assembly's packer wrote. */
private const val JAR_OUTPUT_KIND: String = "jar"

/** Why one `jar` row of an executed plan is not compared. A closed vocabulary, and every reason is a stated fact. */
internal enum class PlanHoldOutReason(@JvmField val message: String) {
  /** A jar outside `plugins/<directory>/lib/`, which is every platform jar of a platform fragment. */
  NOT_A_PLUGIN_JAR("the output is not under a plugin's `lib/`"),

  /** A plugin directory no plugin of the content population places, so no derivation names the jar. */
  UNPLACED_PLUGIN_DIRECTORY("no plugin of the population places that directory"),

  /**
   * The layout gives a member a jar of its own and names it, and the residue states only the member.
   *
   * `PluginLayout.withModule(name, jarName)` is what states such a jar, and [ContentResidueSection.memberJars] is where
   * the jar's path belongs. So a row of this class marks a missing or a stale `member_jars` row, and
   * `--write-dev-dist-residue` repairs it off a distribution build's content report.
   *
   * Until the row exists, the derivation co-packs the member into the plugin's main jar. That is the same fact seen from
   * the other side as the main jar's own difference: the model over-states the main jar by exactly the members these
   * jars hold.
   */
  UNSTATED_MEMBER_JAR_NAME("the layout names a jar for a member the residue states without one"),

  /** The plugin is derived and its jar set names no jar at that path, and none of its members either. */
  NO_DERIVED_JAR("the plugin's derived jar set holds no jar of that name"),

  /**
   * The population derives one jar path twice, so the derivation states no single answer for it.
   *
   * Two plugins share a directory today. `intellij.java.plugin` and `language-server.plugins.java` both place
   * `plugins/java/`, because they belong to different products and no distribution holds both. The population is the
   * union over the products, so this side sees both, and taking one of them would compare a jar of the other product.
   *
   * One plugin can also derive one name twice, and the message does not tell that case from the one above. The station
   * plugin is the live case: it states `modules/intellij.station.aia.jar` for one member and derives the same path for
   * another. Widening the relation key from the jar name to the plugin and the jar is what separates the two.
   */
  AMBIGUOUS_JAR_NAME("two plugins of the population derive a jar of that name"),
}

/**
 * The print order of the held-out table, so a run's output does not depend on map iteration.
 *
 * The same rule `heldOutOrder` of `build/dev-dist/internal/devdist/replay.go` states for the replay's own table. It
 * runs from the widest reason to the narrowest, which is also the order the reasons are asked in.
 */
private val HELD_OUT_PRINT_ORDER: List<PlanHoldOutReason> = listOf(
  PlanHoldOutReason.NOT_A_PLUGIN_JAR,
  PlanHoldOutReason.UNPLACED_PLUGIN_DIRECTORY,
  PlanHoldOutReason.AMBIGUOUS_JAR_NAME,
  PlanHoldOutReason.UNSTATED_MEMBER_JAR_NAME,
  PlanHoldOutReason.NO_DERIVED_JAR,
)

/** One jar the two sides describe differently, with both descriptions. */
internal class PlanJarDifference(
  @JvmField val name: String,
  @JvmField val field: String,
  @JvmField val onlyDerived: List<String>,
  @JvmField val onlyExecuted: List<String>,
)

/**
 * What one comparison of a derived jar set against one executed fragment plan produced.
 *
 * [heldOut] is a partition: a row is filed under the first reason that holds, so the counts sum to the held-out rows.
 */
internal class PluginJarPlanComparison(
  @JvmField val identical: List<String>,
  @JvmField val differing: List<PlanJarDifference>,
  /** Jars the derivation names that the fragment packed nowhere, inside the plugin directories the fragment touched. */
  @JvmField val derivedOnly: List<String>,
  @JvmField val heldOut: Map<PlanHoldOutReason, List<String>>,
)

/**
 * Compares the jars the model derives against the jars one fragment really packed.
 *
 * **The comparator normalizes, and the alternative was to give the two sides one writer.** A fragment writes its plan
 * with `serializeContentEntries` and this side would write yaml with `recipeYaml`, so a byte comparison of two writers
 * is a claim about yaml formatting. Worse, it cannot hold: a `file` source's `size` and `hash` are properties of the
 * descriptor a build produced, and no generated recipe can state either. So both sides are parsed and the parsed values
 * are compared, and this states which fields it reads: the output name, `modules` and `contentModules`.
 *
 * **The comparison covers every `jar` output of the plan, not the 342 the replay reproduces.** The replay's hold-out is
 * a property of the packer's flag grammar - `replayableKinds` in `build/dev-dist/internal/devdist/replay.go` - and not
 * of the recipe, so a held-out output still has a plan row and still has a derived jar.
 *
 * **The scope is the plugin directories the fragment touched.** A fragment packs a slice of the product, and the
 * derivation is repo-global, so every plugin outside the slice would otherwise read as a missing jar. A directory the
 * plan holds no jar for is out of scope rather than held out, because the plan says nothing about it either way.
 *
 * A handed-over jar is expected to be absent from the plan: a `content_module_jar` target packs it and
 * `./build/dev-dist.cmd jars` is its byte gate. So it is dropped from [PluginJarPlanComparison.derivedOnly] rather
 * than reported there.
 */
internal fun comparePluginJarPlan(
  executed: Map<String, ExecutedPlanEntry>,
  derived: List<DerivedPluginJar>,
): PluginJarPlanComparison {
  val derivedByName = HashMap<String, DerivedPluginJar>(derived.size)
  val ambiguousNames = HashSet<String>()
  val placedDirectories = HashSet<String>()
  // What the derivation co-packed into each plugin's main jar, which is where a member with no stated jar name lands.
  val mainJarMembers = HashMap<String, MutableSet<String>>()
  for (jar in derived) {
    val previous = derivedByName.put(jar.name, jar)
    if (previous != null) {
      ambiguousNames.add(jar.name)
    }
    val directory = pluginDirectoryOf(jar.name) ?: continue
    placedDirectories.add(directory)
    if (jar.isMainJar) {
      val members = mainJarMembers.computeIfAbsent(directory) { HashSet() }
      members.addAll(jar.modules)
      members.addAll(jar.contentModules)
    }
  }
  // The directories this plan really packed a jar in, which is the slice the fragment laid out.
  val fragmentDirectories = executed.keys.mapNotNullTo(HashSet(), ::pluginDirectoryOf)

  val identical = ArrayList<String>()
  val differing = ArrayList<PlanJarDifference>()
  val heldOut = LinkedHashMap<PlanHoldOutReason, MutableList<String>>()
  for ((name, entry) in executed) {
    val directory = pluginDirectoryOf(name)
    val reason = when {
      directory == null -> PlanHoldOutReason.NOT_A_PLUGIN_JAR
      directory !in placedDirectories -> PlanHoldOutReason.UNPLACED_PLUGIN_DIRECTORY
      name in ambiguousNames -> PlanHoldOutReason.AMBIGUOUS_JAR_NAME
      name in derivedByName -> null
      holdsOnlyMainJarMembers(entry = entry, members = mainJarMembers.get(directory)) -> PlanHoldOutReason.UNSTATED_MEMBER_JAR_NAME
      else -> PlanHoldOutReason.NO_DERIVED_JAR
    }
    if (reason != null) {
      heldOut.computeIfAbsent(reason) { ArrayList() }.add(name)
      continue
    }
    val jar = derivedByName.getValue(name)
    val fields = listOfNotNull(
      compareNameSets(name = name, field = "modules", derived = jar.modules, executed = entry.modules.map { it.name }),
      compareNameSets(name = name, field = "contentModules", derived = jar.contentModules, executed = entry.contentModules.map { it.moduleName }),
    )
    if (fields.isEmpty()) {
      identical.add(name)
    }
    else {
      differing.addAll(fields)
    }
  }

  val derivedOnly = derived.asSequence()
    .filterNot { it.isHandedOver }
    .filter { pluginDirectoryOf(it.name) in fragmentDirectories }
    .map { it.name }
    .filterNot { it in ambiguousNames || executed.containsKey(it) }
    .distinct()
    .sorted()
    .toList()
  return PluginJarPlanComparison(
    identical = identical.sorted(),
    differing = differing.sortedWith(compareBy({ it.name }, { it.field })),
    derivedOnly = derivedOnly,
    heldOut = heldOut.mapValues { it.value.sorted() },
  )
}

/**
 * Whether every module [entry] names is one the derivation co-packed into the plugin's main jar.
 *
 * That is what tells [PlanHoldOutReason.UNSTATED_MEMBER_JAR_NAME] from [PlanHoldOutReason.NO_DERIVED_JAR]: the
 * derivation has these members and put them in the wrong jar, rather than not having them at all. `false` for an entry
 * naming no module, because such a jar states nothing to match.
 */
private fun holdsOnlyMainJarMembers(entry: ExecutedPlanEntry, members: Set<String>?): Boolean {
  if (members == null) {
    return false
  }
  val named = entry.modules.map { it.name } + entry.contentModules.map { it.moduleName }
  return named.isNotEmpty() && named.all { it in members }
}

/**
 * The plugin directory a distribution-relative output name sits in, or `null` for an output that is not a plugin's jar.
 *
 * `plugins/<directory>/lib/<anything>`, and the directory may hold a space - `JPA Model` does. A jar under
 * `plugins/<directory>/` but outside `lib/` is not a member's jar either: `plugins/Groovy/lib/agent/gragent.jar` is
 * placed rather than packed, and `DevDistRecipe` reports it as `placed`.
 */
private fun pluginDirectoryOf(name: String): String? {
  val parts = name.split('/')
  if (parts.size < 4 || parts[0] != "plugins" || parts[2] != "lib") {
    return null
  }
  return parts[1]
}

/**
 * The two sides' name lists as a difference, or `null` when they hold the same names.
 *
 * Sets, not lists: `modules` order in a plan row is the order the packer merged the sources in, and this comparison
 * reads no source list. The order question belongs to the source half, which the replay gates.
 */
private fun compareNameSets(name: String, field: String, derived: List<String>, executed: List<String>): PlanJarDifference? {
  val derivedSet = derived.toSet()
  val executedSet = executed.toSet()
  if (derivedSet == executedSet) {
    return null
  }
  return PlanJarDifference(
    name = name,
    field = field,
    onlyDerived = (derivedSet - executedSet).sorted(),
    onlyExecuted = (executedSet - derivedSet).sorted(),
  )
}

/**
 * Compares every plugin of the population against one or more executed fragment plans, and prints what it found.
 *
 * One derivation for the whole run, then one comparison per plan: the plans are slices of one product, and a plugin's
 * jar set does not depend on which fragment packs it.
 *
 * Returns whether every compared jar agreed. The caller decides what a disagreement costs, because this is a
 * measurement first: a run states its own coverage, and the held-out table prints last so that no reader has to work
 * the denominator out.
 */
internal fun reportPluginJarPlanComparison(
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  plans: List<Path>,
): Boolean {
  val derived = moduleList.allModules.flatMap { derivePluginJars(module = it, moduleList = moduleList, context = context) }
  println("derived ${derived.size} plugin jars, ${derived.count { it.isHandedOver }} of them handed over to a packing target")
  var agreed = true
  for (plan in plans) {
    val executed = readExecutedPlanJars(plan)
    val comparison = comparePluginJarPlan(executed = executed, derived = derived)
    println()
    println("$plan: ${executed.size} jar outputs")
    println("  identical           : ${comparison.identical.size}")
    println("  differing           : ${comparison.differing.size}")
    println("  derived, not packed : ${comparison.derivedOnly.size}")
    for (difference in comparison.differing) {
      println("    ${difference.name} ${difference.field}")
      if (difference.onlyDerived.isNotEmpty()) {
        println("      only the model : ${difference.onlyDerived.joinToString()}")
      }
      if (difference.onlyExecuted.isNotEmpty()) {
        println("      only the run   : ${difference.onlyExecuted.joinToString()}")
      }
    }
    for (name in comparison.derivedOnly) {
      println("    $name is derived and this fragment packed no such jar")
    }
    val held = comparison.heldOut.values.sumOf { it.size }
    if (held != 0) {
      println("  held out of the comparison: $held of the ${executed.size} jar outputs")
      for (reason in HELD_OUT_PRINT_ORDER) {
        val names = comparison.heldOut.get(reason) ?: continue
        println("    ${names.size}  ${reason.message}")
        println("       ${names.first()}")
      }
    }
    if (comparison.differing.isNotEmpty() || comparison.derivedOnly.isNotEmpty()) {
      agreed = false
    }
  }
  return agreed
}
