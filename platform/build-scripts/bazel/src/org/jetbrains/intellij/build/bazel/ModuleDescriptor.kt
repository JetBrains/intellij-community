// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

internal data class ModuleDescriptor(
  @JvmField val module: JpsModule,
  @JvmField val contentRoots: List<Path>,
  @JvmField val sources: List<SourceDirDescriptor>,
  @JvmField val resources: List<ResourceDescriptor>,
  @JvmField val testSources: List<SourceDirDescriptor>,
  @JvmField val testResources: List<ResourceDescriptor>,
  @JvmField val isCommunity: Boolean,
  @JvmField val bazelBuildFileDir: Path,
  @JvmField val targetName: String,
)

internal data class ResourceDescriptor(
  @JvmField val baseDirectory: String,
  @JvmField val files: List<String>,
)

internal data class SourceDirDescriptor(
  @JvmField val glob: List<String>,
  @JvmField val excludes: List<String>,
)