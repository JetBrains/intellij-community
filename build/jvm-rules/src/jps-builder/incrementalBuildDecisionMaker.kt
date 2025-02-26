// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.jps

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.jps.impl.RequestLog
import org.jetbrains.bazel.jvm.jps.state.DependencyStateResult
import org.jetbrains.bazel.jvm.jps.state.PathRelativizer
import org.jetbrains.bazel.jvm.jps.state.SourceFileStateResult
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.loadBuildState
import org.jetbrains.bazel.jvm.jps.state.loadDependencyState
import org.jetbrains.bazel.jvm.span
import java.nio.file.Files
import java.nio.file.Path

// if more than 50% files were changed, perform a full rebuild
private const val thresholdPercentage = 0.5

// if output jar doesn't exist, make sure that we do not to use existing cache -
// set `isRebuild` to true and clear caches in this case
internal fun validateFileExistence(outJar: Path, abiJar: Path?, cacheDir: Path): String? {
  return when {
    !Files.isDirectory(cacheDir) -> "cache dir doesn't exist"
    Files.notExists(cacheDir.resolve(outJar.fileName)) -> "cached output jar doesn't exist"
    abiJar != null && Files.notExists(cacheDir.resolve(abiJar.fileName)) -> {
      "cached output JAR exists but not ABI JAR - something wrong, or we enabled ABI JARs"
    }
    else -> null
  }
}

internal data class BuildStateResult(
  @JvmField val sourceFileState: SourceFileStateResult?,
  @JvmField val dependencyState: DependencyStateResult?,

  @JvmField val rebuildRequested: String?,
)

internal suspend fun computeBuildState(
  buildStateFile: Path,
  depStateStorageFile: Path,
  parentSpan: Span,
  sourceRelativizer: PathRelativizer,
  allocator: RootAllocator,
  sourceFileToDigest: Map<Path, ByteArray>,
  targetDigests: TargetConfigurationDigestContainer,
  forceIncremental: Boolean,
  log: RequestLog,
  tracer: Tracer,
  classPath: Array<Path>,
): BuildStateResult {
  val sourceFileStateResult = loadBuildState(
    buildStateFile = buildStateFile,
    relativizer = sourceRelativizer,
    allocator = allocator,
    sourceFileToDigest = sourceFileToDigest,
    targetDigests = targetDigests,
    parentSpan = parentSpan,
  ) ?: return BuildStateResult(null, null, "no source file state")

  sourceFileStateResult.rebuildRequested?.let {
    return BuildStateResult(null, null, it)
  }

  if (!forceIncremental) {
    val reason = checkIsFullRebuildRequired(
      buildState = sourceFileStateResult,
      log = log,
      sourceFileCount = sourceFileToDigest.size,
      parentSpan = parentSpan,
    )
    if (reason != null) {
      return BuildStateResult(null, null, reason)
    }
  }

  return tracer.span("load and check dependency state") { span ->
    val result = loadDependencyState(
      storageFile = depStateStorageFile,
      allocator = allocator,
      relativizer = sourceRelativizer,
      span = span,
    )
    if (result != null && classPath.size != result.size) {
      BuildStateResult(null, null, "class path size (${classPath.size}) and dependency file size (${result.size}) must be equal")
    }
    else {
      BuildStateResult(
        sourceFileState = sourceFileStateResult,
        dependencyState = DependencyStateResult(result ?: hashMap(classPath.size)),
        rebuildRequested = null,
      )
    }
  }
}

private fun checkIsFullRebuildRequired(
  buildState: SourceFileStateResult,
  log: RequestLog,
  sourceFileCount: Int,
  parentSpan: Span,
): String? {
  val incrementalEffort = buildState.changedOrAddedFiles.size + buildState.deletedFiles.size
  val rebuildThreshold = sourceFileCount * thresholdPercentage
  val forceFullRebuild = incrementalEffort >= rebuildThreshold
  val reason = "incrementalEffort=$incrementalEffort, rebuildThreshold=$rebuildThreshold, isFullRebuild=$forceFullRebuild"
  log.out.appendLine(reason)

  if (parentSpan.isRecording) {
    // do not use toRelative - print as is to show the actual path
    parentSpan.setAttribute(AttributeKey.stringArrayKey("changedFiles"), buildState.changedOrAddedFiles.map { it.toString() })
    parentSpan.setAttribute(AttributeKey.stringArrayKey("deletedFiles"), buildState.deletedFiles.map { it.toString() })

    parentSpan.setAttribute("incrementalEffort", incrementalEffort.toLong())
    parentSpan.setAttribute("rebuildThreshold", rebuildThreshold)
    parentSpan.setAttribute("forceFullRebuild", forceFullRebuild)
  }
  return if (forceFullRebuild) reason else null
}
