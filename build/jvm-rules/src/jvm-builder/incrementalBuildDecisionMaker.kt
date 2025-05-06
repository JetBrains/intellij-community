// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.worker

import androidx.collection.ScatterMap
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.worker.state.PathRelativizer
import org.jetbrains.bazel.jvm.worker.state.SourceFileStateResult
import org.jetbrains.bazel.jvm.worker.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.worker.state.loadBuildState
import org.jetbrains.bazel.jvm.span
import java.nio.file.Files
import java.nio.file.Path

// if more than 50% files were changed, perform a full rebuild
private const val thresholdPercentage = 0.5

// if an output jar doesn't exist, make sure that we do not to use existing cache -
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
  @JvmField val rebuildRequested: String?,
)

internal suspend fun computeBuildState(
  buildStateFile: Path,
  parentSpan: Span,
  sourceRelativizer: PathRelativizer,
  allocator: RootAllocator,
  sourceFileToDigest: ScatterMap<Path, ByteArray>,
  targetDigests: TargetConfigurationDigestContainer,
  forceIncremental: Boolean,
  tracer: Tracer,
): BuildStateResult {
  val sourceFileStateResult = loadBuildState(
    buildStateFile = buildStateFile,
    relativizer = sourceRelativizer,
    allocator = allocator,
    sourceFileToDigest = sourceFileToDigest,
    targetDigests = targetDigests,
    parentSpan = parentSpan,
  ) ?: return createCleanBuildStateResult("no source file state")

  sourceFileStateResult.rebuildRequested?.let {
    return createCleanBuildStateResult(it)
  }

  if (!forceIncremental) {
    val reason = checkIsFullRebuildRequired(
      buildState = sourceFileStateResult,
      sourceFileCount = sourceFileToDigest.size,
      parentSpan = parentSpan,
    )
    if (reason != null) {
      return createCleanBuildStateResult(reason)
    }
  }

  return tracer.span("load and check dependency state") { span ->
    BuildStateResult(sourceFileState = sourceFileStateResult, rebuildRequested = null)
  }
}

internal fun createCleanBuildStateResult(rebuildRequested: String?): BuildStateResult {
  return BuildStateResult(sourceFileState = null, rebuildRequested = rebuildRequested)
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
