// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CustomAssetShimSource
import org.jetbrains.intellij.build.UnpackedZipSource
import org.jetbrains.intellij.build.dependencies.extractFileToCacheLocation
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal suspend fun buildPlatformSpecificPluginResources(
  plugin: PluginLayout,
  targetDirs: List<Pair<SupportedDistribution, Path>>,
  context: BuildContext,
): List<DistributionFileEntry> {
  for ((dist, generators) in plugin.platformResourceGenerators) {
    val targetDir = targetDirs.firstOrNull { it.first == dist }?.second ?: continue
    val pluginDir = targetDir.resolve(plugin.directoryName)
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

  val distEntries = ArrayList<DistributionFileEntry>()
  for ((platform, targetDir) in targetDirs) {
    handleCustomPlatformSpecificAssets(
      layout = plugin,
      targetPlatform = platform,
      context = context,
      pluginDir = targetDir.resolve(plugin.directoryName),
      distEntries = distEntries,
      isDevMode = false,
    )
  }
  return distEntries
}

internal suspend fun handleCustomPlatformSpecificAssets(
  layout: PluginLayout,
  targetPlatform: SupportedDistribution,
  context: BuildContext,
  pluginDir: Path,
  distEntries: MutableList<DistributionFileEntry>,
  isDevMode: Boolean,
) {
  for (customAsset in layout.customAssets) {
    val platformSpecific = customAsset.platformSpecific ?: continue
    if (platformSpecific != targetPlatform) {
      continue
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

          else -> throw UnsupportedOperationException("Not supported source for custom plugin platform-specific assets, got $source for $customAsset")
        }
      }
    }
  }
}