// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.dev.DevPluginResourceExclusions
import org.jetbrains.intellij.build.dev.LayoutAsset
import org.jetbrains.intellij.build.dev.LayoutAssetOwner
import org.jetbrains.intellij.build.dev.LayoutAssetSource
import org.jetbrains.intellij.build.dev.LayoutAssetSpec
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path

/**
 * Declares a filtered resource tree for production and development. [DevPluginResourceExclusions] states the pattern
 * rules. The source carries the exclusions, so the development layout is a plain copy of a filtered filegroup.
 */
@ApiStatus.Internal
class ModuleResourceTree(
  moduleName: String,
  resourcePath: String,
  relativeOutputPath: String,
  excludedFiles: List<String> = emptyList(),
  excludedDirectories: List<String> = emptyList(),
) : LayoutAssetOwner, ResourceGenerator {
  override val layoutAssetSpec: LayoutAssetSpec

  init {
    require(moduleName.isNotBlank()) { "A resource tree requires a module" }
    require(relativeOutputPath.isNotEmpty()) { "A resource tree requires an output path" }
    for (path in listOf(resourcePath, relativeOutputPath).filter(String::isNotEmpty)) {
      require(path.none { it == '\\' || it == ':' } && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
        "A resource tree requires a relative path: $path"
      }
    }
    val exclusions = DevPluginResourceExclusions(files = excludedFiles.toList(), directories = excludedDirectories.toList())
    layoutAssetSpec = LayoutAssetSpec(
      sources = listOf(LayoutAssetSource.ModuleDirectory(moduleName, resourcePath, exclusions)),
      assets = listOf(LayoutAsset(destination = relativeOutputPath, sources = listOf(0))),
    )
  }

  override fun invoke(targetDirectory: Path, context: BuildContext) {
    val source = layoutAssetSpec.sources.single() as LayoutAssetSource.ModuleDirectory
    val module = context.findRequiredModule(source.moduleName)
    val contentRoot = JpsPathUtil.urlToNioPath(module.contentRootsList.urls.first())
    copyTo(contentRoot.resolve(source.path), targetDirectory)
  }

  /** Copies the declared tree from a resolved source directory. */
  fun copyTo(sourceDirectory: Path, targetDirectory: Path) {
    require(Files.isDirectory(sourceDirectory)) { "The resource tree is missing: $sourceDirectory" }
    val exclusions = (layoutAssetSpec.sources.single() as LayoutAssetSource.ModuleDirectory).exclusions
    val fileMatchers = exclusions.fileGlobs().map { sourceDirectory.fileSystem.getPathMatcher("glob:$it") }
    val directoryMatchers = exclusions.directoryGlobs().map { sourceDirectory.fileSystem.getPathMatcher("glob:$it") }
    copyDir(
      sourceDir = sourceDirectory,
      targetDir = targetDirectory.resolve(layoutAssetSpec.assets.single().destination),
      dirFilter = { path -> directoryMatchers.none { it.matches(sourceDirectory.relativize(path)) } },
      fileFilter = { path -> fileMatchers.none { it.matches(sourceDirectory.relativize(path)) } },
    )
  }
}
