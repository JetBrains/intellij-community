// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.ModuleItem

/**
 * Which independently cacheable slice of a dev distribution one assembly produces.
 *
 * A complete distribution is [COMPLETE] - one assembly, everything in it. Anything else is a fragment: a caller
 * assembles several of them, each cached and invalidated on its own, and composes the results with
 * `composeDevBuildComponents`. The producers of one distribution must partition it exactly. A complement assembles
 * what nobody claimed, so nothing is silently dropped: [PlatformJarSelector.Mode.EXCLUDE] takes every `lib/` jar the
 * per-module packing actions do not pack. The plugin directories come from the packed plugin components, so no
 * fragment owns one.
 */
@ApiStatus.Internal
data class DevBuildFragment(
  /** Identifies the fragment in its component manifest and in diagnostics; `platform_lib`, `platform_resources`. */
  @JvmField val name: String,
  /** The `lib/` jars this fragment owns, or `null` if it owns none. */
  @JvmField val platform: PlatformJarSelector?,
  /** Whether this fragment owns `bin`, the product metadata, the launchers and the copied product files. */
  @JvmField val platformResources: Boolean,
  /** The bundled plugin directories this assembly owns: [PluginFragmentSelector.All] for a complete one, `null` for a fragment. */
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

/** Which bundled plugin directories an assembly owns. */
@ApiStatus.Internal
sealed interface PluginFragmentSelector {
  /** Every bundled plugin, and the prebuilt plugin directories a product copies in. */
  data object All : PluginFragmentSelector
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
