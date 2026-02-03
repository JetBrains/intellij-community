// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

internal data class ModuleDescriptor(
  @JvmField val imlFile: Path,
  @JvmField val module: JpsModule,
  @JvmField val contentRoots: List<Path>,
  @JvmField val sources: List<SourceDirDescriptor>,
  @JvmField val resources: List<ResourceDescriptor>,
  @JvmField val testSources: List<SourceDirDescriptor>,
  @JvmField val testResources: List<ResourceDescriptor>,
  @JvmField val isCommunity: Boolean,
  @JvmField val bazelBuildFileDir: Path,
  @JvmField val relativePathFromProjectRoot: Path,
  @JvmField val targetName: String,
) {
  init {
    require(bazelBuildFileDir.isAbsolute) {
      "bazelBuildFileDir must be absolute: $bazelBuildFileDir"
    }

    require(!relativePathFromProjectRoot.isAbsolute) {
      "relativePathFromProjectRoot must be relative: $relativePathFromProjectRoot"
    }

    require(bazelBuildFileDir.endsWith(relativePathFromProjectRoot) || relativePathFromProjectRoot.toString().isEmpty()) {
      "bazelBuildFileDir must end with relativePathFromProjectRoot: bazelBuildFileDir=$bazelBuildFileDir, relativePathFromProjectRoot=$relativePathFromProjectRoot"
    }

    require(imlFile.isAbsolute) {
      "imlFile must be an absolute path: $imlFile"
    }

    require(imlFile.exists()) {
      "imlFile must be exist: $imlFile"
    }

    require(imlFile.isRegularFile()) {
      "imlFile must be a regular file: $imlFile"
    }
  }

  val targetAsLabel = BazelLabel(targetName, this)
}

internal data class ResourceDescriptor(
  @JvmField val baseDirectory: String,
  @JvmField val files: List<String>,
  @JvmField val relativeOutputPath: String,
)

internal data class SourceDirDescriptor(
  @JvmField val glob: List<String>,
  @JvmField val excludes: List<String>,
)