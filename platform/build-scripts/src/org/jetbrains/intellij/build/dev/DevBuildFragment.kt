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
 * is defined as a predicate over what the layout produced rather than as a list of files someone maintains, and each
 * family has a `Remaining` member that is the complement of its named siblings - what nobody claimed is assembled by
 * that fragment instead of being silently dropped.
 */
@ApiStatus.Internal
data class DevBuildFragment(
  /** Identifies the fragment in its component manifest and in diagnostics; `platform_core`, `cm_libraries_platform`, `plugins_air`. */
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

  override fun toString(): String = name
}

/**
 * Which `lib/` jars a fragment owns.
 *
 * The split follows the plugin model: `processAndGetProductPluginContentModules` gives every content module of the
 * product's core plugin a jar of its own, so those jars are separable, while everything else - the flat core jars that
 * merge many modules, and project libraries not yet converted to content modules - has to stay together.
 */
@ApiStatus.Internal
sealed interface PlatformFragmentSelector {
  /** Every `lib/` jar. */
  data object All : PlatformFragmentSelector

  /** The jars that hold no content module: the flat platform core, and the project libraries packed beside it. */
  data object Core : PlatformFragmentSelector

  /** The jars of the content modules these module sets declare, named without the `intellij.moduleSets.` prefix. */
  data class ContentModuleSets(@JvmField val setNames: Set<String>) : PlatformFragmentSelector

  /** Every content-module jar that [claimedSetNames] does not cover, including modules declared outside any set. */
  data class RemainingContentModules(@JvmField val claimedSetNames: Set<String>) : PlatformFragmentSelector
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

private const val MODULE_SET_NAME_PREFIX = "intellij.moduleSets."

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

  /** Every module set that declares a content module of this layout. */
  val contentModuleSetNames: Set<String>
    get() = byJar.values.asSequence().filterIsInstance<JarOwnership.ContentModule>().mapNotNull { it.setName }.toSet()

  companion object {
    fun of(includedModules: Collection<ModuleItem>): PlatformJarOwnership {
      val byJar = HashMap<String, JarOwnership>()
      for ((relativeOutputFile, modules) in includedModules.groupBy(ModuleItem::relativeOutputFile)) {
        byJar[relativeOutputFile] = jarOwnership(relativeOutputFile, modules)
      }
      return PlatformJarOwnership(byJar)
    }
  }
}

/**
 * What owns one `lib/` jar - the module set of the content module in it, or [JarOwnership.PlatformCore] when it holds none.
 *
 * A jar is the unit, not a module: [org.jetbrains.intellij.build.impl.PlatformLayout.withProductModuleOutputFile] lets a
 * content module be renamed into another jar, so ownership has to be decided for the jar as a whole.
 *
 * Two content modules from *different module sets* in one jar would put it in two fragments at once, so that fails here
 * rather than leaving the composer to report the same path twice. The layout cannot produce it today - a content module
 * gets `$moduleName.jar` unless renamed, and `ImplicitEmbeddedContentModuleValidator` keeps `includeDependencies`
 * closures free of content modules - and if it ever can, the fragment API has to grow a way to say which fragment wins.
 */
private fun jarOwnership(relativeOutputFile: String, includedModules: Collection<ModuleItem>): JarOwnership {
  var holdsContentModule = false
  var owner: ModuleItem? = null
  for (module in includedModules) {
    if (!module.isProductModule()) {
      continue
    }
    holdsContentModule = true
    // A set-less content module does not decide ownership while a declared one is present: `includeDependencies` on an
    // embedded module puts its whole JPS closure in that module's jar, and those companions carry no module set.
    if (contentModuleSetName(module) == null) {
      continue
    }
    if (owner != null && contentModuleSetName(owner) != contentModuleSetName(module)) {
      error(
        "'$relativeOutputFile' holds content modules from two module sets: '${owner.moduleName}' from" +
        " '${contentModuleSetName(owner)}' and '${module.moduleName}' from '${contentModuleSetName(module)}'." +
        " A jar is owned by one dev-build fragment, so it cannot be split between them."
      )
    }
    owner = module
  }
  return when {
    !holdsContentModule -> JarOwnership.PlatformCore
    else -> JarOwnership.ContentModule(owner?.let(::contentModuleSetName))
  }
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

/**
 * The module set that declares [module], or `null` if none does.
 *
 * [ModuleItem.moduleSet] is the chain of sets a content module was reached through, outermost first, so the last
 * element is the set that declares it - the finest grouping the model offers. It is absent for a module the product
 * lists directly (`additionalModules`) and for a product whose content still comes from a hand-written XML rather than
 * a [org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec]; both then fall to
 * [PlatformFragmentSelector.RemainingContentModules].
 */
private fun contentModuleSetName(module: ModuleItem): String? {
  return module.moduleSet?.lastOrNull()?.removePrefix(MODULE_SET_NAME_PREFIX)
}

internal sealed interface JarOwnership {
  data object PlatformCore : JarOwnership

  data class ContentModule(@JvmField val setName: String?) : JarOwnership
}

/**
 * Fails when this selector names a module set the product's layout does not have.
 *
 * A misspelled set is otherwise invisible: its fragment assembles nothing, and the set it was meant to name returns to
 * the `remaining` fragment, so the distribution is still complete and only the caching intent is quietly lost. An empty
 * *known* set is fine - a set can legitimately contribute no jar to a given target platform.
 */
internal fun PlatformFragmentSelector.checkNamesAreKnown(ownership: PlatformJarOwnership, fragmentName: String) {
  val named = when (this) {
    is PlatformFragmentSelector.ContentModuleSets -> setNames
    is PlatformFragmentSelector.RemainingContentModules -> claimedSetNames
    else -> return
  }
  val known = ownership.contentModuleSetNames
  val unknown = named.filterNot(known::contains)
  check(unknown.isEmpty()) {
    "Fragment '$fragmentName' names module sets this product's platform does not declare: ${unknown.sorted().joinToString()}." +
    " Known: ${known.sorted().joinToString()}"
  }
}

/** Whether the `lib/` jar at [relativeOutputFile] belongs to this fragment. */
internal fun PlatformFragmentSelector.accepts(ownership: PlatformJarOwnership, relativeOutputFile: String): Boolean {
  return when (this) {
    PlatformFragmentSelector.All -> true
    PlatformFragmentSelector.Core -> ownership.of(relativeOutputFile) == JarOwnership.PlatformCore
    is PlatformFragmentSelector.ContentModuleSets -> {
      val owner = ownership.of(relativeOutputFile)
      owner is JarOwnership.ContentModule && setNames.contains(owner.setName)
    }
    is PlatformFragmentSelector.RemainingContentModules -> {
      val owner = ownership.of(relativeOutputFile)
      owner is JarOwnership.ContentModule && !claimedSetNames.contains(owner.setName)
    }
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
 * Fails when this selector names a plugin the product does not bundle - the same silent no-op a misspelled module set
 * would be. [candidateMainModules] is the product's whole bundled set, which every plugin fragment computes.
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
