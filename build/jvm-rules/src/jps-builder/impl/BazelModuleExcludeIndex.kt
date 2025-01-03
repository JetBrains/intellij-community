package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.openapi.util.io.FileFilters
import org.jetbrains.jps.indices.ModuleExcludeIndex
import org.jetbrains.jps.model.module.JpsModule
import java.io.File
import java.io.FileFilter
import java.nio.file.Path

object BazelModuleExcludeIndex : ModuleExcludeIndex {
  override fun isExcluded(file: File) = false

  override fun isExcludedFromModule(file: File, module: JpsModule) = false

  override fun isInContent(file: File) = true

  override fun getModuleExcludes(module: JpsModule): Collection<Path> = emptyList()

  override fun getModuleFileFilterHonorExclusionPatterns(module: JpsModule): FileFilter = FileFilters.EVERYTHING
}