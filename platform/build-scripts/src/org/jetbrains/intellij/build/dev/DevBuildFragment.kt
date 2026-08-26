// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.ModuleItem

/** Stable key of one plugin content-module jar handed from JarPackager to a Bazel packing action. */
@ApiStatus.Internal
data class PrepackedPluginContentKey(
  @JvmField val pluginMainModule: String,
  @JvmField val contentModule: String,
)

/** The product-independent jar relation. Placement is validated against the product layout before it is used. */
@ApiStatus.Internal
data class PrepackedPluginContentJar(
  @JvmField val pluginMainModule: String,
  @JvmField val contentModule: String,
  /** Path below the plugin's `lib/` directory. */
  @JvmField val relativeOutputFile: String,
) {
  val key: PrepackedPluginContentKey
    get() = PrepackedPluginContentKey(pluginMainModule = pluginMainModule, contentModule = contentModule)
}

/**
 * One handed-off jar as a single assembly saw it: the relation, and where in that assembly the jar belongs.
 *
 * A type of its own, because [assetOrdinal] is not part of the relation. The relation is product-independent and comes
 * from Bazel; the ordinal is a fact about one `JarPackager` run and cannot be read from a checked-in file.
 */
@ApiStatus.Internal
data class AssembledPrepackedPluginContentJar(
  @JvmField val jar: PrepackedPluginContentJar,
  /**
   * How many assets the assembly had created when it handed this jar over, which is the index the jar's own asset
   * would have had.
   *
   * The plugin classpath needs it. `generatePluginClassPath` orders a plugin's jars with
   * `putMoreLikelyPluginJarsFirst`, a stable sort whose last tiebreak is the file name *length* - so two jars with
   * equally long names keep the order they were added in. Appending the handed-off jars after the assembled ones would
   * therefore let the *producer* of a jar decide the classpath order, and handing a jar over would reorder
   * `plugin-classpath.txt` without changing a byte of any jar. With the ordinal the merge puts each handed-off jar back
   * where `computeSourcesForModule` would have put its asset, so the sort sees the same input either way.
   */
  @JvmField val assetOrdinal: Int,
)

/**
 * Which independently cacheable slice of a dev distribution one assembly produces.
 *
 * A complete distribution is [COMPLETE] - one assembly, everything in it. Anything else is a fragment: a caller
 * assembles several of them, each cached and invalidated on its own, and composes the results with
 * `composeDevBuildComponents`. The producers of one distribution must partition it exactly, so what nobody claimed is
 * assembled by a complement instead of being silently dropped: [PlatformJarSelector.Mode.EXCLUDE] takes every
 * `lib/` jar the per-module packing actions do not pack, and [PluginFragmentSelector.Remaining] every bundled plugin
 * the named plugin fragments did not claim.
 */
@ApiStatus.Internal
data class DevBuildFragment(
  /** Identifies the fragment in its component manifest and in diagnostics; `platform_lib`, `platform_resources`, `plugins_air`. */
  @JvmField val name: String,
  /** The `lib/` jars this fragment owns, or `null` if it owns none. */
  @JvmField val platform: PlatformJarSelector?,
  /** Whether this fragment owns `bin`, the product metadata, the launchers and the copied product files. */
  @JvmField val platformResources: Boolean,
  /** The bundled plugin directories this fragment owns, or `null` if it owns none. */
  @JvmField val plugins: PluginFragmentSelector?,
) {
  companion object {
    /** The whole distribution in one assembly: what an in-process dev launch and a non-split standalone build produce. */
    @JvmField
    val COMPLETE: DevBuildFragment = DevBuildFragment(
      name = "all",
      platform = PlatformJarSelector.ALL,
      platformResources = true,
      plugins = PluginFragmentSelector.All,
    )
  }

  /**
   * Whether this fragment is the whole distribution: it needs no component manifest, writes `plugin-classpath.txt`
   * itself, and is the only shape that may be scrambled.
   *
   * Defined by what it owns rather than by its name, so that naming a fragment `all` does not make it complete and a
   * fragment that genuinely owns everything is not treated as a piece of something larger.
   */
  val isComplete: Boolean
    get() = platform?.isEverything == true && platformResources && plugins == PluginFragmentSelector.All

  internal val ownsPlatformJars: Boolean
    get() = platform != null

  internal val ownsPlugins: Boolean
    get() = plugins != null

  /**
   * Whether this fragment packs the jars that the inlined product descriptor ends up in.
   *
   * A fragment that owns `lib/` by exclusion holds the application-info module - that module is not a content module,
   * so no other producer packs its jar - and needs the descriptors inlined into it. A fragment that owns only the jars
   * another producer packs holds none of them and does not resolve them, see
   * [org.jetbrains.intellij.build.BuildOptions.embedProductContentModuleDescriptors]. `layoutPlatform` re-checks this
   * conclusion against the layout it actually got, so a product that puts its application-info module in a jar this
   * fragment does not own fails instead of shipping a descriptor with nothing inlined into it.
   */
  internal val ownsProductDescriptorJars: Boolean
    get() = platform?.mode == PlatformJarSelector.Mode.EXCLUDE

  override fun toString(): String = name
}

/**
 * Which `lib/` jars a fragment owns: a set of jar names, and how to read it.
 *
 * Ownership is decided per **jar**, not per module: [org.jetbrains.intellij.build.impl.PlatformLayout.withProductModuleOutputFile]
 * can rename a content module into another jar, so the jar as a whole belongs to one owner. Packing also creates `lib/`
 * jars the layout never named - for a library that has to stay in its own jar
 * (`org.jetbrains.intellij.build.impl.isSeparateLibraryJar`) or for a project library - and those hold no module at all.
 * Both facts are why the selector is a name set with a default rather than a classification of what a jar contains: a
 * jar nobody named is simply not excluded, so it has an owner without anyone having to decide what it holds.
 */
@ApiStatus.Internal
data class PlatformJarSelector(
  /** The `lib/`-relative jar names this selector names - `ModuleItem.relativeOutputFile`. */
  @JvmField val jars: Set<String>,
  @JvmField val mode: Mode,
) {
  enum class Mode {
    /**
     * Every `lib/` jar except [jars].
     *
     * [jars] are jar names another producer packs and the distribution composes in as a component of its own:
     * `jvm_library` packs a content module's jar from the jars it merges alone, declaring no project model, so those
     * jars survive a model edit that re-keys every fragment. A fragment must not pack them too - the composer fails on
     * a path two components both provide - and must not resolve their modules either, since a declared module output is
     * what makes a source edit re-run this action.
     *
     * The layout still knows those jars exist, which is what keeps the core classpath complete: see
     * `contentModuleJarCoreClasspathEntries`.
     */
    EXCLUDE,

    /**
     * Only [jars], and nothing else.
     *
     * What the reference target of `./build/dev-dist.cmd jars` assembles: the same jars the other producer
     * packs, packed the way `JarPackager` packs them, so the two can be compared byte for byte. It is composed into no
     * distribution.
     */
    ONLY,
  }

  init {
    require(mode == Mode.EXCLUDE || jars.isNotEmpty()) {
      "A selector that owns only the jars it names must name at least one"
    }
  }

  /** Whether this selector owns every `lib/` jar, which is what a complete distribution needs. */
  val isEverything: Boolean
    get() = mode == Mode.EXCLUDE && jars.isEmpty()

  /** Whether the `lib/` jar at [relativeOutputFile] belongs to this fragment. */
  fun accepts(relativeOutputFile: String): Boolean {
    return when (mode) {
      Mode.EXCLUDE -> !jars.contains(relativeOutputFile)
      Mode.ONLY -> jars.contains(relativeOutputFile)
    }
  }

  companion object {
    /** Every `lib/` jar. */
    @JvmField
    val ALL: PlatformJarSelector = PlatformJarSelector(jars = emptySet(), mode = Mode.EXCLUDE)
  }
}

/** Which bundled plugin directories a fragment owns. */
@ApiStatus.Internal
sealed interface PluginFragmentSelector {
  /** Every bundled plugin, and the prebuilt plugin directories a product copies in. */
  data object All : PluginFragmentSelector

  /** The plugins with these main modules. */
  data class Named(@JvmField val mainModules: Set<String>) : PluginFragmentSelector

  /**
   * Every bundled plugin that [claimedMainModules] does not cover.
   *
   * This fragment also owns the prebuilt plugin directories from `ProductProperties.getAdditionalPluginPaths`, which
   * are not plugin layouts and so cannot be claimed by name.
   */
  data class Remaining(@JvmField val claimedMainModules: Set<String>) : PluginFragmentSelector
}

/**
 * The modules of [includedModules] whose jars this fragment owns.
 *
 * Filtering the layout, and not only the jars it produced, is what makes the split pay: a module the fragment does not
 * pack is never resolved to its Bazel output, so it does not end up in the action's used-input set and cannot invalidate
 * this fragment. The jar stays the unit - filtering module by module would split one jar between two fragments, and the
 * composer would then find both of them providing it.
 */
internal fun PlatformJarSelector.selectModules(includedModules: Collection<ModuleItem>): Collection<ModuleItem> {
  if (isEverything) {
    return includedModules
  }
  return includedModules.filter { accepts(it.relativeOutputFile) }
}

/** Whether the plugin with [mainModule] belongs to this fragment. */
internal fun PluginFragmentSelector.accepts(mainModule: String): Boolean {
  return when (this) {
    PluginFragmentSelector.All -> true
    is PluginFragmentSelector.Named -> mainModules.contains(mainModule)
    is PluginFragmentSelector.Remaining -> !claimedMainModules.contains(mainModule)
  }
}

/**
 * Fails when this selector names a plugin the product does not bundle. A misspelled name is otherwise invisible: its
 * fragment assembles nothing, the plugin it was meant to name returns to [PluginFragmentSelector.Remaining], and the
 * distribution is still complete - only the caching intent is quietly lost. [candidateMainModules] is the product's
 * whole bundled set, which every plugin fragment computes.
 */
internal fun PluginFragmentSelector.checkNamesAreKnown(candidateMainModules: Set<String>, fragmentName: String) {
  val named = when (this) {
    is PluginFragmentSelector.Named -> mainModules
    is PluginFragmentSelector.Remaining -> claimedMainModules
    else -> return
  }
  val unknown = named.filterNot(candidateMainModules::contains)
  check(unknown.isEmpty()) {
    "Fragment '$fragmentName' names plugins this product does not bundle: ${unknown.sorted().joinToString()}"
  }
}

/** Whether this fragment owns the prebuilt plugin directories a product copies in verbatim. */
internal val PluginFragmentSelector.ownsPrebuiltPluginDirs: Boolean
  get() = this is PluginFragmentSelector.All || this is PluginFragmentSelector.Remaining
