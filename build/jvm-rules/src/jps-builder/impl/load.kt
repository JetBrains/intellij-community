// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "HardCodedStringLiteral", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetIndex
import org.jetbrains.jps.builders.BuildTargetRegistry.ModuleTargetSelector
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.impl.BuildTargetChunk
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.BuildDataManager
import org.jetbrains.jps.incremental.storage.BuildTargetsState
import org.jetbrains.jps.indices.IgnoredFileIndex
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

private class BazelBuildDataPaths(private val dir: Path) : BuildDataPaths {
  override fun getDataStorageDir() = dir

  override fun getTargetsDataRoot(): Path = dir

  override fun getTargetTypeDataRootDir(targetType: BuildTargetType<*>): Path = dir.resolve(targetType.typeId)

  override fun getTargetDataRootDir(target: BuildTarget<*>): Path = dir.resolve(target.targetType.typeId)

  override fun getTargetDataRoot(targetType: BuildTargetType<*>, targetId: String): Path = dir
}

internal fun createDataManager(
  dataStorageRoot: Path,
  relativizer: PathRelativizerService,
  buildDataProvider: BazelBuildDataProvider,
  requestLog: RequestLog,
): BuildDataManager {
  val containerFactory = BazelPersistentMapletFactory(dataStorageRoot.resolve("mappings-graph"))
  return BuildDataManager(
    dataPaths = BazelBuildDataPaths(dataStorageRoot),
    targetsState = BuildTargetsState(BazelBuildTargetStateManager),
    relativizer = relativizer,
    dataManager = buildDataProvider,
    containerFactory = containerFactory,
  )
}

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

internal class BazelBuildTargetIndex(@JvmField val moduleTarget: ModuleBuildTarget) : BuildTargetIndex {
  private var targetChunks = java.util.List.of(BuildTargetChunk(java.util.Set.of(moduleTarget)))
  private var targets = java.util.List.of(moduleTarget)

  override fun getSortedTargetChunks(context: CompileContext): List<BuildTargetChunk> = targetChunks

  override fun isDummy(target: BuildTarget<*>) = false

  @Suppress("OVERRIDE_DEPRECATION", "removal")
  override fun getDependenciesRecursively(target: BuildTarget<*>, context: CompileContext): Set<BuildTarget<*>> = java.util.Set.of()

  override fun getDependencies(target: BuildTarget<*>, context: CompileContext): Collection<BuildTarget<*>> = java.util.List.of()

  override fun getModuleBasedTargets(module: JpsModule, selector: ModuleTargetSelector): List<ModuleBuildTarget> = targets

  @Suppress("UNCHECKED_CAST")
  override fun <T : BuildTarget<*>> getAllTargets(type: BuildTargetType<T>) = targets as List<T>

  override fun getAllTargets(): List<ModuleBuildTarget> = targets
}