// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.openapi.util.Key
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
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.GlobalContextKey
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.dependencies.LibraryDef
import org.jetbrains.jps.incremental.dependencies.LibraryDependenciesUpdater
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.io.FileFilter
import java.nio.file.Path

private val LIBRARIES_STATE_UPDATER_KEY = GlobalContextKey.create<LibraryDependenciesUpdater>("_libraries_state_updater_")
private val ALL_AFFECTED_NODE_SOURCES_KEY = Key.create<MutableSet<NodeSource>>("_all_compiled_node_sources_")

private fun fileToNodeSource(file: Path, relativizer: PathTypeAwareRelativizer): NodeSource {
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
  tracer.span("check lib deps") { span ->
    var libUpdater = context.getUserData(LIBRARIES_STATE_UPDATER_KEY)
    if (libUpdater == null) {
      libUpdater = LibraryDependenciesUpdater(dataProvider.libRootManager)
      context.putUserData(LIBRARIES_STATE_UPDATER_KEY, libUpdater)
    }

    val incremental = libUpdater.update(context, chunk, target, span)
    if (!incremental) {
      throw RebuildRequestedException(RuntimeException("incremental lib deps update failed"))
    }
  }

  val toCompile = hashSet<NodeSource>()
  val relativizer = dataProvider.relativizer
  dirtyFilesHolder.processFilesToRecompile {
    toCompile.add(fileToNodeSource(it, relativizer))
    true
  }

  val deletedSources = getRemovedPaths(chunk, dirtyFilesHolder, relativizer)
  if (toCompile.isEmpty() && deletedSources.isEmpty()) {
    return
  }

  tracer.span("update dependency graph") { span ->
    val delta = context.projectDescriptor.dataManager.dependencyGraph!!.graph.createDelta(
      /* sourcesToProcess = */ toCompile,
      /* deletedSources = */ deletedSources,
      /* isSourceOnly = */ true,
    )
    updateDependencyGraph(
      context = context,
      delta = delta,
      chunk = chunk,
      markDirtyRound = CompilationRound.CURRENT,
      skipMarkDirtyFilter = null,
      relativizer = relativizer,
      span = span,
    )
  }
}

private fun getRemovedPaths(
  chunk: ModuleChunk,
  dirtyFilesHolder: BazelDirtyFileHolder,
  relativizer: PathTypeAwareRelativizer,
): List<NodeSource> {
  if (!dirtyFilesHolder.hasRemovedFiles()) {
    return emptyList()
  }

  val removed = mutableListOf<NodeSource>()
  for (target in chunk.targets) {
    for (file in dirtyFilesHolder.getRemoved(target)) {
      removed.add(fileToNodeSource(file, relativizer))
    }
  }
  return removed
}

/**
 * @param context        compilation context
 * @param delta          registered delta files in this round
 * @param markDirtyRound compilation round at which dirty files should be visible to builders
 * @return true if additional compilation pass is required, false otherwise
 */
@Suppress("SameParameterValue")
private fun updateDependencyGraph(
  context: BazelCompileContext,
  delta: Delta?,
  chunk: ModuleChunk,
  markDirtyRound: CompilationRound,
  skipMarkDirtyFilter: FileFilter?,
  span: Span,
  relativizer: PathTypeAwareRelativizer,
): Boolean {
  val errorsDetected = Utils.errorsDetected(context)
  val performIntegrate = !errorsDetected
  var additionalPassRequired = false
  val dataManager = context.projectDescriptor.dataManager
  val graphConfig = dataManager.dependencyGraph!!
  val dependencyGraph = graphConfig.graph

  val isRebuild = context.scope.isRebuild
  val params = DifferentiateParametersBuilder.create(chunk.getPresentableShortName())
    .compiledWithErrors(errorsDetected)
    .calculateAffected(!isRebuild && context.shouldDifferentiate(chunk))
    .processConstantsIncrementally(dataManager.isProcessConstantsIncrementally)
    .withAffectionFilter { !LibraryDef.isLibraryPath(it) }
  val differentiateParams = params.get()
  val diffResult = dependencyGraph.differentiate(delta, differentiateParams)

  val compilingIncrementally = context.scope.isIncrementalCompilation

  if (compilingIncrementally && !errorsDetected && differentiateParams.isCalculateAffected && diffResult.isIncremental) {
    // Some compilers (and compiler plugins) may produce different outputs for the same set of inputs.
    // This might cause corresponding graph Nodes to be considered as always 'changed'.
    // In some scenarios, this may lead to endless build loops.
    // This fallback logic detects such loops and recompiles the whole module chunk instead.
    val affectedForChunk = diffResult.affectedSources.filterTo(hashSet()) { differentiateParams.belongsToCurrentCompilationChunk().test(it) }
    if (!affectedForChunk.isEmpty() && !getOrCreate(context, ALL_AFFECTED_NODE_SOURCES_KEY) { hashSet() }.addAll(affectedForChunk)) {
      // All affected files in this round have already been affected in previous rounds.
      // This might indicate a build cycle => recompiling the whole chunk.
      span.addEvent("build cycle detected for " + chunk.name + "; recompiling whole module chunk")
      // turn on non-incremental mode for all targets from the current chunk =>
      // next time the whole chunk is recompiled and affected files won't be calculated anymore
      for (target in chunk.targets) {
        context.markNonIncremental(target)
      }
      FSOperations.markDirty(context, markDirtyRound, chunk, null)
      return true
    }
  }

  if (diffResult.isIncremental) {
    dataManager.relativizer
    assert(skipMarkDirtyFilter == null)
    val affectedFiles = diffResult.affectedSources
      .mapTo(hashSet()) { relativizer.toAbsoluteFile(it.toString(), RelativePathType.SOURCE) }

    if (differentiateParams.isCalculateAffected) {
      span.addEvent("affected file count", Attributes.of(AttributeKey.longKey("count"), affectedFiles.size.toLong()))
    }

    if (!affectedFiles.isEmpty()) {
      if (span.isRecording) {
        span.addEvent("affected files", Attributes.of(AttributeKey.stringArrayKey("count"), affectedFiles.map { it.toString() }))
      }

      var targetsToMark: MutableSet<ModuleBuildTarget>? = null
      val moduleIndex = JpsJavaExtensionService.getInstance().getJavaModuleIndex(context.projectDescriptor.project)
      for (file in affectedFiles) {
        if (file.fileName.toString() == "module-info.java") {
          val rootDescriptor = (context.getProjectDescriptor().buildRootIndex as BazelBuildRootIndex).fileToDescriptors.get(file)
          if (rootDescriptor != null) {
            val target = rootDescriptor.getTarget()
            val targetModuleInfo = moduleIndex.getModuleInfoFile(target.module, target.isTests)
            if (targetModuleInfo?.toPath() == file) {
              if (targetsToMark == null) {
                targetsToMark = hashSet<ModuleBuildTarget>()
              }
              targetsToMark.add(target)
            }
          }
        }
        else {
          FSOperations.markDirtyIfNotDeleted(context, markDirtyRound, file)
        }
      }

      var isCurrentChunkAffected = false
      if (targetsToMark != null) {
        for (target in targetsToMark) {
          if (chunk.targets.contains(target)) {
            isCurrentChunkAffected = true
          }
          else {
            FSOperations.markDirty(context, markDirtyRound, target, null)
          }
        }
        if (isCurrentChunkAffected) {
          if (compilingIncrementally) {
            // turn on non-incremental mode for targets from the current chunk if at least one of them was affected
            for (target in chunk.targets) {
              context.markNonIncremental(target)
            }
          }
          FSOperations.markDirty(context, markDirtyRound, chunk, null)
        }
      }
      additionalPassRequired = compilingIncrementally && (isCurrentChunkAffected)
    }
  }
  else {
    // non-incremental mode
    throw RebuildRequestedException(RuntimeException("diffResult is non incremental: $diffResult"))
  }

  if (!additionalPassRequired) {
    // cleanup
    ALL_AFFECTED_NODE_SOURCES_KEY.set(context, null)
  }

  if (performIntegrate) {
    dependencyGraph.integrate(diffResult)
  }

  return additionalPassRequired
}

private inline fun <T : Any> getOrCreate(context: CompileContext, @Suppress("SameParameterValue") dataKey: Key<T>, factory: () -> T): T {
  var result = context.getUserData(dataKey)
  if (result == null) {
    result = factory()
    context.putUserData(dataKey, result)
  }
  return result
}