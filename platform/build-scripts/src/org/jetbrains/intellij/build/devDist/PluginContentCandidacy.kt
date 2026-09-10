// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * One hand-off a plugin offers the candidacy fold: the member, where the plugin puts its jar, and what the jar merges.
 *
 * [libraries] is a function of the member alone, so two plugins can never offer one module two derived library sets.
 */
@ApiStatus.Internal
class DerivedCandidacyOffer(
  @JvmField val moduleName: String,
  @JvmField val relativeOutputFile: String,
  @JvmField val libraries: Set<String>,
  /**
   * Whether a layout decision narrowed [libraries] below the member's own dependency list.
   *
   * A stated set beats a derived one in [foldDerivedPluginContentCandidacy]. A layout that excluded a module library
   * states the jar for every plugin, because one packing target serves them all. Two residues stating different
   * sets do veto, since a target cannot pack two jars.
   */
  @JvmField val isStated: Boolean = false,
)

/** What one plugin's layout states about its members' jars, derived from the project model. */
@ApiStatus.Internal
class DerivedPluginCandidacy(
  @JvmField val offers: List<DerivedCandidacyOffer>,
  /**
   * Members this plugin packs into a jar that is not the member's own, so no packing target may serve them.
   *
   * A veto is repo-global: one packing target serves every plugin that ships the module, so a plugin that co-packs the
   * module takes the target away from all of them.
   */
  @JvmField val vetoes: List<String>,
  /**
   * Where this plugin puts each member's jar, by module name, relative to the plugin's `lib/`.
   *
   * Every member [deriveMemberJarPath] answers for, whatever [offers] and [vetoes] say about it. A path is a fact of
   * the convention, and an offer is the narrower claim that one packing target may serve that jar.
   */
  @JvmField val memberPaths: Map<String, String>,
  /**
   * The module libraries each member's jar merges, by module name, and `null` for a member with a library it cannot name.
   *
   * The same set [DerivedCandidacyOffer.libraries] holds, for every member and not for the offered half.
   */
  @JvmField val memberLibraries: Map<String, Set<String>?>,
)

/**
 * Where each member's jar of the plugin [mainModule] goes, and which of those jars one packing target may serve.
 *
 * The loading rule comes from the plugin's own `<content>`. The `pack-content-into-plugin-jar` marker and the
 * `package` attribute come from the member's own descriptor. The merged library set comes from the member's
 * production-scope module libraries. [deriveMemberJarPath] holds the path rule, and [deriveMemberJar] puts the
 * eligibility gate on top of it.
 *
 * Fail closed. A member whose descriptor this cannot read is vetoed rather than offered, because an offered jar that
 * the distribution does not pack is noticed at class-load time.
 */
@ApiStatus.Internal
fun derivePluginContentCandidacy(
  mainModule: String,
  mainJarName: String,
  findModule: (String) -> JpsModule?,
  frontend: FrontendCompatibility,
  residue: PluginContentResidue,
  closure: WalkedContentModules?,
): DerivedPluginCandidacy {
  val offers = ArrayList<DerivedCandidacyOffer>()
  val vetoes = ArrayList<String>(residue.vetoedMembers)
  val memberPaths = HashMap<String, String>()
  val memberLibraries = HashMap<String, Set<String>?>()
  val seen = HashSet<String>()
  // One library read per member, and only for a member that needs one.
  val libraryReads = HashMap<String, MergedMemberLibraries>()
  fun librariesOf(member: JpsModule): MergedMemberLibraries {
    return libraryReads.computeIfAbsent(member.name) { mergedLibrariesOf(member = member, residue = residue) }
  }
  // The plugin's own `<content>` first, because it carries each member's loading rule.
  for (rawName in (closure ?: EMPTY_WALKED_CONTENT_MODULES).moduleNames) {
    // The build skips a `moduleName/descriptorName` element outright, so such a member reaches no jar of its own.
    if (rawName.contains('/') || !seen.add(rawName)) {
      continue
    }
    val member = findModule(rawName)
    if (member == null) {
      if (rawName !in residue.vetoedMembers) {
        vetoes.add(rawName)
      }
      continue
    }
    val jar = readMemberJar(
      member = member,
      loadingRule = closure?.loadingRules?.get(rawName),
      mainModule = mainModule,
      mainJarName = mainJarName,
      libraries = ::librariesOf,
      frontend = frontend,
    )
    if (jar != null || rawName in residue.memberJars) {
      memberLibraries.put(rawName, librariesOf(member).names)
    }
    if (jar != null) {
      memberPaths.put(rawName, jar.relativeOutputFile)
    }
    // A vetoed member keeps the path above and makes no offer.
    if (rawName in residue.vetoedMembers) {
      continue
    }
    // A member whose library the platform ships as a bare jar merges less than its own jar would, and the bare jar is
    // one no packing target writes.
    val offer = jar?.offer
    if (offer == null || librariesOf(member).takenOutByPlatform.isNotEmpty()) {
      vetoes.add(rawName)
    }
    else if (!isPackedIntoRenamedJar(member = rawName, residue = residue)) {
      offers.add(offer)
    }
  }
  for (name in residue.extraMembers) {
    if (name in seen) {
      continue
    }
    val member = findModule(name) ?: continue
    val jar = readMemberJar(
      member = member,
      loadingRule = null,
      mainModule = mainModule,
      mainJarName = mainJarName,
      libraries = ::librariesOf,
      frontend = frontend,
    )
    // A member of a jar the layout names needs its library set even with no descriptor of its own.
    if (jar != null || name in residue.memberJars) {
      memberLibraries.putIfAbsent(name, librariesOf(member).names)
    }
    if (jar == null) {
      continue
    }
    memberPaths.putIfAbsent(name, jar.relativeOutputFile)
    // A member the layout puts in a jar it names itself is packed from its raw output into a jar of the plugin's, which
    // is no jar of the member's own, so it gets no offer. A member with no named jar takes the convention's jar, and
    // the offer is the same one a `<content>` member gets.
    if (name in residue.memberJars) {
      continue
    }
    seen.add(name)
    jar.offer?.let(offers::add)
  }
  return DerivedPluginCandidacy(
    offers = offers,
    vetoes = vetoes,
    memberPaths = memberPaths,
    memberLibraries = memberLibraries,
  )
}

/**
 * Whether the layout packs the `<content>` member [member] into a flat jar of another name.
 *
 * Such a member has no jar of its own in this plugin, so this plugin offers none. `intellij.gradle.plugin` is the
 * case: its layout puts the embedded member `intellij.gradle.toolingExtension` into `gradle-tooling-extension-api.jar`.
 * A jar under a subdirectory is a second jar beside the member's own and takes nothing away from it. Another plugin
 * that packs the member's own jar still offers it, the way a pure layout member with a named jar is handled.
 */
private fun isPackedIntoRenamedJar(member: String, residue: PluginContentResidue): Boolean {
  return residue.memberJars.get(member)?.any { !it.contains('/') && it != "$member.jar" } == true
}

/**
 * Where a plugin whose main jar is [mainJarName] puts [moduleName]'s jar, relative to its own `lib/`.
 *
 * The convention alone. The derivation owns this copy of the convention, so that the packaging gate compares two
 * producers. Two inputs of the build's rule are `PluginLayout` state and reach this through the caller: a jar
 * `PluginLayout.withModule(name, jarName)` names wins over this answer in [composeDerivedPluginJars], and the layout's
 * excluded module libraries decide [mergesLibraries] through [mergedLibrariesOf].
 *
 * An answer for every member, and never `null`. A member the convention gives no jar of its own is co-packed into
 * [mainJarName]. [composeDerivedPluginJars] reads that answer the same way.
 */
@ApiStatus.Internal
fun deriveMemberJarPath(
  moduleName: String,
  loadingRule: String?,
  hasPackageAttribute: Boolean,
  mergesLibraries: Boolean,
  mainJarName: String,
  /**
   * Whether the member is compatible with the frontend while the plugin's main module is not.
   *
   * Such a member gets a jar of its own, or the plugin's `<main>-frontend.jar` where the convention co-packs it.
   */
  frontendSplit: Boolean = false,
): String {
  // The main jar, renamed for a frontend member of a plugin whose main module is not one.
  val defaultJarName = if (frontendSplit) mainJarName.removeSuffix(".jar") + "-frontend.jar" else mainJarName
  if (loadingRule == EMBEDDED_LOADING_RULE) {
    // The marker sends the member into the plugin's main jar. Every other embedded member gets `lib/<module>.jar`,
    // whatever libraries that jar merges.
    return "$moduleName.jar"
  }
  // The marker wins outright. Then a descriptor with no `package` attribute cannot be loaded from the plugin jar, and
  // a module declaring a module library is put in its own jar so that the library travels with it. A frontend member
  // of a plugin that is not frontend-compatible itself gets its own jar too.
  if (!hasPackageAttribute || mergesLibraries || frontendSplit) {
    return "modules/$moduleName.jar"
  }
  return defaultJarName
}

/** One member's jar: where the plugin puts it, and the offer a packing target may serve, where there is one. */
@ApiStatus.Internal
class DerivedMemberJar(
  @JvmField val relativeOutputFile: String,
  @JvmField val offer: DerivedCandidacyOffer?,
)

/**
 * The module libraries one member's jar merges: the set, and whether the residue states it.
 *
 * [names] is `null` for a member with a module library the derivation cannot name; see [distributionLibraryName].
 */
@ApiStatus.Internal
class MergedMemberLibraries(
  @JvmField val names: Set<String>?,
  /** See [DerivedCandidacyOffer.isStated]. */
  @JvmField val isStated: Boolean,
  /**
   * The member's libraries the platform ships as bare jars beside the member's own; see [isSeparateLibraryJar].
   *
   * Not in [names], because the member's jar does not merge them. A bare library jar is one no packing target writes,
   * so the member gets no offer.
   */
  @JvmField val takenOutByPlatform: Set<String> = emptySet(),
)

/**
 * What [member]'s jar merges: the member's own module libraries minus what the layout and the platform take out.
 *
 * Three layout decisions leave a library out of the jar: `doNotCopyModuleLibrariesAutomatically`,
 * `excludeModuleLibrary` and a `withModuleLibrary` of the same library. [PluginContentResidue] carries each of them
 * as a fact. The platform's own rule, [isSeparateLibraryJar], is the fourth, and a library that brings no file of its
 * own is the fifth; see [mergedModuleLibraries].
 */
@ApiStatus.Internal
fun mergedLibrariesOf(member: JpsModule, residue: PluginContentResidue): MergedMemberLibraries {
  val memberName = member.name
  if (memberName in residue.unmergedMembers) {
    return MergedMemberLibraries(names = emptySet(), isStated = true)
  }
  val names = productionModuleLibraryNames(member) ?: return MergedMemberLibraries(names = null, isStated = false)
  val separate = separateLibraryJarNames(member)
  val excluded = buildSet {
    addAll(residue.excludedModuleLibraries.get(memberName).orEmpty())
    addAll(residue.takenOutLibraries)
    addAll(separate)
  }
  val merged = if (excluded.isEmpty()) names else names - excluded
  return MergedMemberLibraries(names = merged, isStated = merged.size != names.size, takenOutByPlatform = separate)
}

/**
 * The distribution names of [module]'s production-scope module libraries whose file the platform packs as a bare jar.
 *
 * The same walk as [productionModuleLibraryNames], over the library files instead of the names. Only a library the
 * derivation can name is reported.
 */
private fun separateLibraryJarNames(module: JpsModule): Set<String> {
  var result: MutableSet<String>? = null
  for (library in mergedModuleLibraries(module)) {
    if (library.getPaths(JpsOrderRootType.COMPILED).any { isSeparateLibraryJar(it.fileName.toString()) }) {
      val name = distributionLibraryName(library) ?: continue
      (result ?: LinkedHashSet<String>().also { result = it }).add(name)
    }
  }
  return result ?: emptySet()
}

/**
 * Whether the platform packs a library file as a jar of its own instead of merging it into the module's jar.
 *
 * The derivation owns this copy of the rule, so that the packaging gate compares two producers. An agent is attached
 * by path at runtime, and an `-rt` or `maven-` jar is loaded by an external process, so each stays a standalone file.
 */
@ApiStatus.Internal
fun isSeparateLibraryJar(fileName: String): Boolean {
  return fileName.endsWith("-rt.jar") ||
         fileName.startsWith("byte-buddy-") ||
         (fileName.contains("-agent") && AGENT_LIBRARIES_MERGED.none { fileName.contains(it) }) ||
         (fileName.startsWith("maven-") && MAVEN_LIBRARIES_MERGED.none { fileName.contains(it) })
}

/** The agent libraries the platform merges all the same. */
private val AGENT_LIBRARIES_MERGED = listOf("code-agents", "code-prompt-agents")

/** The `maven-` libraries the platform merges all the same. */
private val MAVEN_LIBRARIES_MERGED = listOf("maven-artifact", "maven-central-configuration", "maven-plugin-xml-parser")

/**
 * [member]'s jar under [mainJarName], or `null` when no resource root holds the member's own descriptor.
 *
 * A member with no readable descriptor has no `packIntoPluginJar` and no `package` attribute to read, so the caller
 * vetoes it. [libraries] is a function and not a set, so that the descriptor decides whether the library walk runs.
 */
private fun readMemberJar(
  member: JpsModule,
  loadingRule: String?,
  mainModule: String,
  mainJarName: String,
  libraries: (JpsModule) -> MergedMemberLibraries,
  frontend: FrontendCompatibility,
): DerivedMemberJar? {
  val descriptor = memberDescriptor(member) ?: return null
  val merged = libraries(member)
  return deriveMemberJar(
    moduleName = member.name,
    loadingRule = loadingRule,
    hasPackageAttribute = descriptor.hasPackageAttribute,
    libraries = merged.names,
    isStated = merged.isStated,
    mainJarName = mainJarName,
    frontendSplit = frontend.isSplit(mainModule = mainModule, member = member.name),
  )
}

/**
 * [moduleName]'s jar under [mainJarName], and the offer one packing target may serve, where there is one.
 *
 * The path is [deriveMemberJarPath]. The offer is that path narrowed to a jar of the member's own:
 * `lib/modules/<module>.jar`, or `lib/<module>.jar` for a jar that merges no module library.
 *
 * [libraries] is `null` for a member whose module library has no single jar; see [distributionLibraryName]. Such a
 * member gets a path and no offer. The path rule still reads `true` for the merge, because an unnamed library is a
 * module library all the same.
 */
@ApiStatus.Internal
fun deriveMemberJar(
  moduleName: String,
  loadingRule: String?,
  hasPackageAttribute: Boolean,
  libraries: Set<String>?,
  isStated: Boolean,
  mainJarName: String,
  frontendSplit: Boolean = false,
): DerivedMemberJar {
  val relativeOutputFile = deriveMemberJarPath(
    moduleName = moduleName,
    loadingRule = loadingRule,
    hasPackageAttribute = hasPackageAttribute,
    mergesLibraries = libraries == null || libraries.isNotEmpty(),
    mainJarName = mainJarName,
    frontendSplit = frontendSplit,
  )
  if (libraries == null) {
    return DerivedMemberJar(relativeOutputFile = relativeOutputFile, offer = null)
  }
  val servesOneMember = when (relativeOutputFile) {
    "modules/$moduleName.jar" -> true
    "$moduleName.jar" -> libraries.isEmpty()
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
@ApiStatus.Internal
const val EMBEDDED_LOADING_RULE: String = "embedded"

/** The two facts the jar path reads out of a content module's own descriptor. */
@ApiStatus.Internal
class MemberDescriptorFacts(
  @JvmField val hasPackageAttribute: Boolean,
)

/**
 * [module]'s own `<module>.xml`, read for the two facts the jar path depends on, or `null` when no resource root holds it.
 */
@ApiStatus.Internal
fun memberDescriptor(module: JpsModule): MemberDescriptorFacts? {
  val file = descriptorFiles(module = module, loadPath = module.name + ".xml").firstOrNull() ?: return null
  val text = file.readText()
  return MemberDescriptorFacts(
    hasPackageAttribute = JDOMUtil.load(text).getAttributeValue("package") != null,
  )
}

/**
 * The distribution names of [module]'s production-scope module libraries, or `null` when one of them has no name.
 *
 * A project library is out: a plugin merges one only through a `withProjectLibrary(name, jarName)` layout call.
 * `null` for an unnamed library with no single jar, for the reason [distributionLibraryName] gives.
 */
@ApiStatus.Internal
fun productionModuleLibraryNames(module: JpsModule): Set<String>? {
  val result = LinkedHashSet<String>()
  for (library in mergedModuleLibraries(module)) {
    result.add(distributionLibraryName(library) ?: return null)
  }
  return result
}

/**
 * The production-scope module libraries of [module] that bring a file of their own into the module's jar.
 *
 * The build copies a file once per target jar, so a library whose every file an earlier library of the same module
 * brings merges nothing, and the jar records it under no name. `intellij.ml.llm.libraries.grazie.cloud` is the case:
 * the one file of `ai.grazie.api.gateway.jvm` is a file of `ai.grazie.api.gateway.client.jvm` too. The derivation
 * owns this copy of the rule, so that the packaging gate compares two producers.
 */
private fun mergedModuleLibraries(module: JpsModule): Sequence<JpsLibrary> = sequence {
  val seen = HashSet<Path>()
  for (library in productionModuleLibraries(module)) {
    var contributes = false
    for (file in library.getPaths(JpsOrderRootType.COMPILED)) {
      if (seen.add(file)) {
        contributes = true
      }
    }
    if (contributes) {
      yield(library)
    }
  }
}

/** The module libraries [module] declares with the `COMPILE` or the `RUNTIME` scope, in dependency order. */
private fun productionModuleLibraries(module: JpsModule): Sequence<JpsLibrary> = sequence {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  for (element in module.dependenciesList.dependencies) {
    if (element !is JpsLibraryDependency) {
      continue
    }
    val scope = javaExtensionService.getDependencyExtension(element)?.scope ?: continue
    if (scope != JpsJavaDependencyScope.COMPILE && scope != JpsJavaDependencyScope.RUNTIME) {
      continue
    }
    val parentReference = element.libraryReference.parentReference
    if (parentReference.resolve() is JpsGlobal || parentReference !is JpsModuleReference) {
      continue
    }
    yield(element.library ?: continue)
  }
}

/**
 * The name a distribution records a library under: the library's own name, or the file name of its single jar for an
 * unnamed module library. `null` when an unnamed library has no single jar.
 *
 * `null` rather than a failure, because the candidacy asks this about every production-scope library of a member,
 * merged or not. A jar the derivation cannot name is a jar it refuses to offer.
 */
@ApiStatus.Internal
fun distributionLibraryName(library: JpsLibrary): String? {
  val name = library.name
  if (name.isNotEmpty() && !name.startsWith('#')) {
    return name
  }
  return library.getPaths(JpsOrderRootType.COMPILED).singleOrNull()?.fileName?.toString()
}

/**
 * The repo-global candidate set, folded over what the plugins' own models state.
 *
 * An AND over every plugin, with one tri-state per module: unseen, agreed on a library set, or vetoed. A vetoed module
 * never comes back. Every veto is applied first, so that a module one plugin co-packs is refused whatever order the
 * plugins are read in. A stated library set beats a derived one, and two stated sets that differ veto the module.
 */
@ApiStatus.Internal
fun foldDerivedPluginContentCandidacy(candidacies: Collection<DerivedPluginCandidacy>): Map<String, Set<String>> {
  val agreed = HashMap<String, Set<String>>()
  val stated = HashMap<String, Set<String>>()
  val vetoed = HashSet<String>()
  for (plugin in candidacies) {
    for (name in plugin.vetoes) {
      vetoed.add(name)
    }
  }
  for (plugin in candidacies) {
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
        // Unreachable while a derived library set is a function of the member alone. Kept because the fold's contract
        // is an AND over plugins, and a rule that later makes the set plugin-dependent must veto rather than pick one.
        reportCandidacyLibraryDisagreement(offer.moduleName, recorded, offer.libraries)
        agreed.remove(offer.moduleName)
        vetoed.add(offer.moduleName)
      }
    }
  }
  // After the merge, not before it. A derived disagreement vetoes a module without taking it out of `stated`, so only
  // a removal that follows the merge refuses every vetoed module.
  agreed.putAll(stated)
  agreed.keys.removeAll(vetoed)
  return agreed
}

private fun reportCandidacyLibraryDisagreement(moduleName: String, first: Set<String>, second: Set<String>) {
  println(
    "WARN: $moduleName keeps being packed by the layout evaluation: its plugins disagree about the libraries" +
    " merged into its jar (${first.sorted()} against ${second.sorted()})"
  )
}
