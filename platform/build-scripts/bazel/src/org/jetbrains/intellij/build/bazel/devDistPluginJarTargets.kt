// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// `EnumValuesSoftDeprecate`: `Enum.entries` needs an opt-in this module does not grant, and the compiler refuses it.
@file:Suppress("ReplaceGetOrSet", "KotlinPrintToLogpoint", "EnumValuesSoftDeprecate")

package org.jetbrains.intellij.build.bazel

/**
 * One `dev_dist_plugin_jar` target of a plugin, as the generator writes it.
 *
 * The emission half of [DerivedPluginJar]: the same jar with every member and every library resolved to a Bazel label.
 * Both orders are merge orders and neither is sorted - the packer resolves an entry two sources both offer to the first
 * one, so a sorted list is a different jar.
 */
internal class PluginJarTarget(
  /** The jar's destination under the plugin's own `lib/`, which is also what the target name comes from. */
  @JvmField val relativeOutputFile: String,
  /** The members' own `jvm_library` targets, in the layout's member order. */
  @JvmField val memberLabels: List<String>,
  /**
   * The same members by JPS module name, in the same order.
   *
   * What the hand-off needs. A member of a jar this target packs leaves the plugin's `content_modules`, and a name is
   * the key [resolvePluginContent] works in.
   */
  @JvmField val memberNames: List<String>,
  /** The merged libraries' container targets, in merge order; see [PluginContent.libraryContainerLabels]. */
  @JvmField val libraryLabels: List<String>,
)

/**
 * Which producer packs one jar of a plugin's `lib/`.
 *
 * **The jar's name decides it, and nothing else.** A jar named after its one member holds what that member's own
 * `content_module_jar` already packs: the same one module output, the same libraries off the same `.iml`, the same jar
 * name. So the member's target is the producer - one target for a member many plugins ship - and the plugin says only
 * where its `lib/` puts the jar. A jar the plugin's layout names itself has no such member, and `dev_dist_plugin_jar` is
 * the producer for it.
 *
 * The split is the rule `_collect_prepacked` in `dev_dist_content.bzl` already enforces one layer up: a relation that
 * restates a derived path fails the load. A `dev_dist_plugin_jar` for a member-named jar restates four derived facts -
 * the jar name, the member label, the member's own libraries and the enclosing plugin. `intellij.libraries.kotlinx.bcv`
 * of `plugins/api-watcher` is the shape: such a target is a copy of the `content_module_jar` that stands beside the
 * member in `community/libraries/kotlinx/bcv`.
 */
internal enum class PluginJarProducer(@JvmField val message: String) {
  /**
   * The member's own `content_module_jar`, which this tree already holds.
   *
   * [isPrepackedPluginContentModule] is true for the member, so the target exists and the hand-off vocabulary accepts
   * it. What the plugin states is the *relation*: `prepacked_content_modules` for `modules/<member>.jar`, or a
   * `prepacked_jars` row for the embedded `<member>.jar`. [MovablePluginJars.memberRelations] holds the ones this run
   * wires, and [MovablePluginJars.withheldRelations] the ones a refusal keeps back.
   */
  MEMBER_JAR_TARGET("the member's own `content_module_jar` packs it"),

  /**
   * The member's own jar, for a member the candidacy states no packing target for.
   *
   * [prepackedPluginContentModuleLibraries] answers `null`, so this tree holds no `content_module_jar` beside the member
   * and there is no label a relation could name. The candidacy is keyed by module name over a distribution build's
   * report, and widening that key is the hand-over slice's own work - the same widening
   * [PluginJarExclusion.AMBIGUOUS_DESTINATION] waits for.
   */
  MEMBER_JAR_CANDIDACY("the member's own jar, and the candidacy states no target for it"),

  /**
   * A jar the plugin's layout names itself, which is what `dev_dist_plugin_jar` exists for.
   *
   * This generator answers for the jar, and a [PluginJarExclusion] may still take it out. So this value says only that
   * no member's own target packs the jar, and not that a target is written for it.
   */
  PLUGIN_JAR_TARGET("the plugin names the jar itself"),
}

/**
 * Which producer packs [jar] - see [PluginJarProducer].
 *
 * Pure, and the whole rule: a jar of one member whose destination is that member's own jar name, at either of the two
 * destinations `_collect_prepacked` accepts, belongs to the member's target. Everything else belongs to the plugin.
 *
 * **The destination is the only fact this reads.** A jar of a destination the plugin derives twice is member-named here
 * all the same, and [PluginJarExclusion.AMBIGUOUS_DESTINATION] states what that leaves for the hand-over slice.
 */
internal fun pluginJarProducer(jar: DerivedPluginJar, hasMemberJarTarget: (String) -> Boolean): PluginJarProducer {
  val member = jar.members.singleOrNull() ?: return PluginJarProducer.PLUGIN_JAR_TARGET
  val isMemberNamed = jar.relativeOutputFile == "$member.jar" ||
                      isConventionalPrepackedPath(moduleName = member, relativeOutputFile = jar.relativeOutputFile)
  return when {
    !isMemberNamed -> PluginJarProducer.PLUGIN_JAR_TARGET
    hasMemberJarTarget(member) -> PluginJarProducer.MEMBER_JAR_TARGET
    else -> PluginJarProducer.MEMBER_JAR_CANDIDACY
  }
}

/**
 * Why one derived jar of a plugin gets no packing target.
 *
 * A closed vocabulary, and every reason is a fact the generator can state. The classes fall into two groups, and the
 * split is not arbitrary: every one but the last is a property of the project model and of the plugin's own residue,
 * which this run reads, and [STATED_UNPACKABLE] is a property of what the distribution builder does to a jar after it is
 * merged, which no model states.
 *
 * A jar is filed under the first reason that holds, so the counts partition the refused set.
 */
internal enum class PluginJarExclusion(@JvmField val message: String) {
  /**
   * The plugin's layout scrambles, so what the distribution ships is not what a packer merged.
   *
   * `collectLayoutsOfPluginsToScramble` selects a layout whose `pathsToScramble` is not empty, and
   * `devDistPluginDescriptorPlan.kt` already records that predicate as the `no_embedding` row of the plugin's descriptor
   * residue. Precautionary today, because `IdeBuilder` asserts that no incomplete fragment scrambles anything - so the
   * two plugins this refuses ship unscrambled jars in a dev distribution. Refused all the same: a target that is correct
   * only while an assertion holds is a jar that changes when the assertion is relaxed.
   */
  SCRAMBLING_PLUGIN("the plugin's layout scrambles"),

  /**
   * The plugin derives two jars at one destination, so the derivation states no single answer for it.
   *
   * The emission counterpart of [PlanHoldOutReason.AMBIGUOUS_JAR_NAME], and the station plugin is the live case: it
   * states `modules/intellij.station.aia.jar` for one member and derives the same path for another. One destination
   * holds one jar, and nothing here says which of the two it is.
   *
   * **Only the half this generator answers for reaches this refusal.** [pluginJarProducer] runs first, so a
   * member-named half of the same destination is counted under its own producer in [MovablePluginJars.memberNamed], and
   * the count here is 1 for such a pair. The relation is keyed by the plugin and the destination, so two plugins that
   * derive one jar of a name are two relations. What that key does not separate is one plugin's two members at one
   * destination: a relation there would name a destination the layout also writes from the other member.
   */
  AMBIGUOUS_DESTINATION("the plugin derives two jars at one destination"),

  /**
   * The residue vetoes a member, so the layout puts something in the jar that no rule derives.
   *
   * [ContentResidueSection.vetoedMembers] states three `PluginLayout` decisions, and each one changes the jar rather
   * than only who packs it: a jar holding several content modules, a bare library jar taken out of the member's own jar,
   * and a project library merged into it. The third is the one this refusal is measured on -
   * [productionModuleLibraryNames] takes no project library, so the derivation would pack the jar without it.
   */
  VETOED_MEMBER("the residue vetoes a member"),

  /**
   * The plugin packs a member's raw output into a second jar of its own, so the member cannot leave the fragment.
   *
   * [ContentResidueSection.rawMembers] states it, and [derivePluginContent] already gates the member hand-off on the
   * same row. A hand-off takes the member's raw jar out of the fragment's declaration, and that second jar still needs
   * it. `intellij.gateway.core` is the case. Its own `lib/modules/` jar is plain, and the gateway layout packs the raw
   * output into `lib/gateway-standalone/gateway.core.jar` as well.
   */
  RAW_MEMBER("the plugin packs a member's raw output into a second jar"),

  /** A member this project holds no Bazel target for, so nothing can name its output. */
  UNKNOWN_MEMBER("a member has no Bazel target"),

  /**
   * A member only the other repository can name.
   *
   * `getBazelDependencyLabel` fails outright on a community package naming an ultimate label, and the completion set in
   * `//build/dev-dist-content` is the one package that sees both halves. So such a jar waits for a writer there, exactly
   * as a cross-repository content member does; see [PluginContentResult.crossRepositoryPrepackedModules].
   */
  CROSS_REPOSITORY_MEMBER("a member is an ultimate module of a community plugin"),

  /**
   * The derivation states no library set for one of the members.
   *
   * [DerivedPluginCandidacy.memberLibraries] answers for a member the candidacy walk reached. A member named only as
   * `<module>/<descriptor>.xml` in the plugin's `<content>` is not one: `computeModuleSourcesByContent` skips such an
   * element, so the walk never asks the module anything. The jar is refused rather than packed with no library, because
   * an empty set and an unknown one are not the same claim.
   */
  UNSTATED_MEMBER_LIBRARIES("the derivation states no library set for a member"),

  /**
   * A merged library this generator cannot name, or a library set that disagrees with what the layout records.
   *
   * The same refusal [computeContentModuleJar] makes for a platform jar, through the same
   * [mergedLibraryTargetLabels] body.
   */
  UNNAMEABLE_LIBRARY("a merged library has no Bazel target this package can name"),

  /**
   * A jar [UNPACKABLE_PLUGIN_JARS] states the packer cannot reproduce.
   *
   * Its own class, because it is the one exclusion no derivation reaches. See that map for what states each row.
   */
  STATED_UNPACKABLE("the packer cannot reproduce the jar"),
}

/**
 * Jars a distribution builder does more to than merge the members' outputs and their libraries, by distribution path.
 *
 * The plugin twin of [EXCLUDED_CONTENT_MODULES], and the same class of fact: what the layout really put in the jar is not
 * what the model derives, so a packing action would write a jar that differs and nothing would notice until class-load
 * time. The value is the cause, and every row was **measured** and not predicted. A gate states each row: the first two
 * came from `./build/dev-dist.cmd plugin-jars` on 2026-09-01, and the third from the hand-off refusing itself on the
 * same day.
 *
 * **A converter-side list, and not a section of [PLUGIN_MODEL_TABLES_FILE_NAME].** That file is the hand-off from
 * `plugin-model-tool`, and one writer per file is what keeps it honest. Nothing in `plugin-model-tool` reads or derives
 * these rows: they are a property of the bytes one fragment wrote, and only a byte comparison finds them. A section with
 * no producer is the shape ADR 0008 records having gone wrong once.
 *
 * A jar named after its one member never reaches this map, because [PluginJarProducer] takes it out first. A row that
 * stops being needed is a jar that can be moved, and the byte gate is what finds that out.
 * `build/dev-dist-measurements.md` holds the run behind every row.
 */
private val UNPACKABLE_PLUGIN_JARS: Map<String, String> = mapOf(
  // The layout keeps the members' module libraries out of the jar and lays them beside it. `isSeparateLibraryJar` inside
  // `computeSourcesForModuleLibs` is what does it, and the fragment's jar holds 2 entries where the merge holds
  // thousands.
  "plugins/maven-plugin/lib/intellij.maven.server.indexer/maven-server-indexer.jar" to
  "the layout keeps the libraries out: 4 893 entries",
  // The one order failure of the whole set. Both jars hold the same 5 entries.
  "plugins/maven-plugin/lib/artifact-resolver-m31.jar" to
  "the members merge in another order: the two jars hold one entry set and the layout puts `META-INF/plexus/` first",
  // Measured on 2026-09-01 by the hand-over itself: `validatePrepackedPluginContentHandoff` refused the relation with
  // "has patched module output". A `ModuleOutputPatcher` entry is a `PluginLayout` decision, so the jar holds a path no
  // module output has and a packing action would write the jar without it.
  "plugins/gateway-plugin/lib/gateway-standalone/gateway.jar" to
  "the layout patches a member's module output",
)

/**
 * Prints how many plugin jars this run emitted a packing target for, and what it refused.
 *
 * The run states its own coverage, which is the rule the jar comparison's held-out table and the replay's both follow: a
 * count with no denominator leaves the reader to work it out. The denominator is what [computeMovablePluginJars] keeps:
 * the derived non-main jars of every plugin, less the jars a relation already hands over to a `content_module_jar`. Those
 * are thousands and no producer question here reaches them, so the printed total is not every plugin jar of the model.
 */
internal fun reportMovablePluginJars(targets: List<BazelBuildFileGenerator.ModuleTargets>) {
  val emitted = targets.sumOf { it.pluginJars.targets.size }
  val relations = targets.sumOf { it.pluginJars.memberRelations.size }
  val excluded = LinkedHashMap<PluginJarExclusion, Int>()
  val withheld = LinkedHashMap<PluginJarExclusion, Int>()
  val memberNamed = LinkedHashMap<PluginJarProducer, Int>()
  for (module in targets) {
    for ((reason, jars) in module.pluginJars.excluded) {
      excluded.merge(reason, jars.size, Int::plus)
    }
    for ((reason, jars) in module.pluginJars.withheldRelations) {
      withheld.merge(reason, jars.size, Int::plus)
    }
    for ((producer, jars) in module.pluginJars.memberNamed) {
      memberNamed.merge(producer, jars.size, Int::plus)
    }
  }
  val refused = excluded.values.sum()
  val derived = emitted + refused + memberNamed.values.sum()
  println(
    "dev-distribution plugin jars: $emitted packing targets of the $derived non-main jars the model derives" +
    " and no relation hands over yet"
  )
  // The two enums' own order, each from the widest class to the narrowest, so two runs print two comparable tables.
  for (producer in PluginJarProducer.values()) {
    val count = memberNamed.get(producer) ?: continue
    println("  $count not this generator's to emit: ${producer.message}")
  }
  println("  $relations of them handed to that target by a relation")
  for (reason in PluginJarExclusion.values()) {
    val count = withheld.get(reason) ?: continue
    println("  relation withheld $count: ${reason.message}")
  }
  for (reason in PluginJarExclusion.values()) {
    val count = excluded.get(reason) ?: continue
    println("  refused $count: ${reason.message}")
  }
}

/** What one plugin emits into one Bazel package, which is every fact [checkOnePluginPerJarTargetPackage] reads. */
internal class PluginJarPackage(
  @JvmField val packagePath: String,
  /** The plugin's own main module, which is what the failure names. */
  @JvmField val plugin: String,
  /** The destinations of this plugin's packing targets. The target name comes from the destination alone. */
  @JvmField val relativeOutputFiles: List<String>,
)

/** One [PluginJarPackage] per plugin of [targets], including a plugin that emits no packing target. */
internal fun pluginJarPackagesOf(targets: List<BazelBuildFileGenerator.ModuleTargets>): List<PluginJarPackage> =
  targets.map { module ->
    PluginJarPackage(
      packagePath = module.moduleDescriptor.bazelBuildFileDir.toString(),
      plugin = module.moduleDescriptor.module.name,
      relativeOutputFiles = module.pluginJars.targets.map { it.relativeOutputFile },
    )
  }

/**
 * Fails the run when two plugins of one Bazel package emit a packing target of the same name.
 *
 * `dev_dist_plugin_jar_target_name` derives the name from the destination alone, on the convention of one plugin per
 * package - the package that holds the plugin's own `dev_dist_plugin` call. Two plugins in one package both putting a
 * jar at `modules/x.jar` would break that convention, and Bazel would then refuse the package while loading, naming a
 * target and no plugin. This says which two plugins instead.
 *
 * **The caller asks before it writes the first file.** A check after the save fails with both halves of the collision
 * already on disk, which leaves a package Bazel refuses to load and prints no counts.
 *
 * Not a refusal, because it is not a fact about a jar. It is the emission convention itself, and a writer that has to
 * break it owes its own disambiguation.
 */
internal fun checkOnePluginPerJarTargetPackage(packages: List<PluginJarPackage>) {
  val owners = HashMap<String, String>()
  for (pluginPackage in packages) {
    for (relativeOutputFile in pluginPackage.relativeOutputFiles) {
      val targetName = pluginJarTargetName(relativeOutputFile)
      val previous = owners.put(pluginPackage.packagePath + " " + targetName, pluginPackage.plugin)
      check(previous == null || previous == pluginPackage.plugin) {
        "${pluginPackage.packagePath}: `${pluginPackage.plugin}` and `$previous` both emit a packing target named" +
        " `$targetName`. A plugin jar's target name is its destination, so one package states one plugin's jars"
      }
    }
  }
}

/**
 * The name of the target that packs one jar of a plugin's `lib/`, in the plugin's own package.
 *
 * The Kotlin half of `dev_dist_plugin_jar_target_name` of `content_module_jar.bzl`, which is the half the macro runs.
 * The two must agree, exactly as [contentModuleJarTargetName] and its own Starlark half do. Nothing writes this name
 * into a `BUILD.bazel` - the macro derives it - so the one reader is the collision check above.
 */
internal fun pluginJarTargetName(relativeOutputFile: String): String =
  relativeOutputFile.removeSuffix(".jar").replace('/', '_') + "_dev_dist_plugin_jar"

/**
 * Why [jar] gets no packing target, or `null` when it gets one.
 *
 * The whole refusal rule, and it reads no project model. Every fact is a parameter, the way [composeDerivedPluginJars]
 * takes its four. The order is the partition: a jar is filed under the first reason that holds, and the reasons run from
 * the widest to the narrowest, so two runs print two comparable tables.
 *
 * [unknownMembers] and [crossRepositoryMembers] are the two facts the caller resolves against the module list, narrowed
 * to this jar's own members. They are sets rather than booleans so that a caller cannot pass the answer to another
 * question.
 */
internal fun refusePluginJar(
  jar: DerivedPluginJar,
  scrambles: Boolean,
  ambiguousDestinations: Set<String>,
  vetoedMembers: Set<String>,
  rawMembers: Set<String>,
  unknownMembers: Set<String>,
  crossRepositoryMembers: Set<String>,
  memberLibraries: Map<String, Set<String>?>,
): PluginJarExclusion? {
  return when {
    scrambles -> PluginJarExclusion.SCRAMBLING_PLUGIN
    jar.relativeOutputFile in ambiguousDestinations -> PluginJarExclusion.AMBIGUOUS_DESTINATION
    jar.members.any { it in vetoedMembers } -> PluginJarExclusion.VETOED_MEMBER
    jar.members.any { it in rawMembers } -> PluginJarExclusion.RAW_MEMBER
    jar.name in UNPACKABLE_PLUGIN_JARS -> PluginJarExclusion.STATED_UNPACKABLE
    jar.members.any { it in unknownMembers } -> PluginJarExclusion.UNKNOWN_MEMBER
    jar.members.any { it in crossRepositoryMembers } -> PluginJarExclusion.CROSS_REPOSITORY_MEMBER
    jar.members.any { !memberLibraries.containsKey(it) } -> PluginJarExclusion.UNSTATED_MEMBER_LIBRARIES
    jar.members.any { memberLibraries.get(it) == null } -> PluginJarExclusion.UNNAMEABLE_LIBRARY
    else -> null
  }
}

/**
 * What [computeMovablePluginJars] produced, as a partition of the plugin's movable jars.
 *
 * Three fields for three answers, and every derived non-main jar of the plugin is in exactly one of them: [targets] is
 * the jar this generator emits a `dev_dist_plugin_jar` for, [excluded] is the jar it refuses with the reason, and
 * [memberNamed] is the jar another producer owns.
 */
internal class MovablePluginJars(
  @JvmField val targets: List<PluginJarTarget>,
  /** The refused jars by reason, by distribution-relative path. A partition of the refused set. */
  @JvmField val excluded: Map<PluginJarExclusion, List<String>>,
  /**
   * The jars the member's own `content_module_jar` produces, by which half of that hand-off this tree already has.
   *
   * Counted and not emitted. See [PluginJarProducer], which holds the reason.
   */
  @JvmField val memberNamed: Map<PluginJarProducer, List<String>> = emptyMap(),
  /**
   * Where this plugin puts the jar of each [PluginJarProducer.MEMBER_JAR_TARGET] member it hands over, by module name.
   *
   * The relation the member-named half needs, in the shape [resolvePluginContent] already takes: the two existing
   * attributes carry it, and this generator writes no target for such a jar.
   */
  @JvmField val memberRelations: Map<String, String> = emptyMap(),
  /**
   * The [PluginJarProducer.MEMBER_JAR_TARGET] jars this run does **not** hand over, by reason.
   *
   * Its own map and not part of [excluded], because the two answer different questions: [excluded] is a target this
   * generator would have written and refused, and this is a relation to a target that already exists.
   */
  @JvmField val withheldRelations: Map<PluginJarExclusion, List<String>> = emptyMap(),
) {
  companion object {
    @JvmField val NONE: MovablePluginJars = MovablePluginJars(targets = emptyList(), excluded = emptyMap())
  }
}

/** One plugin's whole dev-distribution packing statement: what it declares, and what packs its own jars. */
internal class DerivedPluginPacking(
  @JvmField val content: DerivedPluginContent,
  /** Every jar the plugin puts in its own directory, with [DerivedPluginJar.isHandedOver] over both producers. */
  @JvmField val jars: List<DerivedPluginJar>,
  @JvmField val movable: MovablePluginJars,
)

/**
 * Both halves of one plugin's packing statement, from one walk of its content.
 *
 * Two resolutions of the content leaf, in this order, because the two answers depend on each other in one direction
 * only: the movable set is a function of the derived jars, and which members the leaf still declares is a function of
 * the movable set. So the first pass answers who packs each jar, and the second takes the members of the jars that got a
 * packing target out of the leaf. Nothing between the two is walked again; see [withPluginJarHandOff].
 *
 * The second pass composes the jars again as well, because a jar reads its own hand-off from the destinations of the
 * targets the first pass produced. A plugin with no target of its own pays for neither pass.
 *
 * `null` for a module the dev distribution states no content for.
 */
internal fun computeDerivedPluginPacking(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): DerivedPluginPacking? {
  val residue = contentResidueOf(module = module, context = context)
  val first = computeDerivedPluginContent(module = module, moduleList = moduleList, context = context, residue = residue)
                ?: return null
  val firstJars = derivedPluginJarsOf(module = module, derived = first, moduleList = moduleList, context = context)
  val movable = computeMovablePluginJars(
    module = module,
    derived = first,
    jars = firstJars,
    residue = residue,
    moduleList = moduleList,
    context = context,
  )
  // A member leaves the content leaf only where the moved jar is its whole jar set. `intellij.javaee.appServers.tomee
  // .agent.rt` is in `specifics/tomee-specifics.jar` and in the plugin's main jar, so the fragment still packs its raw
  // output and still has to declare it. The jar moves either way; only the declaration stays.
  val memberJars = residue.memberJars
  val layoutJarMembers = movable.targets.asSequence()
    .flatMap { it.memberNames }
    .filterTo(LinkedHashSet()) { (memberJars.get(it)?.size ?: 1) == 1 }
  // The destinations those targets pack, which is the key the jars read; see [DerivedPluginJar.isHandedOver]. The
  // member set above answers the declaration and this answers the jar, so the tomee case moves the jar and keeps the
  // declaration.
  val handedOverJars = movable.targets.mapTo(LinkedHashSet()) { it.relativeOutputFile }
  if (layoutJarMembers.isEmpty() && movable.memberRelations.isEmpty()) {
    for (warning in first.warnings) {
      println(warning)
    }
    return DerivedPluginPacking(
      content = first,
      // No member leaves the leaf, and a target can still own a jar: every member of it holds a second jar. So the
      // jars are composed a second time where a target exists, and the first pass's answer stands where none does.
      jars = if (handedOverJars.isEmpty()) {
        firstJars
      }
      else {
        derivedPluginJarsOf(module = module, derived = first, moduleList = moduleList, context = context, handedOverJars = handedOverJars)
      },
      movable = movable,
    )
  }
  val content = first.withPluginJarHandOff(
    module = module,
    residue = residue,
    layoutJarMembers = layoutJarMembers,
    memberRelations = movable.memberRelations,
    moduleList = moduleList,
    context = context,
  )
  // The second pass's warnings, and not the first pass's. The two resolutions warn about the same labels, and a handed
  // over member drops the warnings it was the only reason for.
  for (warning in content.warnings) {
    println(warning)
  }
  return DerivedPluginPacking(
    content = content,
    jars = derivedPluginJarsOf(
      module = module,
      derived = content,
      moduleList = moduleList,
      context = context,
      handedOverJars = handedOverJars,
    ),
    movable = movable,
  )
}

/**
 * The jars of [module] one `dev_dist_plugin_jar` target may pack, from the derivation the jar comparison uses.
 *
 * **One derivation, two readers.** [derivedPluginJarsOf] states every jar of the plugin, and `comparePluginJarPlan`
 * measures that same statement against a fragment's executed plan. This narrows it to the jars a packing action can
 * reproduce, and every narrowing is a [PluginJarExclusion] with a count. So a target exists only for a jar the
 * comparison covers, and the byte gate `./build/dev-dist.cmd plugin-jars` is what proves it.
 *
 * **A member-named jar is another producer's.** [pluginJarProducer] takes it out before any refusal runs, so a target
 * here states only what no convention derives. That split is what the emission owes; see the enum for the case that
 * proved it. Such a jar still gets the same refusal question, because the relation to its member's target is a hand-off
 * too - the answer goes to [MovablePluginJars.memberRelations] or to [MovablePluginJars.withheldRelations].
 *
 * **Non-main jars only.** A plugin's main jar holds the plugin's own patched descriptor, and `dev_dist_plugin_jar` has no
 * attribute for a produced file. So a main jar is out of scope here rather than refused - it has no exclusion class.
 *
 * **A handed-over jar is not a candidate either.** A `content_module_jar` target already packs it and
 * `./build/dev-dist.cmd jars` is its byte gate. The relation names one producer for one destination, so this emission is
 * what keeps a second target off a destination a relation already carries.
 *
 * The walk cost is nil: [derived] is the content derivation the generation run already made, and the library sets come
 * out of it. Nothing here reads a file or lists a directory.
 */
internal fun computeMovablePluginJars(
  module: ModuleDescriptor,
  derived: DerivedPluginContent,
  /** Every jar of the plugin, from the first pass of [computeDerivedPluginPacking]. */
  jars: List<DerivedPluginJar>,
  residue: PluginContentResidue,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): MovablePluginJars {
  val movable = jars.filter { !it.isMainJar && !it.isHandedOver }
  if (movable.isEmpty()) {
    return MovablePluginJars.NONE
  }

  val targets = ArrayList<PluginJarTarget>()
  val excluded = LinkedHashMap<PluginJarExclusion, MutableList<String>>()
  val memberNamed = LinkedHashMap<PluginJarProducer, MutableList<String>>()
  val memberRelations = LinkedHashMap<String, String>()
  val withheldRelations = LinkedHashMap<PluginJarExclusion, MutableList<String>>()

  val scrambles = descriptorResidueOf(module = module, context = context).values.any { it.noEmbedding }
  // Every destination this plugin derives twice. A partition needs the whole set before the first verdict, which is why
  // this is counted ahead of the loop rather than inside it.
  val ambiguous = movable.groupingBy { it.relativeOutputFile }.eachCount().filterValues { it > 1 }.keys
  fun refusalOf(jar: DerivedPluginJar, members: List<ModuleDescriptor?>): PluginJarExclusion? {
    return refusePluginJar(
      jar = jar,
      scrambles = scrambles,
      ambiguousDestinations = ambiguous,
      vetoedMembers = residue.vetoedMembers,
      rawMembers = residue.rawMembers,
      unknownMembers = jar.members.filterIndexedTo(HashSet()) { index, name ->
        members.get(index) == null || name in moduleList.skippedModules
      },
      crossRepositoryMembers = if (!module.isCommunity) {
        emptySet()
      }
      else {
        jar.members.filterIndexedTo(HashSet()) { index, _ -> members.get(index)?.isCommunity == false }
      },
      memberLibraries = derived.memberLibraries,
    )
  }
  for (jar in movable) {
    // Before every refusal, because a refusal is about a target this generator would write and a member-named jar is not
    // one. The two questions are also independent: a member-named jar the residue vetoes still has no target here, and
    // the hand-over needs it counted under its producer rather than under a reason for not emitting.
    val producer = pluginJarProducer(jar = jar) {
      context.hasContentModuleJarTarget(memberName = it, moduleList = moduleList)
    }
    val members = jar.members.map { moduleList.getModuleDescriptorOrNull(it) }
    if (producer != PluginJarProducer.PLUGIN_JAR_TARGET) {
      memberNamed.computeIfAbsent(producer) { ArrayList() }.add(jar.name)
      if (producer == PluginJarProducer.MEMBER_JAR_TARGET) {
        // The same refusals, over the relation instead of over a target. Nothing here writes a jar, and every one of
        // those reasons is a reason the packed jar is not the jar the layout puts at this destination.
        val withheld = refusalOf(jar, members)
        if (withheld == null) {
          memberRelations[jar.members.single()] = jar.relativeOutputFile
        }
        else {
          withheldRelations.computeIfAbsent(withheld) { ArrayList() }.add(jar.name)
        }
      }
      continue
    }
    val refusal = refusalOf(jar, members)
    if (refusal != null) {
      excluded.computeIfAbsent(refusal) { ArrayList() }.add(jar.name)
      continue
    }
    // The union over the members, because one jar's library set is one attribute. A jar that reaches this point either
    // holds several members or carries a name no member has, so the union is a real union here.
    val recordedNames = LinkedHashSet<String>()
    for (member in jar.members) {
      recordedNames.addAll(derived.memberLibraries.getValue(member)!!)
    }
    val libraryLabels = mergedLibraryTargetLabels(
      dependent = module,
      packedModuleNames = jar.members,
      // The plugin rules: the recorded set selects and the module orders. A plugin layout decides which of a member's
      // libraries its jar merges, and evaluating a product layout is the work this generator keeps out of a fragment.
      rules = MergeRules.PLUGIN,
      recordedNames = recordedNames,
      moduleList = moduleList,
      context = context,
      refuse = { println("WARN: ${jar.name} keeps being packed by JarPackager: $it") },
    )
    if (libraryLabels == null) {
      excluded.computeIfAbsent(PluginJarExclusion.UNNAMEABLE_LIBRARY) { ArrayList() }.add(jar.name)
      continue
    }
    targets.add(
      PluginJarTarget(
        relativeOutputFile = jar.relativeOutputFile,
        memberLabels = members.map { context.getBazelDependencyLabel(it!!, module) },
        memberNames = jar.members,
        libraryLabels = libraryLabels,
      )
    )
  }
  return MovablePluginJars(
    targets = targets,
    excluded = excluded,
    memberNamed = memberNamed,
    memberRelations = memberRelations,
    withheldRelations = withheldRelations,
  )
}
