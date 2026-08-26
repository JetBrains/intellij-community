// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.dev

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.classPath.PluginBuildResult
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.buildPlatformSpecificPluginResources
import org.jetbrains.intellij.build.impl.copyAdditionalPlugins
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.impl.plugins.buildPlugins
import org.jetbrains.intellij.build.impl.plugins.scrambleAlreadyLaidOutPlugins
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.satisfiesBundlingRequirements
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path

internal data class PluginsLayoutResult(
  @JvmField val pluginEntries: List<PluginBuildResult>,
  @JvmField val additionalPlugins: List<Pair<Path, List<Path>>>?,
)

internal enum class DevModePluginBuildStrategy {
  NORMAL,
  LAYOUT_BEFORE_PLATFORM_SCRAMBLE,
}

internal fun selectDevModePluginBuildStrategy(request: BuildRequest, context: BuildContext, pluginLayouts: List<PluginLayout>): DevModePluginBuildStrategy {
  if (!context.productProperties.scrambleMainJar || request.scrambleTool == null || context.isStepSkipped(BuildOptions.SCRAMBLING_STEP)) {
    return DevModePluginBuildStrategy.NORMAL
  }
  return if (pluginLayouts.any { it.scrambleWithPlatform }) {
    DevModePluginBuildStrategy.LAYOUT_BEFORE_PLATFORM_SCRAMBLE
  }
  else {
    DevModePluginBuildStrategy.NORMAL
  }
}

internal suspend fun buildPluginsForDevMode(
  request: BuildRequest,
  pluginLayouts: List<PluginLayout>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  platformEntriesProvider: suspend () -> List<DistributionFileEntry>,
): PluginsLayoutResult {
  val descriptors = buildPluginDescriptorsForDevMode(
    os = request.os,
    arch = request.arch,
    plugins = pluginLayouts,
    context = context,
    runDir = runDir,
    platformLayout = platformLayout,
    searchableOptionSet = searchableOptionSet,
    platformEntriesProvider = platformEntriesProvider,
    layoutOnly = false,
    prepackedPluginContent = request.prepackedPluginContent,
  )
  // Prebuilt plugin directories are not plugin layouts, so no fragment can claim them by name - one fragment owns them
  // all, and it is the one that also assembles whatever the named fragments did not claim.
  val additionalPlugins = if (checkNotNull(request.fragment.plugins).ownsPrebuiltPluginDirs) {
    copyAdditionalPlugins(runDir.resolve("plugins"), context)
  }
  else {
    null
  }
  return PluginsLayoutResult(descriptors, additionalPlugins)
}

/**
 * Lays out ALL bundled plugins for dev mode (no scrambling). The result feeds the platform ZKM
 * run via `coScrambleEntriesProvider` / `classpathDirsProvider`, then per-plugin scramble runs
 * after platform scramble via [scrambleAlreadyLaidOutPluginsForDevMode].
 */
internal suspend fun layoutAllPluginsForDevMode(
  request: BuildRequest,
  pluginLayouts: List<PluginLayout>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
): List<PluginBuildResult> {
  return buildPluginDescriptorsForDevMode(
    os = request.os,
    arch = request.arch,
    plugins = pluginLayouts,
    context = context,
    runDir = runDir,
    platformLayout = platformLayout,
    searchableOptionSet = searchableOptionSet,
    platformEntriesProvider = null,
    layoutOnly = true,
    prepackedPluginContent = request.prepackedPluginContent,
  )
}

private suspend fun buildPluginDescriptorsForDevMode(
  os: OsFamily,
  arch: JvmArchitecture,
  plugins: List<PluginLayout>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  platformEntriesProvider: (suspend () -> List<DistributionFileEntry>)?,
  layoutOnly: Boolean,
  prepackedPluginContent: Map<PrepackedPluginContentKey, PrepackedPluginContentJar>,
): List<PluginBuildResult> {
  if (plugins.isEmpty()) return emptyList()
  val pluginRootDir = runDir.resolve("plugins")
  withContext(Dispatchers.IO) {
    Files.createDirectories(pluginRootDir)
  }
  val platform = platformLayout.await()
  val spanName = if (layoutOnly) "lay out plugins" else "build plugins"
  return spanBuilder(spanName).setAttribute(AttributeKey.longKey("count"), plugins.size.toLong()).use {
    val targetPlatform = SupportedDistribution(os = os, arch = arch, libcImpl = LibcImpl.current(os))
    buildPlugins(
      plugins = plugins,
      os = null,
      arch = null,
      targetDir = pluginRootDir,
      state = DistributionBuilderState(platformLayout = platform, pluginsToPublish = emptySet(), context = context),
      platformEntriesProvider = platformEntriesProvider,
      searchableOptionSet = searchableOptionSet,
      descriptorCacheContainer = platform.descriptorCacheContainer,
      context = context,
      layoutOnly = layoutOnly,
      prepackedPluginContent = prepackedPluginContent,
    ) { layout, pluginDirOrFile ->
      buildPlatformSpecificPluginResources(
        plugin = layout,
        pluginDirs = listOf(targetPlatform to pluginDirOrFile),
        context = context,
        isDevMode = true,
      )
    }
  }
}

/** Per-plugin scramble for non-co-scramble plugins after platform scramble has completed (dev mode). */
internal suspend fun scrambleAlreadyLaidOutPluginsForDevMode(
  request: BuildRequest,
  descriptors: List<PluginBuildResult>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  layoutsOfPluginsToScramble: Map<String, PluginLayout>,
  platformEntriesProvider: suspend () -> List<DistributionFileEntry>,
): PluginsLayoutResult {
  val platform = platformLayout.await()
  val state = DistributionBuilderState(platformLayout = platform, pluginsToPublish = emptySet(), context = context)
  // wait for platform scramble before running per-plugin scramble (it needs the scrambled platform jars on classpath)
  val platformEntries = platformEntriesProvider()
  scrambleAlreadyLaidOutPlugins(
    descriptors = descriptors,
    state = state,
    platformEntries = platformEntries,
    layoutsOfPluginsToScramble = layoutsOfPluginsToScramble,
    context = context,
  )
  val pluginRootDir = runDir.resolve("plugins")
  // The same rule as on the non-scrambling path: only the fragment that assembles what nobody claimed owns these, and
  // this path is reached only by a complete distribution, which owns them either way.
  val additionalPlugins = if (checkNotNull(request.fragment.plugins).ownsPrebuiltPluginDirs) {
    copyAdditionalPlugins(pluginRootDir, context)
  }
  else {
    null
  }
  return PluginsLayoutResult(descriptors, additionalPlugins)
}

internal fun devModePluginCandidates(request: BuildRequest, context: BuildContext): List<PluginLayout> {
  val selector = checkNotNull(request.fragment.plugins)
  val bundledMainModuleNames = getBundledMainModuleNames(context, request.additionalModules)
  selector.checkNamesAreKnown(bundledMainModuleNames, request.fragment.name)
  // The candidate set is the product's, and the fragment takes its share of it. Computing the whole set in every
  // fragment is what makes `Remaining` exact: it is the complement of what the named fragments claimed, not a
  // second list that could drift from them.
  val owned = getPluginLayoutsByJpsModuleNames(bundledMainModuleNames, context.productProperties.productLayout)
    .filter { selector.accepts(it.mainModule) }

  // One plugin reaches this point as one variant for each supported (os, arch): see `NATIVE_DEBUG_ALL_LAYOUTS` and
  // `rustPluginOsSpecificLayouts`. A distribution holds one of them, so the target platform selects a variant rather
  // than filtering a flat list. Grouping asks the question the caller asks, which is about a plugin and not about a
  // variant. `groupBy` keeps the encounter order, so the result follows the order of `owned`.
  val demanded = demandedMainModules(request)
  val result = ArrayList<PluginLayout>(owned.size)
  for ((mainModule, variants) in owned.groupBy(PluginLayout::mainModule)) {
    val applicable = variants.filter {
      isPluginApplicable(
        bundledMainModuleNames = bundledMainModuleNames,
        plugin = it,
        os = request.os,
        arch = request.arch,
        context = context,
      )
    }
    when (applicable.size) {
      1 -> result.add(applicable.single())
      0 -> checkTheAbsenceIsIntended(mainModule = mainModule, variants = variants, demanded = demanded, request = request, context = context)
      else -> error(
        "Plugin '$mainModule' has ${applicable.size} variants for ${request.os} ${request.arch}. A distribution holds" +
        " one variant of a plugin, so these would overwrite each other: " +
        applicable.joinToString { "[${it.bundlingRestrictions}] -> plugins/${it.directoryName}" } +
        ". Restrict the variants so that one of them remains."
      )
    }
  }
  return result
}

/** The plugins this fragment was told to assemble, which is not the same set as the plugins it may assemble. */
private fun demandedMainModules(request: BuildRequest): Set<String> {
  // A plugin reaches a fragment either way, so both sources count as a demand.
  val result = HashSet(request.additionalModules)
  (request.fragment.plugins as? PluginFragmentSelector.Named)?.let { result.addAll(it.mainModules) }
  return result
}

/**
 * Fails when a plugin this fragment was told to assemble is absent, and the target platform does not explain it.
 *
 * The sibling of the `checkNamesAreKnown` call in [devModePluginCandidates], and for the same reason: a plugin that
 * quietly does not appear is invisible here and surfaces far away. It cost an EAP branch a day of red builds. The
 * layout dropped `intellij.air.plugin` and `intellij.devkit` over a release-cycle bundling restriction a dev
 * distribution should never have applied. The only symptom was `collectPrepackedPluginContentJars` reporting 149
 * Bazel-built jars with no destination.
 *
 * The target platform is the normal reason for an absence, so it is never a failure here. `intellij.laf.macos` has a
 * MACOS variant alone, and a LINUX distribution is right to hold none of it. What this checks is the rest:
 * [org.jetbrains.intellij.build.BuildOptions.bundledPluginDirectoriesToSkip] and the release cycle.
 *
 * What is *not* checked is a bundled plugin nobody named. Its own restrictions are the normal reason for it to be
 * absent. A [PluginFragmentSelector.Remaining] fragment's complement is not checked either, because it describes what
 * is left rather than demanding a list.
 */
private fun checkTheAbsenceIsIntended(
  mainModule: String,
  variants: List<PluginLayout>,
  demanded: Set<String>,
  request: BuildRequest,
  context: BuildContext,
) {
  if (!demanded.contains(mainModule) || variants.all { isAbsentBecauseOfTheTargetPlatform(plugin = it, context = context) }) {
    return
  }

  error(
    "Fragment '${request.fragment.name}' of ${request.platformPrefix} was asked for the plugin '$mainModule', and then" +
    " left it out of the distribution, so nothing would assemble it. Its variants are " +
    variants.joinToString { "[${it.bundlingRestrictions}]" } +
    ", and the target platform is ${request.os} ${request.arch}." +
    " isDevDistribution=${context.options.isDevDistribution}, isNightlyBuild=${context.isNightlyBuild}." +
    " Either stop requesting it here, or let the restriction admit it."
  )
}

/**
 * Whether the target platform alone keeps [plugin] out of the distribution.
 *
 * Asks [satisfiesBundlingRequirements] again with the platform the variant itself names, so neither the os clause nor
 * the arch clause can say no a second time. What can still say no is what a target platform does not explain.
 */
private fun isAbsentBecauseOfTheTargetPlatform(plugin: PluginLayout, context: BuildContext): Boolean {
  val restrictions = plugin.bundlingRestrictions
  if (restrictions === PluginBundlingRestrictions.MARKETPLACE) {
    // A marketplace variant is uploaded, never bundled. `PluginBundlingRestrictions.MARKETPLACE` requires a bundled
    // sibling of its own, and `validatePluginModel` owns that rule.
    return true
  }

  // `satisfiesBundlingRequirements` wants a null os for an os-independent variant, so ask it both ways.
  val arch = restrictions.supportedArch.firstOrNull()
  return satisfiesBundlingRequirements(plugin = plugin, osFamily = restrictions.supportedOs.firstOrNull(), arch = arch, context = context) ||
         satisfiesBundlingRequirements(plugin = plugin, osFamily = null, arch = arch, context = context)
}

internal fun collectLayoutsOfPluginsToScramble(pluginLayouts: Collection<PluginLayout>): Map<String, PluginLayout> {
  return pluginLayouts.asSequence()
    .filter { it.pathsToScramble.isNotEmpty() }
    .groupBy { it.mainModule }
    .mapValues { it.value.singleOrNull() ?: error("Multiple layouts for plugin ${it.key}") }
}

internal fun isPluginApplicable(
  bundledMainModuleNames: Set<String>,
  plugin: PluginLayout,
  os: OsFamily,
  arch: JvmArchitecture,
  context: BuildContext,
): Boolean {
  if (!bundledMainModuleNames.contains(plugin.mainModule)) {
    return false
  }

  if (plugin.bundlingRestrictions == PluginBundlingRestrictions.NONE) {
    return true
  }

  return satisfiesBundlingRequirements(plugin = plugin, osFamily = os, arch = arch, context = context) ||
         satisfiesBundlingRequirements(plugin = plugin, osFamily = null, arch = arch, context = context)
}

private fun getBundledMainModuleNames(context: BuildContext, additionalModules: List<String>): Set<String> {
  val bundledPluginModules = context.getBundledPluginModules()
  val result = LinkedHashSet<String>(bundledPluginModules.size + additionalModules.size)
  result.addAll(bundledPluginModules)
  result.addAll(additionalModules)
  return result
}
