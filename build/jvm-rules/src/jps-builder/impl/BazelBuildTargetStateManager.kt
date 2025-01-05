// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NonExtendableApiUsage", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.openapi.util.Pair
import org.jetbrains.bazel.jvm.jps.state.TargetStateContainer
import org.jetbrains.bazel.jvm.jps.state.TargetStateProperty
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.storage.BuildTargetConfiguration
import org.jetbrains.jps.incremental.storage.BuildTargetStateManager

internal class BazelBuildTargetStateManager(
  val state: TargetStateContainer,
) : BuildTargetStateManager {
  override fun getBuildTargetId(target: BuildTarget<*>): Int = 1

  override fun getTargetConfiguration(target: BuildTarget<*>): BuildTargetConfiguration {
    throw UnsupportedOperationException()
  }

  override fun getStaleTargetIds(type: BuildTargetType<*>): List<Pair<String, Int>> = java.util.List.of()

  override fun cleanStaleTarget(type: BuildTargetType<*>, targetId: String) {
  }

  override fun getLastSuccessfulRebuildDuration(): Long {
    return state.get(TargetStateProperty.LastSuccessfulRebuildDuration)
  }

  override fun setLastSuccessfulRebuildDuration(duration: Long) {
    state.set(TargetStateProperty.LastSuccessfulRebuildDuration, duration)
  }

  override fun getAverageBuildTime(type: BuildTargetType<*>): Long {
    return state.get(TargetStateProperty.AverageBuildTime)
  }

  override fun setAverageBuildTime(type: BuildTargetType<*>, time: Long) {
    state.set(TargetStateProperty.AverageBuildTime, time)
  }

  override fun save() {
  }

  override fun clean() {
    throw UnsupportedOperationException()
  }

  override fun storeNonExistentOutputRoots(target: BuildTarget<*>, context: CompileContext) {
  }

  override fun isTargetDirty(target: BuildTarget<*>, projectDescriptor: ProjectDescriptor): Boolean = false

  override fun invalidate(target: BuildTarget<*>) {
    // we do not invalidate as JPS does - if target configuration is changed, we delete output before build
    throw UnsupportedOperationException()
  }
}