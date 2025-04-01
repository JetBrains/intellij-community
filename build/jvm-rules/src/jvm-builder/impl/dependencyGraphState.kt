// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.impl

import androidx.collection.MutableScatterSet
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import org.jetbrains.bazel.jvm.worker.dependencies.DependencyAnalyzer
import org.jetbrains.bazel.jvm.worker.dependencies.checkDependencies
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.util.emptySet
import org.jetbrains.bazel.jvm.util.slowEqualsAwareHashStrategy
import org.jetbrains.bazel.jvm.worker.core.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.worker.core.BazelBuildRootIndex
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelDirtyFileHolder
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder
import org.jetbrains.jps.dependency.impl.PathSource
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.dependencies.LibraryDef
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
  dependencyAnalyzer: DependencyAnalyzer,
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
      dependencyAnalyzer = dependencyAnalyzer,
      span = span,
    )
  }

  val toCompile = ObjectLinkedOpenCustomHashSet<NodeSource>(slowEqualsAwareHashStrategy())
  dirtyFilesHolder.processFilesToRecompile { files ->
    toCompile.ensureCapacity(toCompile.size + files.size)
    for (file in files) {
      toCompile.add(fileToNodeSource(file, relativizer))
    }
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
): Set<NodeSource> {
  val removed = dirtyFilesHolder.getRemoved(target)
  if (removed.isEmpty()) {
    return emptySet()
  }
  else if (removed.size == 1) {
    return java.util.Set.of(fileToNodeSource(removed.single(), relativizer))
  }

  val result = ObjectLinkedOpenCustomHashSet<NodeSource>(removed.size, slowEqualsAwareHashStrategy())
  for (file in removed) {
    result.add(fileToNodeSource(file, relativizer))
  }
  return result
}

/**
 * @param context        compilation context
 * @param delta          registered delta files in this round
 * @return true if additional compilation pass is required, false otherwise
 */
private fun updateDependencyGraph(
  context: BazelCompileContext,
  delta: Delta,
  chunk: ModuleChunk,
  target: BazelModuleBuildTarget,
  span: Span,
  dataProvider: BazelBuildDataProvider,
): Boolean {
  val errorsDetected = Utils.errorsDetected(context)
  val dataManager = context.projectDescriptor.dataManager
  val graphConfig = dataManager.getDependencyGraph()
  val dependencyGraph = graphConfig.graph as DependencyGraphImpl

  val isRebuild = context.scope.isRebuild
  val differentiateParams = DifferentiateParametersBuilder.create(chunk.presentableShortName)
    .compiledWithErrors(errorsDetected)
    .calculateAffected(!isRebuild && context.shouldDifferentiate(chunk))
    .processConstantsIncrementally(dataManager.isProcessConstantsIncrementally)
    .withAffectionFilter { !LibraryDef.isLibraryPath(it) }
    .get()
  val diffResult = dependencyGraph.differentiate(
    delta = delta,
    allProcessedSources = delta.deletedSources,
    nodesAfter = emptySet(),
    params = differentiateParams,
    getBeforeNodes = { dependencyGraph.getNodes(it) },
  )

  if (!diffResult.isIncremental) {
    // non-incremental mode
    throw RebuildRequestedException(RuntimeException("diffResult is non incremental: $diffResult"))
  }

  @Suppress("UNCHECKED_CAST")
  val affectedSources = diffResult.affectedSources as Collection<NodeSource>
  val relativizer = dataProvider.relativizer
  val affectedFiles = MutableScatterSet<Path>(affectedSources.size)
  for (source in affectedSources) {
    affectedFiles.add(relativizer.toAbsoluteFile(source.toString(), RelativePathType.SOURCE))
  }

  if (differentiateParams.isCalculateAffected) {
    span.addEvent("affected file count", Attributes.of(AttributeKey.longKey("count"), affectedFiles.size.toLong()))
  }

  if (!affectedFiles.isEmpty()) {
    if (span.isRecording) {
      span.addEvent("affected files", Attributes.of(AttributeKey.stringArrayKey("count"), affectedSources.map { it.toString() }))
    }

    markAffectedFilesDirty(
      context = context,
      dataProvider = dataProvider,
      target = target,
      affectedFiles = sequence {
        affectedFiles.forEach { yield(it) }
      },
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