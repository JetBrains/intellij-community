// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "NonExtendableApiUsage", "RemoveRedundantQualifierName", "ReplaceGetOrSet", "UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.impl

import com.dynatrace.hash4j.hashing.HashSink
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetRegistry
import org.jetbrains.jps.builders.TargetOutputIndex
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.indices.IgnoredFileIndex
import org.jetbrains.jps.indices.ModuleExcludeIndex
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.module.JpsModule
import java.io.File
import java.nio.file.Path

internal class BazelModuleBuildTarget(
  module: JpsModule,
  @JvmField val sources: List<Path>,
  @JvmField val javaFileCount: Int,
) : ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION) {
  // org.jetbrains.kotlin.incremental.IncrementalJvmCache allows `null`
  override fun getOutputDir(): File? = null

  override fun getOutputRoots(context: CompileContext): Collection<File> = throw IllegalStateException("")

  override fun isTests() = false

  override fun computeDependencies(targetRegistry: BuildTargetRegistry, outputIndex: TargetOutputIndex): Collection<BuildTarget<*>> {
    return java.util.List.of()
  }

  override fun computeRootDescriptors(
    model: JpsModel,
    index: ModuleExcludeIndex,
    ignoredFileIndex: IgnoredFileIndex,
    dataPaths: BuildDataPaths,
  ): List<JavaSourceRootDescriptor?> {
    return computeRootDescriptors()
  }

  fun computeRootDescriptors(): List<JavaSourceRootDescriptor> {
    val roots = ArrayList<JavaSourceRootDescriptor>(sources.size)
    for (sourceRoot in sources) {
      roots.add(JavaSourceRootDescriptor.createJavaSourceRootDescriptor(sourceRoot, this))
    }
    return roots
  }

  @ApiStatus.Internal
  override fun computeConfigurationDigest(projectDescriptor: ProjectDescriptor, hash: HashSink) {
    // handled by StorageInitializer and TargetDigest
  }
}