// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus

/** A library the layout packs: its distribution name, and the module that owns it, or `null` for a project library. */
@ApiStatus.Internal
data class RecordedLibrary(@JvmField val name: String, @JvmField val ownerModule: String?)

/**
 * What the layout of one plugin decides about its content, in the shape the derivation reads.
 *
 * [NONE] means pure convention. [layoutResidueOf] states the fields the layout facts imply, the `auto` rule adds its
 * children and the co-pack rule its vetoes; see `derivePluginPacking`.
 */
@ApiStatus.Internal
class PluginContentResidue(
  /** The members the layout merges beside the plugin's own `<content>`. */
  @JvmField val extraMembers: Set<String> = emptySet(),
  /**
   * Members this plugin must not hand over, which takes the packing target away from every plugin.
   *
   * The veto is repo-global, because one packing target serves every plugin that ships the module. One plugin stating
   * it is enough. Two `PluginLayout` decisions reach it: a jar holding several content modules, and a bare library jar
   * taken out of the member's own jar by `withModuleLibrary`; see [layoutResidueOf] and `derivePluginPacking`.
   */
  @JvmField val vetoedMembers: Set<String> = emptySet(),
  /**
   * The jars this plugin packs a member into, by member, where the layout names the jar itself.
   *
   * `PluginLayout.withModule(name, jarName)` is the decision, and the jar name is a free string of the layout, so the
   * value is a path. The value is the whole jar set of the member, and not one jar: one plugin can pack one module into
   * several jars. A member with a row leaves the main-jar co-pack, and a row naming the plugin's main jar puts it back
   * there. Read by the jar composition alone; see [layoutResidueOf].
   */
  @JvmField val memberJars: Map<String, Set<String>> = emptyMap(),
  /**
   * Libraries the layout packs that no member's jar merges, by name and by the module that owns the library.
   *
   * The pair and never a Bazel label, because a label carries the artifact version of a library. An absent
   * [RecordedLibrary.ownerModule] is a project library. The layout facts state every row; see [layoutResidueOf].
   */
  @JvmField val libraries: Set<RecordedLibrary> = emptySet(),
  /** Members whose module libraries the layout keeps out of their jar; see [PluginLayoutFacts.unmergedMembers]. */
  @JvmField val unmergedMembers: Set<String> = emptySet(),
  /** Module libraries the layout takes out of a member's jar, by member; see [PluginLayoutFacts.excludedModuleLibraries]. */
  @JvmField val excludedModuleLibraries: Map<String, Set<String>> = emptyMap(),
  /**
   * Libraries the layout packs as jars of their own, which then leave every member jar that declares them.
   *
   * A `withModuleLibrary` library leaves the jar of every member that declares it; see [PluginLayoutFacts.moduleLibraries].
   */
  @JvmField val takenOutLibraries: Set<String> = emptySet(),
) {
  companion object {
    @JvmField val NONE: PluginContentResidue = PluginContentResidue()
  }
}

/**
 * The residue the layout facts of one plugin imply, in the shape the derivation reads.
 *
 * The derivation is the convention plus the layout's own decisions, and no distribution build has to state the
 * difference:
 *
 * - every layout member is a member of the plugin. A `<content>` member is one already, and the union costs nothing;
 * - a pure layout member keeps the layout's whole jar set as its `memberJars` row, the main jar included. The
 *   convention gives a member with a descriptor of its own a jar of its own, and the build packs a plain
 *   `withModule(name)` item into the main jar all the same, so the row is what keeps the member there. A `<content>`
 *   member ([closureMembers]) loses the main jar from its set, because the build skips a flat layout item of a member
 *   its `<content>` already packed, and it gets no row when nothing else is left;
 * - a member whose library the layout ships as a bare jar merges less than its dependency list says. No packing
 *   target may serve it, so the member is vetoed;
 * - the library decisions reach [mergedLibrariesOf] through the three fields it reads.
 *
 * [PluginContentResidue.NONE] for facts that name no member and no library.
 */
@ApiStatus.Internal
fun layoutResidueOf(mainModule: String, facts: PluginLayoutFacts, closureMembers: Set<String> = emptySet()): PluginContentResidue {
  if (facts.memberJars.isEmpty() &&
      facts.unmergedMembers.isEmpty() &&
      facts.excludedModuleLibraries.isEmpty() &&
      facts.projectLibraries.isEmpty() &&
      facts.moduleLibraries.isEmpty() &&
      facts.generatorLibraries.isEmpty()) {
    return PluginContentResidue.NONE
  }
  val libraries = LinkedHashSet<RecordedLibrary>()
  facts.projectLibraries.keys.mapTo(libraries) { RecordedLibrary(name = it, ownerModule = null) }
  facts.moduleLibraries.mapTo(libraries) { RecordedLibrary(name = it.libraryName, ownerModule = it.moduleName) }
  facts.generatorLibraries.mapTo(libraries) { RecordedLibrary(name = it, ownerModule = null) }
  val memberJars = LinkedHashMap<String, Set<String>>()
  for ((member, paths) in facts.memberJars) {
    if (member == mainModule) {
      continue
    }
    if (member in closureMembers) {
      val stated = paths - facts.mainJarName
      if (stated.isNotEmpty()) {
        memberJars.put(member, stated)
      }
    }
    else {
      memberJars.put(member, paths)
    }
  }
  return PluginContentResidue(
    extraMembers = facts.memberJars.keys - mainModule,
    memberJars = memberJars,
    vetoedMembers = facts.moduleLibraries.mapTo(LinkedHashSet()) { it.moduleName },
    libraries = libraries,
    unmergedMembers = facts.unmergedMembers,
    excludedModuleLibraries = facts.excludedModuleLibraries,
    takenOutLibraries = facts.moduleLibraries.mapTo(LinkedHashSet()) { it.libraryName },
  )
}

/**
 * The `<content>` members of a plugin that a jar the layout names holds, beside or instead of their own jar.
 *
 * Such a member is not handed over. A jar under a subdirectory is a second jar beside the member's own, and the plugin
 * packs it from a raw output it has to keep. A flat jar replaces the member's own jar, so there is no jar to hand
 * over. A pure layout member is not here, because the convention gives it no jar of its own.
 */
@ApiStatus.Internal
fun layoutJarMembers(residue: PluginContentResidue, closureMembers: Set<String>, mainJarName: String): Set<String> {
  return residue.memberJars.asSequence()
    .filter { (member, paths) -> member in closureMembers && paths.any { it != mainJarName } }
    .mapTo(LinkedHashSet()) { it.key }
}

/**
 * The union of two residues.
 *
 * [other] wins where one member states one value twice, because it is the narrower statement.
 */
@ApiStatus.Internal
operator fun PluginContentResidue.plus(other: PluginContentResidue): PluginContentResidue {
  fun mergeSets(first: Map<String, Set<String>>, second: Map<String, Set<String>>): Map<String, Set<String>> {
    val result = LinkedHashMap<String, Set<String>>(first)
    for ((key, value) in second) {
      result.merge(key, value) { a, b -> a + b }
    }
    return result
  }
  return PluginContentResidue(
    extraMembers = extraMembers + other.extraMembers,
    vetoedMembers = vetoedMembers + other.vetoedMembers,
    memberJars = mergeSets(memberJars, other.memberJars),
    libraries = libraries + other.libraries,
    unmergedMembers = unmergedMembers + other.unmergedMembers,
    excludedModuleLibraries = mergeSets(excludedModuleLibraries, other.excludedModuleLibraries),
    takenOutLibraries = takenOutLibraries + other.takenOutLibraries,
  )
}
