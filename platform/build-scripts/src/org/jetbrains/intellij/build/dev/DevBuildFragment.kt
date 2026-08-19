// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.ModuleItem

/**
 * Which independently cacheable slice of a dev distribution one assembly produces.
 *
 * A complete distribution is [COMPLETE] - one assembly, everything in it. Anything else is a fragment: a caller
 * assembles several of them, each cached and invalidated on its own, and composes the results with
 * `composeDevBuildComponents`. The fragments of one distribution must partition it exactly, so every selector here
 * is defined as a predicate over what the layout produced rather than as a list of files someone maintains, and what
 * nobody claimed is assembled by a complement fragment instead of being silently dropped:
 * [PlatformFragmentSelector.ContentModules] takes every `lib/` jar [PlatformFragmentSelector.Core] does not, and
 * [PluginFragmentSelector.Remaining] every bundled plugin the named plugin fragments did not claim.
 */
@ApiStatus.Internal
data class DevBuildFragment(
  /** Identifies the fragment in its component manifest and in diagnostics; `platform_core`, `platform_content_modules`, `plugins_air`. */
  @JvmField val name: String,
  /** The `lib/` jars this fragment owns, or `null` if it owns none. */
  @JvmField val platform: PlatformFragmentSelector?,
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
      platform = PlatformFragmentSelector.All,
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
    get() = platform == PlatformFragmentSelector.All && platformResources && plugins == PluginFragmentSelector.All

  internal val ownsPlatformJars: Boolean
    get() = platform != null

  internal val ownsPlugins: Boolean
    get() = plugins != null

  /**
   * Whether this fragment packs the jars that the inlined product descriptor ends up in.
   *
   * Those are the flat core jars: the application-info module is not a content module, so the jar holding it holds
   * none, and falls to [PlatformFragmentSelector.Core]. A fragment that owns only content-module jars has no use for
   * the inlined descriptors and does not resolve them - see
   * [org.jetbrains.intellij.build.BuildOptions.embedProductContentModuleDescriptors]. `layoutPlatform` re-checks this
   * conclusion against the layout it actually got, so a product that puts its application-info module elsewhere fails
   * instead of shipping a descriptor with nothing inlined into it.
   */
  internal val ownsProductDescriptorJars: Boolean
    get() = platform == PlatformFragmentSelector.All || platform == PlatformFragmentSelector.Core

  override fun toString(): String = name
}

/**
 * Which `lib/` jars a fragment owns.
 *
 * The split follows the plugin model: `processAndGetProductPluginContentModules` gives every content module of the
 * product's core plugin a jar of its own, so those jars are separable, while everything else - the flat core jars that
 * merge many modules, and project libraries not yet converted to content modules - has to stay together. The
 * content-module jar is therefore the finest unit the layout offers, and [Core] and [ContentModules] are its two sides.
 */
@ApiStatus.Internal
sealed interface PlatformFragmentSelector {
  /** Every `lib/` jar. */
  data object All : PlatformFragmentSelector

  /** The jars that hold no content module: the flat platform core, and the project libraries packed beside it. */
  data object Core : PlatformFragmentSelector

  /** Every jar that holds a content module - the complement of [Core]. */
  data object ContentModules : PlatformFragmentSelector
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
 * Who owns each `lib/` jar, decided once from the whole platform layout.
 *
 * Both halves of the split read this one answer: the layout filter, which decides what a fragment resolves and packs,
 * and the asset filter, which decides what it writes. Deciding twice - once over the layout, once over the jars packing
 * produced - is how a jar could pass one and fail the other and end up in no fragment at all: packing creates `lib/`
 * jars the layout never named, for a library that has to stay in its own jar
 * (`org.jetbrains.intellij.build.impl.isSeparateLibraryJar`) or for a project library, and those hold no module for the
 * asset filter to recognize. A jar this does not know is [JarOwnership.PlatformCore], which is what `Core` means: the
 * complement of what the content-module fragments claimed.
 */
internal class PlatformJarOwnership private constructor(private val byJar: Map<String, JarOwnership>) {
  fun of(relativeOutputFile: String): JarOwnership = byJar[relativeOutputFile] ?: JarOwnership.PlatformCore

  companion object {
    fun of(includedModules: Collection<ModuleItem>): PlatformJarOwnership {
      val byJar = HashMap<String, JarOwnership>()
      for ((relativeOutputFile, modules) in includedModules.groupBy(ModuleItem::relativeOutputFile)) {
        byJar[relativeOutputFile] = jarOwnership(modules)
      }
      return PlatformJarOwnership(byJar)
    }
  }
}

/**
 * What owns one `lib/` jar: [JarOwnership.ContentModule] when it holds a content module, [JarOwnership.PlatformCore]
 * when it holds none.
 *
 * A jar is the unit, not a module: [org.jetbrains.intellij.build.impl.PlatformLayout.withProductModuleOutputFile] lets a
 * content module be renamed into another jar, so ownership has to be decided for the jar as a whole. One content module
 * is enough - a jar holding several of them, as `includeDependencies` on an embedded module produces, is still one jar
 * and still belongs to the content-module fragment.
 */
private fun jarOwnership(includedModules: Collection<ModuleItem>): JarOwnership {
  return if (includedModules.any(ModuleItem::isProductModule)) JarOwnership.ContentModule else JarOwnership.PlatformCore
}

/**
 * The modules of [includedModules] whose jars this fragment owns, according to [ownership].
 *
 * Filtering the layout, and not only the jars it produced, is what makes the split pay: a module the fragment does not
 * pack is never resolved to its Bazel output, so it does not end up in the action's used-input set and cannot invalidate
 * this fragment. The jar stays the unit - filtering module by module would split one jar between two fragments, and the
 * composer would then find both of them providing it.
 */
internal fun PlatformFragmentSelector.selectModules(
  includedModules: Collection<ModuleItem>,
  ownership: PlatformJarOwnership,
): Collection<ModuleItem> {
  if (this == PlatformFragmentSelector.All) {
    return includedModules
  }
  return includedModules.filter { accepts(ownership, it.relativeOutputFile) }
}

internal sealed interface JarOwnership {
  data object PlatformCore : JarOwnership

  data object ContentModule : JarOwnership
}

/** Whether the `lib/` jar at [relativeOutputFile] belongs to this fragment. */
internal fun PlatformFragmentSelector.accepts(ownership: PlatformJarOwnership, relativeOutputFile: String): Boolean {
  return when (this) {
    PlatformFragmentSelector.All -> true
    PlatformFragmentSelector.Core -> ownership.of(relativeOutputFile) == JarOwnership.PlatformCore
    PlatformFragmentSelector.ContentModules -> ownership.of(relativeOutputFile) != JarOwnership.PlatformCore
  }
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
