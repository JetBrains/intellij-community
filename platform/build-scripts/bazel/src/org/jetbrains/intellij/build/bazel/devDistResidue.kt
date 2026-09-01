// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * One plugin's dev-distribution content residue: what the derivation cannot state about the plugin's members.
 *
 * ADR 0007 rule 1 applied to the content leaf of `dev_dist_plugin`. The converter derives a plugin's members, the jar of
 * each member and the libraries the members declare from the project model. This file is the remainder, and a plugin
 * with no file at all is pure convention.
 *
 * One part and one producer. [content] is one section for the plugin, since a plugin's membership does not depend on the
 * layout variant. The descriptor deviations are keyed by (plugin, layout variant), so they are in the
 * `plugin_descriptor_residue` section of `dev_dist_plugin_model_tables.txt` instead; see [descriptorResidueOf].
 */
@Serializable
internal data class DevDistResidueFile(
  @JvmField val content: ContentResidueSection? = null,
)

/**
 * The content half of a plugin's residue: seven fields, every one optional.
 *
 * The eighth, the merged members, is not here. It is the one field the monorepo reads, so it sits in
 * `dev_dist_plugin_extra_members.txt` where a reader needs no Starlark and no YAML; see [readPluginExtraMembers].
 * [toResidue] takes it as a parameter and joins the two halves into the one shape the derivations read.
 *
 * Every field is a class the Phase-0 two-producer comparison found, and no field is speculative. Each states one
 * `PluginLayout` decision, and evaluating a product layout is the work this generator exists to keep out of a fragment
 * action.
 */
@Serializable
internal data class ContentResidueSection(
  /**
   * Members whose jar this plugin puts at `lib/<module>.jar` although the derivation says `lib/modules/<module>.jar`.
   *
   * A name list and never a path map. `computeEmbeddedOutputJarPath` composes the path from the module name alone, so a
   * path would be one copy of a derivable rule per row - the mistake `PluginContent.prepackedContentModuleLabels`
   * already records having made once, at 2 030 relations.
   */
  @JvmField @SerialName("lib_root_jars") val libRootJars: List<String> = emptyList(),
  /**
   * Members this plugin does not hand over, although the module has a packing target for the repository.
   *
   * A second jar of this plugin holds the module, so handing the first one off would leave the second to be packed from
   * a raw output the fragment no longer declares. `coPackedElsewhere` is the report-side reader of the same fact.
   */
  @JvmField @SerialName("raw_members") val rawMembers: List<String> = emptyList(),
  /**
   * Members this plugin must not hand over, which takes the packing target away from every plugin.
   *
   * The veto is repo-global, because one packing target serves every plugin that ships the module. One plugin stating
   * the row is therefore enough, and the row is written beside the plugin whose derivation would otherwise make the
   * wrong offer - which is where a reader looks for the reason.
   *
   * Three `PluginLayout` decisions reach it: a jar holding several content modules, a bare library jar taken out of the
   * member's own jar, and a project library merged into the member's jar.
   */
  @JvmField @SerialName("vetoed_members") val vetoedMembers: List<String> = emptyList(),
  /**
   * Members that get a jar of their own although the derivation puts them in the plugin's main jar.
   *
   * `isPluginModulePackedIntoSeparateJar` reads a frontend module filter, which is per product, and
   * `getModulesWithExcludedModuleLibraries`, which is `PluginLayout` state. Where one of those two decides, the answer
   * is stated here.
   */
  @JvmField @SerialName("separate_jars") val separateJars: List<String> = emptyList(),
  /**
   * The jars this plugin packs a member into, by member, where the layout names the jar itself.
   *
   * `PluginLayout.withModule(name, jarName)` is the decision, and nothing derives it: the jar name is a free string of
   * the layout. So the value is a path, unlike [libRootJars], and the two do not overlap - a `lib_root_jars` row still
   * takes the derived `lib/<module>.jar`.
   *
   * The whole jar set of the member, and not one jar. `BaseLayout.checkNotExists` lets one plugin pack one module into
   * several jars, and `intellij.spring.customNs` sits both in the main jar and in `customNs/customNs.jar`. A member with
   * a row leaves the main-jar co-pack, and a row naming the plugin's main jar puts it back there.
   *
   * The jar set is the union over every product report the writer reads, so a member that two products place
   * differently states both jars.
   */
  @JvmField @SerialName("member_jars") val memberJars: Map<String, List<String>> = emptyMap(),
  /**
   * The module libraries a member's jar really merges, by member, where the layout excluded some of them.
   *
   * `PluginLayout.doNotCopyModuleLibrariesAutomatically` and `excludedModuleLibraries` are what take a library out. The
   * value is the whole set and not the difference, so one row states the jar rather than a patch of it, and an empty
   * list is a jar that merges nothing.
   */
  @JvmField @SerialName("merged_libraries") val mergedLibraries: Map<String, List<String>> = emptyMap(),
  /**
   * Libraries the layout packs that no member declares, by the module that owns the library and its name.
   *
   * The pair and never a Bazel label, for the reason `computeLibraryContainerLabels` gives: a per-jar label carries the
   * artifact version, so a Maven bump would rewrite every file naming the library. An absent [ResidueLibraryRow.module]
   * is a project library.
   */
  @JvmField val libraries: List<ResidueLibraryRow> = emptyList(),
)

/** One library row of a residue: a project library states no [module], a module library states its owner. */
@Serializable
internal data class ResidueLibraryRow(
  @JvmField val module: String? = null,
  @JvmField val name: String = "",
)

/** A section that states nothing, so that an absent residue and an all-default one are one verdict. */
internal val EMPTY_CONTENT_RESIDUE: ContentResidueSection = ContentResidueSection()

internal const val DEV_DIST_RESIDUE_FILE_NAME: String = "dev-dist.yaml"

/**
 * Parses [ModuleDescriptor.devDistResidueFile]; reached only through [ModuleDescriptor.devDistResidue].
 *
 * `null` for an absent or empty file, which is the same verdict as a file stating every default: pure convention.
 */
internal fun parseDevDistResidue(file: Path?): DevDistResidueFile? {
  if (file == null) {
    return null
  }
  val text = file.readText()
  if (text.isBlank()) {
    return null
  }
  return recipeYaml.decodeFromString(DevDistResidueFile.serializer(), text)
}

/**
 * [ModuleDescriptor.devDistResidueFile] as a path inside the module's own Bazel package, so a label can name it.
 *
 * `null` when the module has no residue, or when the residue is outside the package - `../` is not a label. The twin of
 * [contentModuleRecipePackagePath].
 */
internal fun devDistResiduePackagePath(module: ModuleDescriptor): String? {
  val file = module.devDistResidueFile ?: return null
  return file.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString.takeIf { !it.startsWith("../") }
}

/**
 * [module]'s residue, in the shape the two derivations take, or [PluginContentResidue.NONE].
 *
 * Joins the plugin's own seven fields with its merged members, which are central; see [readPluginExtraMembers]. A
 * plugin can have either half alone: one that only merges a member has no section, and one that only renames a jar has
 * no row in the table.
 */
internal fun contentResidueOf(module: ModuleDescriptor, context: BazelBuildFileGenerator): PluginContentResidue {
  val extraMembers = context.pluginExtraMembers.get(module.module.name).orEmpty().toSet()
  val section = module.devDistResidue?.content
  if (section == null) {
    return if (extraMembers.isEmpty()) PluginContentResidue.NONE else PluginContentResidue(extraMembers = extraMembers)
  }
  return section.toResidue(extraMembers)
}

/**
 * This section in the shape the two derivations take.
 *
 * The one place the seven fields cross from the file's shape into the derivation's, with the merged members joined in
 * from the central table. The residue writer composes a section rather than reading one, and it takes the same route,
 * so a new field reaches both readers together.
 */
internal fun ContentResidueSection.toResidue(extraMembers: Set<String> = emptySet()): PluginContentResidue {
  return PluginContentResidue(
    extraMembers = extraMembers,
    libRootJars = libRootJars.toSet(),
    rawMembers = rawMembers.toSet(),
    vetoedMembers = vetoedMembers.toSet(),
    separateJars = separateJars.toSet(),
    memberJars = memberJars.mapValues { it.value.toSet() },
    mergedLibraries = mergedLibraries.mapValues { it.value.toSet() },
    libraries = libraries.mapTo(LinkedHashSet()) { RecordedLibrary(name = it.name, ownerModule = it.module) },
  )
}
