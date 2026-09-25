// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.ModuleItem

/**
 * Which independently cacheable slice of a dev distribution one assembly produces.
 *
 * A complete distribution is [COMPLETE] - one assembly, everything in it. Anything else is a fragment. No
 * distribution composes a fragment now: the fragments are the references of the `jars`, `replay` and `runtime-repo`
 * gates, which pack the same files as a second producer. The plugin directories come from the packed plugin
 * components, and `bin` and the product metadata come from the `platform_resources` component, so no fragment owns them.
 */
@ApiStatus.Internal
data class DevBuildFragment(
  /** Identifies the fragment in diagnostics; `platform_lib_reference`, `platform_runtime_module_repository_reference`. */
  @JvmField val name: String,
  /** The `lib/` jars this fragment owns, or `null` if it owns none. */
  @JvmField val platform: PlatformJarSelector?,
  /** Whether this fragment writes `modules/module-descriptors.{dat,jar}`. */
  @JvmField val runtimeModuleRepository: Boolean,
) {
  companion object {
    /** The whole distribution in one assembly: what an in-process dev launch and a non-split standalone build produce. */
    @JvmField
    val COMPLETE: DevBuildFragment = DevBuildFragment(
      name = "all",
      platform = PlatformJarSelector.ALL,
      runtimeModuleRepository = true,
    )
  }

  /**
   * Whether this fragment is the whole distribution. Only a complete assembly owns `bin`, the product metadata, the
   * launchers, the copied product files and the bundled plugin directories. It needs no component manifest, writes
   * `plugin-classpath.txt` itself, and is the only shape that may be scrambled.
   *
   * Defined by what it owns rather than by its name, so that naming a fragment `all` does not make it complete.
   */
  val isComplete: Boolean
    get() = platform?.isEverything == true

  /**
   * Whether this fragment packs `lib/` jars.
   *
   * Such a fragment inlines the content-module descriptors into the product descriptor. The reference of the `jars` gate
   * packs the handed-over jars, and the application-info module jar is one of them.
   */
  internal val ownsPlatformJars: Boolean
    get() = platform != null

  /** Whether this assembly owns the bundled plugin directories, which only a complete one does. */
  internal val ownsPlugins: Boolean
    get() = isComplete

  /** Whether this fragment owns the runtime module repository files under `modules/`. */
  internal val ownsRuntimeModuleRepository: Boolean
    get() = runtimeModuleRepository

  override fun toString(): String = name
}

/**
 * Which `lib/` jars a fragment owns: every jar, or a set of jar names.
 *
 * Ownership is decided per **jar**, not per module: [org.jetbrains.intellij.build.impl.PlatformLayout.withProductModuleOutputFile]
 * can rename a content module into another jar, so the jar as a whole belongs to one owner.
 */
@ApiStatus.Internal
data class PlatformJarSelector(
  /** The `lib/`-relative jar names this selector names - `ModuleItem.relativeOutputFile`. */
  @JvmField val jars: Set<String>,
  @JvmField val mode: Mode,
) {
  enum class Mode {
    /** Every `lib/` jar, with no [jars]. Only [ALL] uses it, for a complete assembly. */
    ALL,

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
    require(mode == Mode.ALL || jars.isNotEmpty()) {
      "A selector that owns only the jars it names must name at least one"
    }
    require(mode == Mode.ONLY || jars.isEmpty()) {
      "A selector that owns every jar names none, but got $jars"
    }
  }

  /** Whether this selector owns every `lib/` jar, which is what a complete distribution needs. */
  val isEverything: Boolean
    get() = mode == Mode.ALL

  /** Whether the `lib/` jar at [relativeOutputFile] belongs to this fragment. */
  fun accepts(relativeOutputFile: String): Boolean {
    return when (mode) {
      Mode.ALL -> true
      Mode.ONLY -> jars.contains(relativeOutputFile)
    }
  }

  companion object {
    /** Every `lib/` jar. */
    @JvmField
    val ALL: PlatformJarSelector = PlatformJarSelector(jars = emptySet(), mode = Mode.ALL)
  }
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
