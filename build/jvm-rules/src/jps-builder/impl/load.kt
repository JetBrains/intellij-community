@file:Suppress("UnstableApiUsage", "HardCodedStringLiteral", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import org.jetbrains.bazel.jvm.jps.state.loadTargetState
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetIndex
import org.jetbrains.jps.builders.BuildTargetRegistry.ModuleTargetSelector
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.builders.impl.BuildTargetChunk
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.BuildDataManager
import org.jetbrains.jps.incremental.storage.BuildDataVersionManager
import org.jetbrains.jps.incremental.storage.StorageManager
import org.jetbrains.jps.indices.IgnoredFileIndex
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

internal fun loadJpsProject(
  storageManager: StorageManager,
  dataStorageRoot: Path,
  fsState: BuildFSState,
  jpsModel: JpsModel,
  moduleTarget: BazelModuleBuildTarget,
  relativizer: PathRelativizerService,
): ProjectDescriptor {
  val dataPaths = BuildDataPathsImpl(dataStorageRoot)
  val dataManager = BuildDataManager.createSingleDb(
    /* dataPaths = */ dataPaths,
    /* targetStateManager = */ BazelBuildTargetStateManager(loadTargetState(storageManager)),
    /* relativizer = */ relativizer,
    /* versionManager = */ NoopBuildDataVersionManager,
    /* storageManager = */ storageManager,
  )
  return ProjectDescriptor(
    /* model = */ jpsModel,
    /* fsState = */ fsState,
    /* dataManager = */ dataManager,
    /* loggingManager = */ BuildLoggingManager.DEFAULT,
    /* moduleExcludeIndex = */ NoopModuleExcludeIndex,
    /* buildTargetIndex = */ BazelBuildTargetIndex(moduleTarget),
    /* buildRootIndex = */ BazelBuildRootIndex(moduleTarget),
    /* ignoredFileIndex = */ NoopIgnoredFileIndex,
  )
}

private object NoopIgnoredFileIndex : IgnoredFileIndex {
  override fun isIgnored(path: String) = false
}

private object NoopBuildDataVersionManager : BuildDataVersionManager {
  override fun versionDiffers() = false

  override fun saveVersion() {
  }
}

private class BazelBuildTargetIndex(moduleTarget: ModuleBuildTarget) : BuildTargetIndex {
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