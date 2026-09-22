// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.dev

import com.intellij.platform.buildScripts.concurrency.Subtask
import io.opentelemetry.api.common.AttributeKey
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
import org.jetbrains.intellij.build.impl.satisfiesDevBundlingRequirements
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

internal fun buildPluginsForDevMode(
  request: BuildRequest,
  pluginLayouts: List<PluginLayout>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Subtask<PlatformLayout>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  platformEntriesProvider: () -> List<DistributionFileEntry>,
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
  )
  // The prebuilt plugin directories are not plugin layouts. The one assembly that owns plugins owns them too.
  val additionalPlugins = copyAdditionalPlugins(runDir.resolve("plugins"), context)
  return PluginsLayoutResult(descriptors, additionalPlugins)
}

/**
 * Lays out ALL bundled plugins for dev mode (no scrambling). The result feeds the platform ZKM
 * run via `coScrambleEntriesProvider` / `classpathDirsProvider`, then per-plugin scramble runs
 * after platform scramble via [scrambleAlreadyLaidOutPluginsForDevMode].
 */
internal fun layoutAllPluginsForDevMode(
  request: BuildRequest,
  pluginLayouts: List<PluginLayout>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Subtask<PlatformLayout>,
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
  )
}

private fun buildPluginDescriptorsForDevMode(
  os: OsFamily,
  arch: JvmArchitecture,
  plugins: List<PluginLayout>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Subtask<PlatformLayout>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  platformEntriesProvider: (() -> List<DistributionFileEntry>)?,
  layoutOnly: Boolean,
): List<PluginBuildResult> {
  if (plugins.isEmpty()) return emptyList()
  val pluginRootDir = runDir.resolve("plugins")
  Files.createDirectories(pluginRootDir)
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
    ) { _, layout, pluginDirOrFile ->
      if (layout != null) {
        buildPlatformSpecificPluginResources(
          plugin = layout,
          pluginDirs = listOf(targetPlatform to pluginDirOrFile),
          context = context,
          isDevMode = true,
        )
      }
      else {
        emptyList()
      }
    }
  }
}

/** Per-plugin scramble for non-co-scramble plugins after platform scramble has completed (dev mode). */
internal fun scrambleAlreadyLaidOutPluginsForDevMode(
  descriptors: List<PluginBuildResult>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Subtask<PlatformLayout>,
  layoutsOfPluginsToScramble: Map<String, PluginLayout>,
  platformEntriesProvider: () -> List<DistributionFileEntry>,
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
  val additionalPlugins = copyAdditionalPlugins(runDir.resolve("plugins"), context)
  return PluginsLayoutResult(descriptors, additionalPlugins)
}

internal fun devModePluginCandidates(request: BuildRequest, context: BuildContext): List<PluginLayout> {
  check(request.fragment.ownsPlugins) { "The '${request.fragment}' fragment owns no plugin" }
  val bundledMainModuleNames = getBundledMainModuleNames(context, request.additionalModules)
  // The candidate set is the product's whole bundled set: the one assembly that owns plugins owns every one of them.
  val owned = getPluginLayoutsByJpsModuleNames(bundledMainModuleNames, context.productProperties.productLayout)

  // One plugin reaches this point as one variant for each supported (os, arch): see `NATIVE_DEBUG_ALL_LAYOUTS` and
  // `rustPluginOsSpecificLayouts`. A distribution holds one of them, so the target platform selects a variant rather
  // than filtering a flat list. Grouping asks the question the caller asks, which is about a plugin and not about a
  // variant. `groupBy` keeps the encounter order, so the result follows the order of `owned`.
  val demanded = demandedMainModules(request)
  return selectDevModePluginVariants(
    owned = owned,
    os = request.os,
    arch = request.arch,
    isApplicable = { isPluginApplicable(bundledMainModuleNames, it, request.os, request.arch, context) },
    checkAbsence = { mainModule, variants ->
      checkTheAbsenceIsIntended(mainModule = mainModule, variants = variants, demanded = demanded, request = request, context = context)
    },
  )
}

/** Selects original layouts from source/model facts. Dev builds do not apply release-cycle restrictions. */
internal fun devModePluginCandidates(
  owned: List<PluginLayout>,
  bundledMainModuleNames: Set<String>,
  demanded: Set<String>,
  fragmentName: String,
  platformPrefix: String,
  os: OsFamily,
  arch: JvmArchitecture,
  bundledPluginDirectoriesToSkip: Collection<String>,
): List<PluginLayout> {
  return selectDevModePluginVariants(
    owned = owned,
    os = os,
    arch = arch,
    isApplicable = { plugin ->
      isPluginApplicable(bundledMainModuleNames, plugin, os) { targetOs ->
        satisfiesDevBundlingRequirements(plugin, targetOs, arch, bundledPluginDirectoriesToSkip)
      }
    },
    checkAbsence = { mainModule, variants ->
      checkTheAbsenceIsIntended(
        mainModule, variants, demanded, fragmentName, platformPrefix, os, arch,
        isAbsentBecauseOfTargetPlatform = { plugin ->
          isAbsentBecauseOfTheTargetPlatform(plugin) { targetOs, targetArch ->
            satisfiesDevBundlingRequirements(plugin, targetOs, targetArch, bundledPluginDirectoriesToSkip)
          }
        },
        buildDescription = { " isDevDistribution=true, useReleaseCycleRelatedBundlingRestrictions=false, skippedDirectories=$bundledPluginDirectoriesToSkip." },
      )
    },
  )
}

private fun selectDevModePluginVariants(
  owned: Collection<PluginLayout>,
  os: OsFamily,
  arch: JvmArchitecture,
  isApplicable: (PluginLayout) -> Boolean,
  checkAbsence: (String, List<PluginLayout>) -> Unit,
): List<PluginLayout> {
  val result = ArrayList<PluginLayout>(owned.size)
  for ((mainModule, variants) in owned.groupBy(PluginLayout::mainModule)) {
    val applicable = variants.filter(isApplicable)
    when (applicable.size) {
      1 -> result.add(applicable.single())
      0 -> checkAbsence(mainModule, variants)
      else -> error(
        "Plugin '$mainModule' has ${applicable.size} variants for $os $arch. A distribution holds" +
        " one variant of a plugin, so these would overwrite each other: " +
        applicable.joinToString { "[${it.bundlingRestrictions}] -> plugins/${it.directoryName}" } +
        ". Restrict the variants so that one of them remains."
      )
    }
  }
  return result
}

/** The plugins this assembly was told to bundle, which is not the same set as the plugins it may assemble. */
private fun demandedMainModules(request: BuildRequest): Set<String> = HashSet(request.additionalModules)

/**
 * Fails when a plugin this assembly was told to bundle is absent, and the target platform does not explain it.
 *
 * A plugin that quietly does not appear is invisible here and surfaces far away. It cost an EAP branch a day of red
 * builds. The layout dropped `intellij.air.plugin` and `intellij.devkit` over a release-cycle bundling restriction a dev
 * distribution should never have applied. The only symptom was a report about 149 jars with no destination.
 *
 * The target platform is the normal reason for an absence, so it is never a failure here. `intellij.laf.macos` has a
 * MACOS variant alone, and a LINUX distribution is right to hold none of it. What this checks is the rest:
 * [org.jetbrains.intellij.build.BuildOptions.bundledPluginDirectoriesToSkip] and the release cycle.
 *
 * What is *not* checked is a bundled plugin nobody named. Its own restrictions are the normal reason for it to be
 * absent.
 */
private fun checkTheAbsenceIsIntended(
  mainModule: String,
  variants: List<PluginLayout>,
  demanded: Set<String>,
  request: BuildRequest,
  context: BuildContext,
) {
  checkTheAbsenceIsIntended(
    mainModule, variants, demanded, request.fragment.name, request.platformPrefix, request.os, request.arch,
    isAbsentBecauseOfTargetPlatform = { isAbsentBecauseOfTheTargetPlatform(plugin = it, context = context) },
    buildDescription = { " isDevDistribution=${context.options.isDevDistribution}, isNightlyBuild=${context.isNightlyBuild}." },
  )
}

private fun checkTheAbsenceIsIntended(
  mainModule: String,
  variants: List<PluginLayout>,
  demanded: Set<String>,
  fragmentName: String,
  platformPrefix: String,
  os: OsFamily,
  arch: JvmArchitecture,
  isAbsentBecauseOfTargetPlatform: (PluginLayout) -> Boolean,
  buildDescription: () -> String,
) {
  if (!demanded.contains(mainModule) || variants.all(isAbsentBecauseOfTargetPlatform)) {
    return
  }

  error(
    "Fragment '$fragmentName' of $platformPrefix was asked for the plugin '$mainModule', and then" +
    " left it out of the distribution, so nothing would assemble it. Its variants are " +
    variants.joinToString { "[${it.bundlingRestrictions}]" } +
    ", and the target platform is $os $arch." +
    buildDescription() +
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
  return isAbsentBecauseOfTheTargetPlatform(plugin) { os, arch ->
    satisfiesBundlingRequirements(plugin = plugin, osFamily = os, arch = arch, context = context)
  }
}

private fun isAbsentBecauseOfTheTargetPlatform(
  plugin: PluginLayout,
  satisfiesRequirements: (OsFamily?, JvmArchitecture?) -> Boolean,
): Boolean {
  val restrictions = plugin.bundlingRestrictions
  if (restrictions === PluginBundlingRestrictions.MARKETPLACE) {
    // A marketplace variant is uploaded, never bundled. `PluginBundlingRestrictions.MARKETPLACE` requires a bundled
    // sibling of its own, and `validatePluginModel` owns that rule.
    return true
  }

  // `satisfiesBundlingRequirements` wants a null os for an os-independent variant, so ask it both ways.
  val arch = restrictions.supportedArch.firstOrNull()
  return satisfiesRequirements(restrictions.supportedOs.firstOrNull(), arch) || satisfiesRequirements(null, arch)
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
  return isPluginApplicable(bundledMainModuleNames, plugin, os) { targetOs ->
    satisfiesBundlingRequirements(plugin = plugin, osFamily = targetOs, arch = arch, context = context)
  }
}

private fun isPluginApplicable(
  bundledMainModuleNames: Set<String>,
  plugin: PluginLayout,
  os: OsFamily,
  satisfiesRequirements: (OsFamily?) -> Boolean,
): Boolean {
  if (!bundledMainModuleNames.contains(plugin.mainModule)) {
    return false
  }

  if (plugin.bundlingRestrictions == PluginBundlingRestrictions.NONE) {
    return true
  }

  return satisfiesRequirements(os) || satisfiesRequirements(null)
}

private fun getBundledMainModuleNames(context: BuildContext, additionalModules: List<String>): Set<String> {
  val bundledPluginModules = context.getBundledPluginModules()
  val result = LinkedHashSet<String>(bundledPluginModules.size + additionalModules.size)
  result.addAll(bundledPluginModules)
  result.addAll(additionalModules)
  return result
}
