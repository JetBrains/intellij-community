// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import kotlin.io.path.readText

/**
 * One hand-off a plugin offers the candidacy fold: the member, where the plugin puts its jar, and what the jar merges.
 *
 * The derived counterpart of [SimplePluginContentEntry], which reads the same three facts off one report entry.
 * [libraries] is a function of the member alone here, so two plugins can never offer one module two library sets - a
 * property the report side does not have, and the reason a derived fold reports no library disagreement.
 */
internal class DerivedCandidacyOffer(
  @JvmField val moduleName: String,
  @JvmField val relativeOutputFile: String,
  @JvmField val libraries: Set<String>,
  /**
   * Whether [libraries] comes from a residue row rather than from the member's own dependency list.
   *
   * A stated set beats a derived one in [foldDerivedPluginContentCandidacy], and it must: a layout that excluded a
   * module library states the jar for every plugin, because one packing target serves them all. Two residues stating
   * different sets do veto, since a target cannot pack two jars.
   */
  @JvmField val isStated: Boolean = false,
)

/** What one plugin's layout states about its members' jars, derived from the project model. */
internal class DerivedPluginCandidacy(
  @JvmField val offers: List<DerivedCandidacyOffer>,
  /**
   * Members this plugin packs into a jar that is not the member's own, so no packing target may serve them.
   *
   * A veto is repo-global, exactly as [foldPluginContentCandidacy]'s is: one packing target serves every plugin that
   * ships the module, so a plugin that co-packs the module takes the target away from all of them.
   */
  @JvmField val vetoes: List<String>,
  /**
   * Where this plugin puts each member's jar, by module name, relative to the plugin's `lib/`.
   *
   * Every member [deriveMemberJarPath] answers for, whatever [offers] and [vetoes] say about it. The two questions are
   * not the same one. A path is a fact of the convention, and an offer is the narrower claim that one packing target may
   * serve that jar. So a vetoed member keeps its path here, and so does a member the plugin co-packs into its main jar.
   *
   * [ContentResidueSection.memberJars] states only what this map does not answer, so the writer needs every path.
   */
  @JvmField val memberPaths: Map<String, String>,
  /**
   * The module libraries each member's jar merges, by module name, and `null` for a member with a library it cannot name.
   *
   * The same set [DerivedCandidacyOffer.libraries] holds, for **every** member and not for the offered half.
   * [computeMovablePluginJars] needs it for a jar no packing target serves: such a jar has no offer, and the libraries
   * still go into it. It is the read [readMemberJar] already made, carried out rather than made a second time.
   */
  @JvmField val memberLibraries: Map<String, Set<String>?>,
)

/**
 * Where each of [module]'s members' jars goes, and which of those jars one packing target may serve.
 *
 * The candidacy fold asks one question - is this module's jar a plain, product-independent, self-named jar, and which
 * libraries does it merge. This states the answer from what the model already holds:
 *
 * - the loading rule comes from the plugin's own `<content>`;
 * - the `pack-content-into-plugin-jar` marker and the `package` attribute come from the member's own descriptor, which
 *   is the same file `contentDescriptorLabels` already names for the descriptor leaf;
 * - the merged library set comes from the member's production-scope module libraries.
 *
 * [deriveMemberJarPath] holds the path rule and names the three inputs that stay stated. [deriveMemberJar] puts the
 * eligibility gate on top of it. This function folds the two answers over the plugin's members, and the repo-global
 * answer for a module two halves of the repository see differently is
 * [DevDistPluginModelTables.contentCandidateOverrides].
 *
 * Fail closed. A member whose descriptor this cannot read is vetoed rather than offered, because an offered jar that the
 * distribution does not pack goes missing and is noticed at class-load time. A plugin with no `META-INF/plugin.xml` of
 * its own has no closure at all, and then only [PluginContentResidue.extraMembers] states its members.
 *
 * [closure] is a parameter so that a caller which already walked it hands it over. [derivePluginContent] is that caller,
 * and it needs the same walk for the member set.
 */
internal fun derivePluginContentCandidacy(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  residue: PluginContentResidue = contentResidueOf(module = module, context = context),
  closure: WalkedContentModules? = derivePluginContentClosure(module = module, moduleList = moduleList, context = context),
): DerivedPluginCandidacy {
  val offers = ArrayList<DerivedCandidacyOffer>()
  val vetoes = ArrayList<String>(residue.vetoedMembers)
  val memberPaths = HashMap<String, String>()
  val memberLibraries = HashMap<String, Set<String>?>()
  val seen = HashSet<String>()
  val mainJarName = pluginJarPlacementOf(mainModule = module.module.name, context = context).mainJarName
  // One library read per member, and only for a member that needs one. The walk costs a dependency list per member and
  // this generator visits every member of every one of the 516 plugins, so an unconditional read costs about 9 s of the
  // run - measured on 2026-09-01.
  val libraryReads = HashMap<String, MergedMemberLibraries>()
  fun librariesOf(member: ModuleDescriptor): MergedMemberLibraries {
    return libraryReads.computeIfAbsent(member.module.name) { mergedLibrariesOf(member = member, residue = residue, context = context) }
  }
  for (name in residue.extraMembers) {
    val member = moduleList.getModuleDescriptorOrNull(name) ?: continue
    val jar = readMemberJar(
      member = member,
      loadingRule = null,
      residue = residue,
      mainJarName = mainJarName,
      libraries = ::librariesOf,
    )
    // A member of a jar the residue names needs its library set even with no descriptor of its own, and a
    // `PluginLayout.withModule` member is a plain runtime module that usually ships none. Every other descriptor-less
    // member has no derived jar at all, so the plugin's main jar holds it and no packing target ever asks.
    if (jar != null || name in residue.memberJars) {
      memberLibraries.putIfAbsent(name, librariesOf(member).names)
    }
    if (jar == null) {
      continue
    }
    memberPaths.putIfAbsent(name, jar.relativeOutputFile)
    // A stated member gets an offer only where the residue states its jar as well. A `PluginLayout.withModule` member is
    // packed from its raw output into a jar of the plugin's, which is no jar of the member's own, and 359 of the 399
    // stated members are that. So the offer is opt-in, and `lib_root_jars` or `separate_jars` is the opt-in.
    if (name !in residue.libRootJars && name !in residue.separateJars) {
      continue
    }
    if (!seen.add(name)) {
      continue
    }
    jar.offer?.let(offers::add)
  }
  for (rawName in (closure ?: EMPTY_WALKED_CONTENT_MODULES).moduleNames) {
    // `computeModuleSourcesByContent` skips a `moduleName/descriptorName` element outright, so such a member reaches no
    // jar of its own and no veto either.
    if (rawName.contains('/') || !seen.add(rawName)) {
      continue
    }
    val member = moduleList.getModuleDescriptorOrNull(rawName)
    if (member == null) {
      if (rawName !in residue.vetoedMembers) {
        vetoes.add(rawName)
      }
      continue
    }
    val jar = readMemberJar(
      member = member,
      loadingRule = closure?.loadingRules?.get(rawName),
      residue = residue,
      mainJarName = mainJarName,
      libraries = ::librariesOf,
    )
    if (jar != null || rawName in residue.memberJars) {
      memberLibraries.put(rawName, librariesOf(member).names)
    }
    if (jar != null) {
      memberPaths.put(rawName, jar.relativeOutputFile)
    }
    // A vetoed member keeps the path above and makes no offer. The veto answers who packs the jar, and the convention
    // answers where the jar goes, so one of the two answers survives the other.
    if (rawName in residue.vetoedMembers) {
      continue
    }
    val offer = jar?.offer
    if (offer == null) {
      vetoes.add(rawName)
    }
    else {
      offers.add(offer)
    }
  }
  return DerivedPluginCandidacy(
    offers = offers,
    vetoes = vetoes,
    memberPaths = memberPaths,
    memberLibraries = memberLibraries,
  )
}

/**
 * Where a plugin whose main jar is [mainJarName] puts [moduleName]'s jar, relative to its own `lib/`.
 *
 * The convention plus three stated corrections. The convention is `computeOutputJarPath` of `autoLayout.kt` with
 * `computeEmbeddedOutputJarPath`, `needsSeparateJar` and `getDefaultJarName`. Three inputs of that function are
 * `PluginLayout` state. Evaluating a product layout is the work this generator keeps out of a fragment action. So each
 * one of the three is stated rather than derived:
 *
 * 1. `modulesWithCustomPath` holds the jar `PluginLayout.withModule(name, jarName)` names, and the authority answers no
 *    path at all for such a member. This answers the convention's path instead, so the two differ and
 *    [ContentResidueSection.memberJars] states the jar. A `withModule` path holding a `/` never enters that set. Such a
 *    member really sits in the convention's jar as well as in the named one. A flat `withModule` path does enter the
 *    set, and the layout then packs the member in the named jar alone. So this answers a jar no distribution holds for
 *    such a member with no row, and [comparePluginJarPlan] reports it under `derived, not packed`;
 * 2. the frontend module filter reaches the path twice. `isPluginModulePackedIntoSeparateJar` reads it, and
 *    `getDefaultJarName` renames the main jar to `<main>-frontend.jar` through it.
 *    [ContentResidueSection.separateJars] states the first and [ContentResidueSection.memberJars] the second;
 * 3. `getModulesWithExcludedModuleLibraries` is the other half of `isPluginModulePackedIntoSeparateJar`, and
 *    [ContentResidueSection.mergedLibraries] states the library set the layout really merges. [mergesLibraries] is that
 *    set where a row exists.
 *
 * An answer for every member, and never `null`. A member the convention gives no jar of its own is co-packed into
 * [mainJarName], which is what `getDefaultJarName` returns for it. [composeDerivedPluginJars] reads that answer the
 * same way.
 */
internal fun deriveMemberJarPath(
  moduleName: String,
  loadingRule: String?,
  packIntoPluginJar: Boolean,
  hasPackageAttribute: Boolean,
  mergesLibraries: Boolean,
  residue: PluginContentResidue,
  mainJarName: String,
): String {
  // The two stated corrections first. Each row exists because the convention below answers another path.
  if (moduleName in residue.libRootJars) {
    return "$moduleName.jar"
  }
  if (moduleName in residue.separateJars) {
    return "modules/$moduleName.jar"
  }
  if (loadingRule == EMBEDDED_LOADING_RULE) {
    // `computeEmbeddedOutputJarPath`. The marker sends the member into the plugin's main jar, and every other embedded
    // member gets `lib/<module>.jar`, whatever libraries that jar merges.
    return if (packIntoPluginJar) mainJarName else "$moduleName.jar"
  }
  // `needsSeparateJar`. The marker wins outright. Then a descriptor with no `package` attribute cannot be loaded from
  // the plugin jar, and a module declaring a module library is put in its own jar so that the library travels with it.
  if (!packIntoPluginJar && (!hasPackageAttribute || mergesLibraries)) {
    return "modules/$moduleName.jar"
  }
  return mainJarName
}

/** One member's jar: where the plugin puts it, and the offer a packing target may serve, where there is one. */
internal class DerivedMemberJar(
  @JvmField val relativeOutputFile: String,
  @JvmField val offer: DerivedCandidacyOffer?,
)

/**
 * The module libraries one member's jar merges: the set, and whether the residue states it.
 *
 * [names] is `null` for a member with a module library this generator cannot name; see [distributionLibraryName].
 */
internal class MergedMemberLibraries(
  @JvmField val names: Set<String>?,
  /** See [DerivedCandidacyOffer.isStated]. */
  @JvmField val isStated: Boolean,
)

/**
 * What [member]'s jar merges: the residue's row where there is one, and the member's own module libraries otherwise.
 *
 * Its own function because two readers need the answer and it must be one read. [readMemberJar] takes it for the path
 * rule, and [DerivedPluginCandidacy.memberLibraries] carries it out for the jar the plugin packs the member into.
 */
internal fun mergedLibrariesOf(
  member: ModuleDescriptor,
  residue: PluginContentResidue,
  context: BazelBuildFileGenerator,
): MergedMemberLibraries {
  val stated = residue.mergedLibraries.get(member.module.name)
  return MergedMemberLibraries(
    names = stated ?: productionModuleLibraryNames(module = member, context = context),
    isStated = stated != null,
  )
}

/**
 * [member]'s jar under [mainJarName], or `null` when no resource root holds the member's own descriptor.
 *
 * The model read of [deriveMemberJar]: the two facts the path rule needs out of the member's own descriptor.
 *
 * `null` is the one case this generator can state no path for. A member with no readable descriptor has no
 * `packIntoPluginJar` and no `package` attribute to read, so the caller vetoes it.
 *
 * [libraries] is a function and not a set, so that the descriptor decides whether the library walk runs at all. The
 * caller's own comment holds the measurement.
 */
private fun readMemberJar(
  member: ModuleDescriptor,
  loadingRule: String?,
  residue: PluginContentResidue,
  mainJarName: String,
  libraries: (ModuleDescriptor) -> MergedMemberLibraries,
): DerivedMemberJar? {
  val descriptor = memberDescriptor(member) ?: return null
  val merged = libraries(member)
  return deriveMemberJar(
    moduleName = member.module.name,
    loadingRule = loadingRule,
    packIntoPluginJar = descriptor.packIntoPluginJar,
    hasPackageAttribute = descriptor.hasPackageAttribute,
    libraries = merged.names,
    isStated = merged.isStated,
    residue = residue,
    mainJarName = mainJarName,
  )
}

/**
 * [moduleName]'s jar under [mainJarName], and the offer one packing target may serve, where there is one.
 *
 * The path is [deriveMemberJarPath]. The offer is that path narrowed to a jar of the member's own:
 * `lib/modules/<module>.jar`, or `lib/<module>.jar` for a jar that merges no module library.
 * `simplePluginContentEntryPath` holds the reason the second shape is restricted, and the Kotlin plugin is the case its
 * KDoc records.
 *
 * A path for every member, and never `null`. The two questions are not the same one, so a member this generator cannot
 * offer keeps the path the convention gives it.
 *
 * [libraries] is `null` for a member whose module library has no single jar; see [distributionLibraryName]. Such a
 * member gets a path and no offer. The path rule still reads `true` for the merge. A library this generator cannot name
 * is a module library all the same, and that is the only fact `needsSeparateJar` asks for.
 */
internal fun deriveMemberJar(
  moduleName: String,
  loadingRule: String?,
  packIntoPluginJar: Boolean,
  hasPackageAttribute: Boolean,
  libraries: Set<String>?,
  isStated: Boolean,
  residue: PluginContentResidue,
  mainJarName: String,
): DerivedMemberJar {
  val relativeOutputFile = deriveMemberJarPath(
    moduleName = moduleName,
    loadingRule = loadingRule,
    packIntoPluginJar = packIntoPluginJar,
    hasPackageAttribute = hasPackageAttribute,
    mergesLibraries = libraries == null || libraries.isNotEmpty(),
    residue = residue,
    mainJarName = mainJarName,
  )
  if (libraries == null) {
    return DerivedMemberJar(relativeOutputFile = relativeOutputFile, offer = null)
  }
  val servesOneMember = when (relativeOutputFile) {
    "modules/$moduleName.jar" -> true
    "$moduleName.jar" -> moduleName in residue.libRootJars || libraries.isEmpty()
    else -> false
  }
  return DerivedMemberJar(
    relativeOutputFile = relativeOutputFile,
    offer = if (servesOneMember) {
      DerivedCandidacyOffer(
        moduleName = moduleName,
        relativeOutputFile = relativeOutputFile,
        libraries = libraries,
        isStated = isStated,
      )
    }
    else {
      null
    },
  )
}

/** `ModuleLoadingRule.EMBEDDED` as the `loading` attribute spells it. */
internal const val EMBEDDED_LOADING_RULE: String = "embedded"

/** The two facts `computeOutputJarPath` reads out of a content module's own descriptor. */
private class MemberDescriptorFacts(
  @JvmField val packIntoPluginJar: Boolean,
  @JvmField val hasPackageAttribute: Boolean,
)

/**
 * [module]'s own `<module>.xml`, read for the two facts the jar path depends on, or `null` when no resource root holds it.
 *
 * The same file `contentDescriptorLabels` names for the descriptor leaf, and `_find_descriptor_rel_paths` in
 * `@community//build:jps_model.bzl` probes. So this parses a file the run already resolves, and it never searches the
 * tree.
 */
private fun memberDescriptor(module: ModuleDescriptor): MemberDescriptorFacts? {
  val loadPath = module.module.name + ".xml"
  val file = descriptorFiles(module = module, loadPath = loadPath).firstOrNull() ?: return null
  val text = file.readText()
  return MemberDescriptorFacts(
    packIntoPluginJar = PACK_CONTENT_INTO_PLUGIN_JAR_MARKER.containsMatchIn(text),
    hasPackageAttribute = JDOMUtil.load(text).getAttributeValue("package") != null,
  )
}

/** `PACK_CONTENT_INTO_PLUGIN_JAR_MARKER_REGEX` of `autoLayout.kt`, which is the authority this mirrors. */
private val PACK_CONTENT_INTO_PLUGIN_JAR_MARKER =
  Regex("""<!--\s+intellij-build:\s+pack-content-into-plugin-jar\s+-->""")

/**
 * The distribution names of [module]'s production-scope module libraries, or `null` when one of them has no name.
 *
 * The set `ModuleEntry.libraries` records for a plugin content module jar. A project library is out: a plugin merges one
 * only for an `auto` `PluginLayout`, which this generator does not evaluate, and an entry recording one vetoes the module
 * on the report path too.
 *
 * `null` for an unnamed library with no single jar, for the reason [distributionLibraryName] gives: a jar this generator
 * cannot name is a jar it refuses to pack.
 */
internal fun productionModuleLibraryNames(module: ModuleDescriptor, context: BazelBuildFileGenerator): Set<String>? {
  val result = LinkedHashSet<String>()
  for (element in module.module.dependenciesList.dependencies) {
    if (element !is JpsLibraryDependency) {
      continue
    }
    val scope = context.javaExtensionService.getDependencyExtension(element)?.scope ?: continue
    if (scope != JpsJavaDependencyScope.COMPILE && scope != JpsJavaDependencyScope.RUNTIME) {
      continue
    }
    val parentReference = element.libraryReference.parentReference
    if (parentReference.resolve() is JpsGlobal || parentReference !is JpsModuleReference) {
      continue
    }
    val library = element.library ?: continue
    result.add(distributionLibraryName(library) ?: return null)
  }
  return result
}

/**
 * The repo-global candidate set, folded over what the plugins' own models state instead of over their reports.
 *
 * The AND of [foldPluginContentCandidacy], with the same tri-state and the same last word for [overrides]: unseen,
 * agreed on a library set, or vetoed, and a vetoed module never comes back. What is different is only where a fact comes
 * from, so a module both folds see reaches the same verdict by the same rule.
 */
internal fun foldDerivedPluginContentCandidacy(
  plugins: List<DerivedPluginCandidacy>,
  overrides: Map<String, Set<String>?>,
): Map<String, Set<String>> {
  val agreed = HashMap<String, Set<String>>()
  val stated = HashMap<String, Set<String>>()
  val vetoed = HashSet<String>()
  // Every veto first, so that a module one plugin co-packs is refused whatever order the plugins are read in. That is
  // what makes the fold an AND, and the report-side fold reaches it by never letting a vetoed module back in.
  for (plugin in plugins) {
    for (name in plugin.vetoes) {
      vetoed.add(name)
    }
  }
  for (plugin in plugins) {
    for (offer in plugin.offers) {
      if (offer.moduleName in vetoed) {
        continue
      }
      if (offer.isStated) {
        val recorded = stated.putIfAbsent(offer.moduleName, offer.libraries)
        if (recorded != null && recorded != offer.libraries) {
          reportCandidacyLibraryDisagreement(offer.moduleName, recorded, offer.libraries)
          stated.remove(offer.moduleName)
          vetoed.add(offer.moduleName)
        }
        continue
      }
      val recorded = agreed.putIfAbsent(offer.moduleName, offer.libraries)
      if (recorded != null && recorded != offer.libraries) {
        // Unreachable while a derived library set is a function of the member alone. Kept because the fold's contract is
        // an AND over plugins, and a rule that later makes the set plugin-dependent must veto rather than pick one.
        reportCandidacyLibraryDisagreement(offer.moduleName, recorded, offer.libraries)
        agreed.remove(offer.moduleName)
        vetoed.add(offer.moduleName)
      }
    }
  }
  // After the merge, not before it. A derived disagreement vetoes a module without taking it out of `stated`, so only a
  // removal that follows the merge refuses every vetoed module.
  agreed.putAll(stated)
  agreed.keys.removeAll(vetoed)

  for ((name, libraries) in overrides) {
    if (libraries == null) {
      agreed.remove(name)
    }
    else {
      agreed.put(name, libraries)
    }
  }
  return agreed
}

/** The line both folds print, so that a reader comparing two runs compares two identical lines. */
private fun reportCandidacyLibraryDisagreement(moduleName: String, first: Set<String>, second: Set<String>) {
  println(
    "WARN: $moduleName keeps being packed by JarPackager: its plugins disagree about the libraries" +
    " merged into its jar (${first.sorted()} against ${second.sorted()})"
  )
}

/**
 * What a community-only run of the derived fold cannot decide for itself, one checked-in row per line.
 *
 * ADR 0007 rule 4. The fold is a repo-global AND, so a checkout holding only the community half reaches a different
 * verdict for a community module the ultimate half has an opinion about - and the converter writes the same attributes
 * in both, which is what `Assert Bazel Files Are In Sync With JPS Model (Community Only)` fails on.
 * The `$CONTENT_CANDIDATE_OVERRIDES_SECTION` section of `$PLUGIN_MODEL_TABLES_FILE_NAME` is the correction, and this is
 * the answer that section has to state.
 *
 * The row is the answer of the **ultimate** run, which is also the global answer: `-M` means some plugin outside
 * `community/` vetoes M, and `+M` plus a sorted library list means the plugins that offer M are all outside `community/`
 * and all agree. So the converter applies these unconditionally, with no notion of which half it is running over.
 *
 * Both arms fold with no override in play, so the rows are a function of the project model alone. That is what keeps the
 * file out of its own input: a run cannot confirm a stale row by reading it.
 *
 * The community half is the plugins whose main module is a community module. That is the set a community-only checkout
 * converts, and its `<content>`, its residue and its members' descriptors are all files that checkout has.
 *
 * A community-only run answers an empty list, and it must: `global` is then the community fold itself, so the two arms
 * agree everywhere and there is nothing such a run could say about the half it does not have.
 */
internal fun communityOnlyCandidacyOverrideRows(moduleList: ModuleList): List<String> {
  val candidacies = moduleList.derivedPluginCandidacies
  val global = foldDerivedPluginContentCandidacy(plugins = candidacies.map { it.second }, overrides = emptyMap())
  val communityOnly = foldDerivedPluginContentCandidacy(
    plugins = candidacies.mapNotNull { (module, candidacy) -> candidacy.takeIf { module.isCommunity } },
    overrides = emptyMap(),
  )
  val communityModules = moduleList.community.mapTo(HashSet()) { it.module.name }
  val result = ArrayList<String>()
  for (name in (global.keys + communityOnly.keys).sorted()) {
    if (name !in communityModules) {
      // A module no community-only conversion emits a target for needs no correction, whatever either fold says.
      continue
    }
    val globalAnswer = global.get(name)
    if (globalAnswer == communityOnly.get(name)) {
      continue
    }
    if (globalAnswer == null) {
      result.add("-$name")
      continue
    }
    for (library in globalAnswer) {
      // One line per module, with the libraries as the rest of the line. A name holding the separator would read back as
      // two libraries, so the writer refuses it rather than the reader mis-splitting it. No library name in the
      // repository holds a space today.
      check(library.isNotEmpty() && !library.any(Char::isWhitespace)) {
        "A library name with whitespace cannot be recorded in $PLUGIN_MODEL_TABLES_FILE_NAME:" +
        " `$library` of $name"
      }
    }
    result.add((sequenceOf("+$name") + globalAnswer.sorted()).joinToString(" "))
  }
  return result
}
