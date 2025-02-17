// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "PackageDirectoryMismatch", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.kotlin.jps.targets

import org.jetbrains.bazel.jvm.jps.impl.BazelBuildTargetIndex
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.kotlin.jps.build.KotlinChunk
import org.jetbrains.kotlin.jps.build.KotlinCompileContext

class KotlinTargetsIndex(
  val byJpsTarget: Map<ModuleBuildTarget, KotlinModuleBuildTarget<*>>,
  val chunks: List<KotlinChunk>,
  val chunksByJpsRepresentativeTarget: Map<ModuleBuildTarget, KotlinChunk>
)

internal class KotlinTargetsIndexBuilder internal constructor(
  private val uninitializedContext: KotlinCompileContext
) {
  fun build(): KotlinTargetsIndex {
    val target = (uninitializedContext.jpsContext.projectDescriptor.buildTargetIndex as BazelBuildTargetIndex).moduleTarget
    val moduleBuildTarget = KotlinJvmModuleBuildTarget(uninitializedContext, target)
    val kotlinChunk = KotlinChunk(uninitializedContext, java.util.List.of(moduleBuildTarget))
    moduleBuildTarget.chunk = kotlinChunk
    val chunks = listOf(kotlinChunk)
    KotlinChunk.calculateChunkDependencies(chunks, java.util.Map.of<ModuleBuildTarget, KotlinModuleBuildTarget<*>>() as MutableMap<ModuleBuildTarget, KotlinModuleBuildTarget<*>>)
    return KotlinTargetsIndex(
      byJpsTarget = java.util.Map.of(target, moduleBuildTarget),
      chunks = chunks,
      chunksByJpsRepresentativeTarget = java.util.Map.of(target, kotlinChunk)
    )
  }
}