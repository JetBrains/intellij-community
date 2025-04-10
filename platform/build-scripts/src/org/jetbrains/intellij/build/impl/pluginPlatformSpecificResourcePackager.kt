// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.UnpackedZipSource
import org.jetbrains.intellij.build.io.W_OVERWRITE
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.writeToFileChannelFully
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path

internal suspend fun buildPlatformSpecificPluginResources(
  plugin: PluginLayout,
  targetDirs: List<Pair<SupportedDistribution, Path>>,
  context: BuildContext,
) {
  for ((dist, generators) in plugin.platformResourceGenerators) {
    val targetPath = targetDirs.firstOrNull { it.first == dist }?.second ?: continue
    val pluginDir = targetPath.resolve(plugin.directoryName)
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

  for ((platform, pluginDir) in targetDirs) {
    handleCustomPlatformSpecificAssets(layout = plugin, targetPlatform = platform, context = context, pluginDir = pluginDir)
  }
}

@ApiStatus.Internal
fun unpackTrustedZip(
  source: UnpackedZipSource,
  rootDir: Path,
  createdParents: MutableSet<Path>,
) {
  readZipFile(source.file) { name, dataProvider ->
    if (!source.filter(name)) {
      return@readZipFile ZipEntryProcessorResult.CONTINUE
    }

    val file = rootDir.resolve(name)
    val parent = file.parent
    if (createdParents.add(parent)) {
      Files.createDirectories(parent)
    }

    val data = dataProvider()
    FileChannel.open(file, W_OVERWRITE).use {
      writeToFileChannelFully(channel = it, data = data)
    }
    ZipEntryProcessorResult.CONTINUE
  }
}

internal suspend fun handleCustomPlatformSpecificAssets(layout: PluginLayout, targetPlatform: SupportedDistribution, context: BuildContext, pluginDir: Path) {
  for (customAsset in layout.customAssets) {
    val platformSpecific = customAsset.platformSpecific ?: continue
    if (platformSpecific != targetPlatform) {
      continue
    }

    val rootDir = customAsset.relativePath?.let { pluginDir.resolve(it) } ?: pluginDir
    val lazySources = customAsset.getSources(context) ?: continue
    val createdParents = HashSet<Path>()
    for (lazySource in lazySources) {
      for (source in lazySource.getSources()) {
        require(source is UnpackedZipSource) {
          "Only UnpackedZipSource is supported for custom plugin platform-specific assets, got $source for $customAsset"
        }

        unpackTrustedZip(source = source, rootDir = rootDir, createdParents = createdParents)
      }
    }
  }
}