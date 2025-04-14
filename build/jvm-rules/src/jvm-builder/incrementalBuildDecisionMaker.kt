// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.worker

import androidx.collection.ObjectList
import androidx.collection.ScatterMap
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.worker.state.DependencyStateResult
import org.jetbrains.bazel.jvm.worker.state.PathRelativizer
import org.jetbrains.bazel.jvm.worker.state.SourceFileStateResult
import org.jetbrains.bazel.jvm.worker.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.worker.state.createNewDependencyList
import org.jetbrains.bazel.jvm.worker.state.loadBuildState
import org.jetbrains.bazel.jvm.worker.state.loadDependencyState
import org.jetbrains.bazel.jvm.span
import java.nio.file.Files
import java.nio.file.Path

// if more than 50% files were changed, perform a full rebuild
private const val thresholdPercentage = 0.5

// if output jar doesn't exist, make sure that we do not to use existing cache -
// set `isRebuild` to true and clear caches in this case
internal fun validateFileExistence(outputs: OutputFiles, cacheDir: Path): String? {
  return when {
    !Files.isDirectory(cacheDir) -> "cache dir doesn't exist"
    Files.notExists(outputs.cachedJar) -> "cached output jar doesn't exist"
    outputs.abiJar != null && Files.notExists(outputs.cachedAbiJar) -> "cached ABI jar doesn't exist"
    else -> null
  }
}

internal data class BuildStateResult(
  @JvmField val sourceFileState: SourceFileStateResult?,
  @JvmField val dependencyState: DependencyStateResult,

  @JvmField val rebuildRequested: String?,
)

internal suspend fun computeBuildState(
  buildStateFile: Path,
  depStateStorageFile: Path,
  parentSpan: Span,
  sourceRelativizer: PathRelativizer,
  allocator: RootAllocator,
  sourceFileToDigest: ScatterMap<Path, ByteArray>,
  targetDigests: TargetConfigurationDigestContainer,
  forceIncremental: Boolean,
  tracer: Tracer,
  trackableDependencyFiles: ObjectList<Path>,
  dependencyFileToDigest: ScatterMap<Path, ByteArray>,
): BuildStateResult {
  val sourceFileStateResult = loadBuildState(
    buildStateFile = buildStateFile,
    relativizer = sourceRelativizer,
    allocator = allocator,
    sourceFileToDigest = sourceFileToDigest,
    targetDigests = targetDigests,
    parentSpan = parentSpan,
  ) ?: return createCleanBuildStateResult(trackableDependencyFiles, dependencyFileToDigest, "no source file state")

  sourceFileStateResult.rebuildRequested?.let {
    return createCleanBuildStateResult(trackableDependencyFiles, dependencyFileToDigest, it)
  }

  if (!forceIncremental) {
    val reason = checkIsFullRebuildRequired(
      buildState = sourceFileStateResult,
      sourceFileCount = sourceFileToDigest.size,
      parentSpan = parentSpan,
    )
    if (reason != null) {
      return createCleanBuildStateResult(trackableDependencyFiles, dependencyFileToDigest, reason)
    }
  }

  return tracer.span("load and check dependency state") { span ->
    val result = loadDependencyState(
      dependencyFileToDigest = dependencyFileToDigest,
      trackableDependencyFiles = trackableDependencyFiles,
      storageFile = depStateStorageFile,
      allocator = allocator,
      relativizer = sourceRelativizer,
      span = span,
    )
    BuildStateResult(sourceFileState = sourceFileStateResult, dependencyState = result, rebuildRequested = null)
  }
}

internal fun createCleanBuildStateResult(
  trackableDependencyFiles: ObjectList<Path>,
  dependencyFileToDigest: ScatterMap<Path, ByteArray>,
  rebuildRequested: String?,
): BuildStateResult {
  return BuildStateResult(
    sourceFileState = null,
    dependencyState = createNewDependencyList(trackableDependencyFiles, dependencyFileToDigest),
    rebuildRequested = rebuildRequested,
  )
}

private fun checkIsFullRebuildRequired(
  buildState: SourceFileStateResult,
  sourceFileCount: Int,
  parentSpan: Span,
): String? {
  val incrementalEffort = buildState.changedOrAddedFiles.size + buildState.deletedFiles.size
  val rebuildThreshold = sourceFileCount * thresholdPercentage
  val forceFullRebuild = incrementalEffort >= rebuildThreshold
  if (parentSpan.isRecording) {
    // do not use toRelative - print as is to show the actual path
    parentSpan.setAttribute(AttributeKey.stringArrayKey("changedFiles"), buildState.changedOrAddedFiles.map { it.toString() })
    parentSpan.setAttribute(AttributeKey.stringArrayKey("deletedFiles"), buildState.deletedFiles.map { it.toString() })

    parentSpan.setAttribute("incrementalEffort", incrementalEffort.toLong())
    parentSpan.setAttribute("rebuildThreshold", rebuildThreshold)
    parentSpan.setAttribute("forceFullRebuild", forceFullRebuild)
  }
  return if (forceFullRebuild) "incrementalEffort=$incrementalEffort, rebuildThreshold=$rebuildThreshold, isFullRebuild=true" else null
}
