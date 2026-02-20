// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.collections.immutable.PersistentList
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CustomAssetShimSource
import org.jetbrains.intellij.build.FileSource
import org.jetbrains.intellij.build.UnpackedZipSource
import org.jetbrains.intellij.build.dependencies.extractFileToCacheLocation
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * @param pluginDirs List of OS-specific plugin directories, with appended `plugin.directoryName` already
 */
internal suspend fun buildPlatformSpecificPluginResources(
  plugin: PluginLayout,
  pluginDirs: List<Pair<SupportedDistribution, Path>>,
  context: BuildContext,
  isDevMode: Boolean
): List<DistributionFileEntry> {
  if (!isDevMode) {
    // Keeping old behavior: `platformResourceGenerators` were not called in dev-mode
    for ((dist, generators) in plugin.platformResourceGenerators) {
      handlePlatformResourceGenerator(dist, generators, pluginDirs, context)
    }
  }

  for ((dist, generators) in plugin.platformResourceGeneratorsDevMode) {
    handlePlatformResourceGenerator(dist, generators, pluginDirs, context)
  }

  val distEntries = ArrayList<DistributionFileEntry>()
  for ((platform, pluginDir) in pluginDirs) {
    distEntries.addAll(handleCustomPlatformSpecificAssets(
      layout = plugin,
      targetPlatform = platform,
      context = context,
      pluginDir = pluginDir,
      isDevMode = isDevMode,
    ))
  }
  return distEntries
}

private suspend fun handlePlatformResourceGenerator(
  dist: SupportedDistribution,
  generators: PersistentList<ResourceGenerator>,
  pluginDirs: List<Pair<SupportedDistribution, Path>>,
  context: BuildContext
) {
  val pluginDir = pluginDirs.firstOrNull { it.first == dist }?.second ?: return
  val relativePluginDir = context.paths.buildOutputDir.relativize(pluginDir).toString()
  for (generator in generators) {
    spanBuilder("plugin platform-specific resources")
      .setAttribute("path", relativePluginDir)
      .setAttribute("os", dist.os.toString())
      .setAttribute("arch", dist.arch.toString())
      .use {
        generator(pluginDir, context)
      }
  }
}

internal suspend fun handleCustomPlatformSpecificAssets(
  layout: PluginLayout,
  targetPlatform: SupportedDistribution?,
  context: BuildContext,
  pluginDir: Path,
  isDevMode: Boolean,
): List<DistributionFileEntry> {
  val distEntries = ArrayList<DistributionFileEntry>()
  for (customAsset in layout.customAssets) {
    if (targetPlatform == null) {
      if (customAsset.platformSpecific != null) {
        continue
      }
    }
    else {
      val platformSpecific = customAsset.platformSpecific ?: continue
      if (platformSpecific != targetPlatform) {
        continue
      }
    }

    val rootDir = customAsset.relativePath?.let { pluginDir.resolve(it) } ?: pluginDir
    val lazySources = customAsset.getSources(context) ?: continue
    for (lazySource in lazySources) {
      require(lazySource.filter == null) {
        "please specify filter for wrapped sources, not for LazySource"
      }

      for (source in lazySource.getSources()) {
        when (source) {
          is UnpackedZipSource -> {
            val dir = extractFileToCacheLocation(archiveFile = source.file, communityRoot = context.paths.communityHomeDirRoot)
            val dirPrefix = dir.toString().length + 1
            copyDir(
              sourceDir = dir,
              targetDir = rootDir,
              fileFilter = source.filter?.let { filter ->
                {
                  filter(it.invariantSeparatorsPathString.substring(dirPrefix))
                }
              },
            )

            distEntries.add(CustomAssetEntry(path = source.file, hash = lazySource.precomputedHash, relativeOutputFile = customAsset.relativePath))
          }

          is CustomAssetShimSource -> {
            if (!isDevMode) {
              distEntries.addAll(source.task(pluginDir, context))
            }
          }

          is FileSource -> {
            copyFile(source.file, rootDir.resolve(source.relativePath))
            distEntries.add(CustomAssetEntry(path = source.file, hash = lazySource.precomputedHash, relativeOutputFile = customAsset.relativePath))
          }

          else -> throw UnsupportedOperationException("Not supported source for custom plugin platform-specific assets, got $source for $customAsset")
        }
      }
    }
  }
  return distEntries
}