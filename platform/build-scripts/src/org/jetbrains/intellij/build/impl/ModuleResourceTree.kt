// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.dev.DevPluginLayoutAsset
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetMapping
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetTransform
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/** Declares a filtered resource tree for production and development. Patterns match paths relative to the source directory. */
@ApiStatus.Internal
class ModuleResourceTree(
  moduleName: String,
  resourcePath: String,
  relativeOutputPath: String,
  excludes: List<String> = emptyList(),
  directoryExcludes: List<String> = emptyList(),
) : DevPluginLayoutAssetOwner, ResourceGenerator {
  override val devPluginLayoutAssetSpec: DevPluginLayoutAssetSpec

  init {
    require(moduleName.isNotBlank()) { "A resource tree requires a module" }
    require(relativeOutputPath.isNotEmpty()) { "A resource tree requires an output path" }
    for (path in listOf(resourcePath, relativeOutputPath).filter(String::isNotEmpty)) {
      require(path.none { it == '\\' || it == ':' } && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
        "A resource tree requires a relative path: $path"
      }
    }
    for (pattern in excludes + directoryExcludes) {
      require(pattern.isNotEmpty()) { "A resource tree exclusion requires a pattern" }
      FileSystems.getDefault().getPathMatcher("glob:$pattern")
    }
    devPluginLayoutAssetSpec = DevPluginLayoutAssetSpec(
      sources = listOf(DevPluginLayoutAssetSource.ModuleDirectory(moduleName, resourcePath)),
      assets = listOf(DevPluginLayoutAsset(
        destination = relativeOutputPath,
        sources = listOf(0),
        transform = DevPluginLayoutAssetTransform.treeMap(
          mappings = listOf(DevPluginLayoutAssetMapping()),
          excludes = excludes.toList(),
          directoryExcludes = directoryExcludes.toList(),
        ),
      )),
    )
  }

  override fun invoke(targetDirectory: Path, context: BuildContext) {
    val source = devPluginLayoutAssetSpec.sources.single() as DevPluginLayoutAssetSource.ModuleDirectory
    val module = context.findRequiredModule(source.moduleName)
    val contentRoot = JpsPathUtil.urlToNioPath(module.contentRootsList.urls.first())
    copyTo(contentRoot.resolve(source.path), targetDirectory)
  }

  /** Copies the declared tree from a resolved source directory. */
  fun copyTo(sourceDirectory: Path, targetDirectory: Path) {
    require(Files.isDirectory(sourceDirectory)) { "The resource tree is missing: $sourceDirectory" }
    val asset = devPluginLayoutAssetSpec.assets.single()
    val transform = requireNotNull(asset.transform)
    val fileMatchers = transform.excludes.map { sourceDirectory.fileSystem.getPathMatcher("glob:$it") }
    val directoryMatchers = transform.directoryExcludes.map { sourceDirectory.fileSystem.getPathMatcher("glob:$it") }
    copyDir(
      sourceDir = sourceDirectory,
      targetDir = targetDirectory.resolve(asset.destination),
      dirFilter = { path -> directoryMatchers.none { it.matches(sourceDirectory.relativize(path)) } },
      fileFilter = { path -> fileMatchers.none { it.matches(sourceDirectory.relativize(path)) } },
    )
  }
}