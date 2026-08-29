// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * The derived counterpart of [SimplePluginContentEntry], which reads the same three facts off one `plugin-content.yaml`
 * entry. [libraries] is a function of the member alone here, so two plugins can never offer one module two library sets -
 * a property the report side does not have, and the reason a derived fold reports no library disagreement.
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
)

/**
 * Where each of [module]'s members' jars goes, reproducing `computeOutputJarPath` of `autoLayout.kt` from the model.
 *
 * The candidacy fold asks one question - is this module's jar a plain, product-independent, self-named jar, and which
 * libraries does it merge - and until now the answer came from every checked-in `plugin-content.yaml`. That is the one
 * input Phase 0 of this arc held constant, so every prepack figure it measured rests on it. This states the same answer
 * from what the model already holds:
 *
 * - the loading rule comes from the plugin's own `<content>`;
 * - the `pack-content-into-plugin-jar` marker and the `package` attribute come from the member's own descriptor, which
 *   is the same file `contentDescriptorLabels` already names for the descriptor leaf;
 * - the merged library set comes from the member's production-scope module libraries.
 *
 * Three inputs of the original are `PluginLayout` state and stay out: `modulesWithCustomPath`, the frontend module
 * filter, and `getModulesWithExcludedModuleLibraries`. Evaluating a product layout is the work this generator exists to
 * keep out of a fragment action, so where one of those decides, the answer is stated rather than derived - see
 * [PLUGIN_CONTENT_CANDIDATE_OVERRIDES_FILE_NAME].
 *
 * Fail closed. A member whose descriptor this cannot read is vetoed rather than offered, because an offered jar that the
 * distribution does not pack goes missing and is noticed at class-load time. A plugin with no `META-INF/plugin.xml` of
 * its own has no closure at all, and then only [PluginContentResidue.extraMembers] states its members.
 */
internal fun derivePluginContentCandidacy(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  residue: PluginContentResidue = contentResidueOf(module),
): DerivedPluginCandidacy {
  val closure = derivePluginContentClosure(module = module, moduleList = moduleList, context = context)
  val offers = ArrayList<DerivedCandidacyOffer>()
  val vetoes = ArrayList<String>(residue.vetoedMembers)
  val seen = HashSet<String>()
  // A stated member gets an offer only where the residue states its jar as well. A `PluginLayout.withModule` member is
  // packed from its raw output into a jar of the plugin's, which is no jar of the member's own, and 359 of the 399 stated
  // members are that. So the offer is opt-in, and `lib_root_jars` or `separate_jars` is the opt-in.
  for (name in residue.extraMembers) {
    if (name !in residue.libRootJars && name !in residue.separateJars) {
      continue
    }
    if (!seen.add(name)) {
      continue
    }
    val member = moduleList.getModuleDescriptorOrNull(name) ?: continue
    deriveMemberJar(member = member, loadingRule = null, residue = residue, context = context)?.let(offers::add)
  }
  for (rawName in (closure ?: EMPTY_WALKED_CONTENT_MODULES).moduleNames) {
    // `computeModuleSourcesByContent` skips a `moduleName/descriptorName` element outright, so such a member reaches no
    // jar of its own and no veto either.
    if (rawName.contains('/') || !seen.add(rawName)) {
      continue
    }
    if (rawName in residue.vetoedMembers) {
      continue
    }
    val member = moduleList.getModuleDescriptorOrNull(rawName)
    if (member == null) {
      vetoes.add(rawName)
      continue
    }
    val offer = deriveMemberJar(
      member = member,
      loadingRule = closure?.loadingRules?.get(rawName),
      residue = residue,
      context = context,
    )
    if (offer == null) {
      vetoes.add(rawName)
    }
    else {
      offers.add(offer)
    }
  }
  return DerivedPluginCandidacy(offers = offers, vetoes = vetoes)
}

/** One member's jar, or `null` when the plugin packs the member somewhere that is not a jar of its own. */
private fun deriveMemberJar(
  member: ModuleDescriptor,
  loadingRule: String?,
  residue: PluginContentResidue,
  context: BazelBuildFileGenerator,
): DerivedCandidacyOffer? {
  val moduleName = member.module.name
  val descriptor = memberDescriptor(member) ?: return null
  val statedLibraries = residue.mergedLibraries.get(moduleName)
  val libraries = statedLibraries ?: productionModuleLibraryNames(module = member, context = context) ?: return null
  val isStated = statedLibraries != null
  if (moduleName in residue.libRootJars) {
    return DerivedCandidacyOffer(
      moduleName = moduleName,
      relativeOutputFile = "$moduleName.jar",
      libraries = libraries,
      isStated = isStated,
    )
  }
  if (moduleName in residue.separateJars) {
    return DerivedCandidacyOffer(
      moduleName = moduleName,
      relativeOutputFile = "modules/$moduleName.jar",
      libraries = libraries,
      isStated = isStated,
    )
  }
  if (descriptor.packIntoPluginJar) {
    return null
  }
  if (loadingRule == EMBEDDED_LOADING_RULE) {
    // `computeEmbeddedOutputJarPath`: the jar goes to `lib/<module>.jar`. `simplePluginContentEntryPath` accepts that
    // path only for a jar merging no module library, and the Kotlin plugin is the case its KDoc records.
    return if (libraries.isEmpty()) {
      DerivedCandidacyOffer(moduleName = moduleName, relativeOutputFile = "$moduleName.jar", libraries = emptySet())
    }
    else {
      null
    }
  }
  // `needsSeparateJar`: a descriptor with no `package` attribute cannot be loaded from the plugin jar, and a module
  // declaring a module library is put in its own jar so that the library travels with it. Anything else is co-packed
  // into the plugin's main jar, which is a jar this generator does not pack.
  if (descriptor.hasPackageAttribute && libraries.isEmpty()) {
    return null
  }
  return DerivedCandidacyOffer(
    moduleName = moduleName,
    relativeOutputFile = "modules/$moduleName.jar",
    libraries = libraries,
    isStated = isStated,
  )
}

/** `ModuleLoadingRule.EMBEDDED` as the `loading` attribute spells it. */
private const val EMBEDDED_LOADING_RULE: String = "embedded"

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
  agreed.keys.removeAll(vetoed)
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
 * Compares the two candidacy folds and reports what a residue would have to state.
 *
 * ADR 0007 rule 5, applied to the one input the content comparison held constant. The report fold is the authority here,
 * because the checked-in `BUILD.bazel` was written with it: a module the derived fold adds would hand a jar over that no
 * distribution packs, and one it drops would leave a `prepacked_content_modules` relation behind.
 *
 * Both sides are folded with an empty override map, so the difference this prints is the whole residue and not the part
 * the checked-in file does not already cover.
 */
internal fun comparePluginContentCandidacy(
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  out: (String) -> Unit,
) {
  val reportFold = foldPluginContentCandidacy(
    reports = moduleList.allModules.mapNotNull { it.pluginContentReport },
    overrides = emptyMap(),
  )
  val candidacies = ArrayList<DerivedPluginCandidacy>()
  var plugins = 0
  for (module in moduleList.allModules) {
    if (module.pluginContentReport == null) {
      continue
    }
    plugins++
    candidacies.add(derivePluginContentCandidacy(module = module, moduleList = moduleList, context = context))
  }
  val derivedFold = foldDerivedPluginContentCandidacy(plugins = candidacies, overrides = emptyMap())

  val onlyInDerived = (derivedFold.keys - reportFold.keys).sorted()
  val onlyInReport = (reportFold.keys - derivedFold.keys).sorted()
  val differingLibraries = derivedFold.keys.intersect(reportFold.keys)
    .filter { derivedFold.get(it) != reportFold.get(it) }
    .sorted()

  out("")
  out("candidacy fold: plugins=$plugins reportCandidates=${reportFold.size} derivedCandidates=${derivedFold.size}")
  out("  only the derived fold offers (a `-` residue row): ${onlyInDerived.size}")
  out("  only the report fold offers (a `+` residue row): ${onlyInReport.size}")
  out("  both offer, library sets differ (a `+` residue row): ${differingLibraries.size}")
  for (name in onlyInDerived) {
    out("  DERIVED-ONLY $name libraries=${derivedFold.get(name)?.sorted()}")
  }
  for (name in onlyInReport) {
    out("  REPORT-ONLY $name libraries=${reportFold.get(name)?.sorted()}")
  }
  for (name in differingLibraries) {
    out("  LIBRARIES $name report=${reportFold.get(name)?.sorted()} derived=${derivedFold.get(name)?.sorted()}")
  }
  reportCommunityOnlyCandidacyDelta(moduleList = moduleList, context = context, global = derivedFold, out = out)
}

/**
 * What a community-only run of the derived fold cannot decide for itself, and what the checked-in override says.
 *
 * ADR 0007 rule 4. The fold is a repo-global AND, so a checkout holding only the community half reaches a different
 * verdict for a community module the ultimate half has an opinion about - and the converter writes the same attributes in
 * both, which is what `Assert Bazel Files Are In Sync With JPS Model (Community Only)` fails on.
 * `dev_dist_plugin_content_candidate_overrides.txt` is the correction, and the plan generator computes it from the
 * **report** fold. This measures whether the derived fold needs the same corrections.
 *
 * The community half is the plugins whose main module is a community module. That is the set a community-only checkout
 * converts, and its `<content>`, its residue and its members' descriptors are all files that checkout has.
 */
private fun reportCommunityOnlyCandidacyDelta(
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  global: Map<String, Set<String>>,
  out: (String) -> Unit,
) {
  val communityOnly = foldDerivedPluginContentCandidacy(
    plugins = moduleList.community.mapNotNull { module ->
      if (isDevDistContentPlugin(module = module, context = context)) {
        derivePluginContentCandidacy(module = module, moduleList = moduleList, context = context)
      }
      else {
        null
      }
    },
    overrides = emptyMap(),
  )
  val communityModules = moduleList.community.mapTo(HashSet()) { it.module.name }
  val needed = ArrayList<String>()
  for (name in (global.keys + communityOnly.keys).sorted()) {
    if (name !in communityModules) {
      // A module no community-only conversion emits a target for needs no correction, whatever either fold says.
      continue
    }
    val globalAnswer = global.get(name)
    if (globalAnswer == communityOnly.get(name)) {
      continue
    }
    needed.add(if (globalAnswer == null) "-$name" else (sequenceOf("+$name") + globalAnswer.sorted()).joinToString(" "))
  }
  val checkedIn = readPluginContentCandidateOverrides(
    (context.ultimateRoot?.resolve("community") ?: context.communityRoot)
      .resolve("build/$PLUGIN_CONTENT_CANDIDATE_OVERRIDES_FILE_NAME")
  )
  val checkedInLines = checkedIn.entries.asSequence()
    .map { (name, libraries) ->
      if (libraries == null) "-$name" else (sequenceOf("+$name") + libraries.sorted()).joinToString(" ")
    }
    .sorted()
    .toList()
  out("")
  out("community-only locality of the derived fold: rows needed=${needed.size} rows checked in=${checkedInLines.size}")
  for (row in needed - checkedInLines.toSet()) {
    out("  MISSING $row")
  }
  for (row in checkedInLines - needed.toSet()) {
    out("  STALE $row")
  }
}
