// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.jetbrains.bazel.jvm.emptyList
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.bazel.jvm.span
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder
import org.jetbrains.jps.dependency.impl.PathSource
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.dependencies.LibraryDef
import org.jetbrains.jps.incremental.dependencies.checkDependencies
import org.jetbrains.jps.incremental.fs.BuildFSState.CURRENT_ROUND_DELTA_KEY
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Path

internal fun fileToNodeSource(file: Path, relativizer: PathTypeAwareRelativizer): PathSource {
  return PathSource(relativizer.toRelative(file, RelativePathType.SOURCE))
}

internal suspend fun markDirtyDependenciesForInitialRound(
  dataProvider: BazelBuildDataProvider,
  target: BazelModuleBuildTarget,
  context: BazelCompileContext,
  dirtyFilesHolder: BazelDirtyFileHolder,
  chunk: ModuleChunk,
  tracer: Tracer,
) {
  val relativizer = dataProvider.relativizer

  tracer.span("check lib deps") { span ->
    checkDependencies(
      context = context,
      chunk = chunk,
      target = target,
      relativizer = relativizer,
      dataProvider = dataProvider,
      tracer = tracer,
      span = span,
    )
  }

  val toCompile = hashSet<NodeSource>()
  dirtyFilesHolder.processFilesToRecompile {
    toCompile.add(fileToNodeSource(it, relativizer))
    true
  }

  val deletedSources = getRemovedPaths(target, dirtyFilesHolder, relativizer)
  if (toCompile.isEmpty() && deletedSources.isEmpty()) {
    return
  }

  tracer.span("update dependency graph") { span ->
    val delta = context.projectDescriptor.dataManager.getDependencyGraph().graph.createDelta(
      /* sourcesToProcess = */ toCompile,
      /* deletedSources = */ deletedSources,
      /* isSourceOnly = */ true,
    )
    updateDependencyGraph(
      context = context,
      delta = delta,
      chunk = chunk,
      dataProvider = dataProvider,
      target = target,
      span = span,
    )
  }
}

private fun getRemovedPaths(
  target: BazelModuleBuildTarget,
  dirtyFilesHolder: BazelDirtyFileHolder,
  relativizer: PathTypeAwareRelativizer,
): List<NodeSource> {
  if (!dirtyFilesHolder.hasRemovedFiles()) {
    return emptyList()
  }
  return dirtyFilesHolder.getRemoved(target).map { fileToNodeSource(it, relativizer) }
}

/**
 * @param context        compilation context
 * @param delta          registered delta files in this round
 * @return true if additional compilation pass is required, false otherwise
 */
private fun updateDependencyGraph(
  context: BazelCompileContext,
  delta: Delta?,
  chunk: ModuleChunk,
  target: BazelModuleBuildTarget,
  span: Span,
  dataProvider: BazelBuildDataProvider,
): Boolean {
  val errorsDetected = Utils.errorsDetected(context)
  val dataManager = context.projectDescriptor.dataManager
  val graphConfig = dataManager.getDependencyGraph()
  val dependencyGraph = graphConfig.graph

  val isRebuild = context.scope.isRebuild
  val params = DifferentiateParametersBuilder.create(chunk.presentableShortName)
    .compiledWithErrors(errorsDetected)
    .calculateAffected(!isRebuild && context.shouldDifferentiate(chunk))
    .processConstantsIncrementally(dataManager.isProcessConstantsIncrementally)
    .withAffectionFilter { !LibraryDef.isLibraryPath(it) }
  val differentiateParams = params.get()
  val diffResult = dependencyGraph.differentiate(delta, differentiateParams)

  if (!diffResult.isIncremental) {
    // non-incremental mode
    throw RebuildRequestedException(RuntimeException("diffResult is non incremental: $diffResult"))
  }

  val relativizer = dataProvider.relativizer
  val affectedFiles = diffResult.affectedSources.mapTo(hashSet()) { relativizer.toAbsoluteFile(it.toString(), RelativePathType.SOURCE) }

  if (differentiateParams.isCalculateAffected) {
    span.addEvent("affected file count", Attributes.of(AttributeKey.longKey("count"), affectedFiles.size.toLong()))
  }

  if (!affectedFiles.isEmpty()) {
    if (span.isRecording) {
      span.addEvent("affected files", Attributes.of(AttributeKey.stringArrayKey("count"), affectedFiles.map { it.toString() }))
    }

    markAffectedFilesDirty(
      context = context,
      dataProvider = dataProvider,
      target = target,
      affectedFiles = affectedFiles.asSequence(),
    )
  }

  if (!errorsDetected) {
    dependencyGraph.integrate(diffResult)
  }

  return false
}

internal fun markAffectedFilesDirty(
  context: CompileContext,
  affectedFiles: Sequence<Path>,
  dataProvider: BazelBuildDataProvider,
  target: BazelModuleBuildTarget,
) {
  val projectDescriptor = context.projectDescriptor
  val buildRootIndex = projectDescriptor.buildRootIndex as BazelBuildRootIndex
  val stampStorage = dataProvider.stampStorage
  for (file in affectedFiles) {
    if (file.fileName.toString() == "module-info.java") {
      val moduleIndex = JpsJavaExtensionService.getInstance().getJavaModuleIndex(projectDescriptor.project)
      if (moduleIndex.getModuleInfoFile(target.module, target.isTests)?.toPath() == file) {
        throw RebuildRequestedException(RuntimeException("module-info.java is changed (file=$file"))
      }
    }
    else {
      val rootDescriptor = buildRootIndex.fileToDescriptors.get(file) ?: continue
      val marked = projectDescriptor.fsState.getDelta(target).markRecompileIfNotDeleted(rootDescriptor, file)
      if (marked) {
        stampStorage.markChanged(file)
        val roundDelta = context.getUserData(CURRENT_ROUND_DELTA_KEY)
        roundDelta?.markRecompile(rootDescriptor, file)
      }
    }
  }
}