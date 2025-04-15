// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.bazel.jvm.worker.impl

import com.intellij.openapi.util.io.FileFilters
import org.jetbrains.bazel.jvm.util.emptyList
import org.jetbrains.jps.indices.ModuleExcludeIndex
import org.jetbrains.jps.model.module.JpsModule
import java.io.File
import java.io.FileFilter
import java.nio.file.Path

internal object NoopModuleExcludeIndex : ModuleExcludeIndex {
  override fun isExcluded(file: File) = false

  override fun isExcludedFromModule(file: File, module: JpsModule) = false

  override fun isInContent(file: File) = true

  override fun getModuleExcludes(module: JpsModule): Collection<Path> = emptyList()

  override fun getModuleFileFilterHonorExclusionPatterns(module: JpsModule): FileFilter = FileFilters.EVERYTHING
}