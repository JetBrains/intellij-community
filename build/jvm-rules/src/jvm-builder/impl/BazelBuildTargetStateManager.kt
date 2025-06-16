// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NonExtendableApiUsage", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.worker.impl

import com.intellij.openapi.util.Pair
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.storage.BuildTargetConfiguration
import org.jetbrains.jps.incremental.storage.BuildTargetStateManager

internal object BazelBuildTargetStateManager : BuildTargetStateManager {
  override fun getBuildTargetId(target: BuildTarget<*>): Int = 1

  override fun getTargetConfiguration(target: BuildTarget<*>): BuildTargetConfiguration {
    throw UnsupportedOperationException()
  }

  override fun getStaleTargetIds(type: BuildTargetType<*>): List<Pair<String, Int>> = java.util.List.of()

  override fun cleanStaleTarget(type: BuildTargetType<*>, targetId: String) {
  }

  override fun getLastSuccessfulRebuildDuration(): Long {
    throw IllegalStateException()
  }

  override fun setLastSuccessfulRebuildDuration(duration: Long) {
    throw IllegalStateException()
  }

  override fun getAverageBuildTime(type: BuildTargetType<*>): Long {
    throw IllegalStateException()
  }

  override fun setAverageBuildTime(type: BuildTargetType<*>, time: Long) {
    throw IllegalStateException()
  }

  override fun save() {
  }

  override fun clean() {
    throw IllegalStateException()
  }

  override fun storeNonExistentOutputRoots(target: BuildTarget<*>, context: CompileContext) {
  }

  override fun isTargetDirty(target: BuildTarget<*>, projectDescriptor: ProjectDescriptor): Boolean = false

  override fun invalidate(target: BuildTarget<*>) {
    // we do not invalidate as JPS does - if target configuration is changed, we delete output before build
    throw UnsupportedOperationException()
  }
}