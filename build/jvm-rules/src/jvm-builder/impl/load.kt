// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "HardCodedStringLiteral", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.impl

import org.jetbrains.bazel.jvm.worker.core.BazelBuildRootIndex
import org.jetbrains.bazel.jvm.worker.core.BazelBuildTargetIndex
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.storage.BuildDataManager
import org.jetbrains.jps.indices.IgnoredFileIndex
import org.jetbrains.jps.model.JpsModel

internal fun createJpsProjectDescriptor(
  dataManager: BuildDataManager,
  jpsModel: JpsModel,
  moduleTarget: BazelModuleBuildTarget,
): ProjectDescriptor {
  return ProjectDescriptor(
    /* model = */ jpsModel,
    // alwaysScanFS doesn't matter, we use our own version of `BuildOperations.ensureFSStateInitialized`,
    // see `JpsProjectBuilder.ensureFsStateInitialized`
    /* fsState = */ BuildFSState(/* alwaysScanFS = */ true),
    /* dataManager = */ dataManager,
    /* loggingManager = */ BuildLoggingManager.DEFAULT,
    /* moduleExcludeIndex = */ NoopModuleExcludeIndex,
    /* buildTargetIndex = */ BazelBuildTargetIndex(moduleTarget),
    /* buildRootIndex = */ BazelBuildRootIndex(moduleTarget),
    /* ignoredFileIndex = */ NoopIgnoredFileIndex,
  )
}

internal object NoopIgnoredFileIndex : IgnoredFileIndex {
  override fun isIgnored(path: String) = false
}

