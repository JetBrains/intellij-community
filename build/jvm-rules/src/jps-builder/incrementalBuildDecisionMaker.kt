package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.jps.impl.RequestLog
import org.jetbrains.bazel.jvm.jps.state.DependencyStateResult
import org.jetbrains.bazel.jvm.jps.state.LoadSourceFileStateResult
import org.jetbrains.bazel.jvm.jps.state.PathRelativizer
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.loadBuildState
import org.jetbrains.bazel.jvm.jps.state.loadDependencyState
import org.jetbrains.bazel.jvm.span
import java.nio.file.Files
import java.nio.file.Path

// if more than 50% files were changed, perform a full rebuild
private const val thresholdPercentage = 0.5

internal fun validateFileExistence(outJar: Path, abiJar: Path?, dataDir: Path): Boolean {
  when {
    Files.notExists(outJar) -> {
      FileUtilRt.deleteRecursively(dataDir)
      if (abiJar != null) {
        Files.deleteIfExists(abiJar)
      }
      return true
    }

    !Files.isDirectory(dataDir) -> {
      Files.deleteIfExists(outJar)
      if (abiJar != null) {
        Files.deleteIfExists(abiJar)
      }
      return true
    }

    abiJar != null && Files.notExists(abiJar) -> {
      // output JAR exists but not abi? something wrong, or we enabled ABI jars, let's rebuild
      FileUtilRt.deleteRecursively(dataDir)
      Files.deleteIfExists(outJar)
      return true
    }

    else -> {
      return false
    }
  }
}

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
): Pair<LoadSourceFileStateResult, DependencyStateResult>? {
  val buildState = loadBuildState(
    buildStateFile = buildStateFile,
    relativizer = sourceRelativizer,
    allocator = allocator,
    sourceFileToDigest = sourceFileToDigest,
    targetDigests = targetDigests,
    parentSpan = parentSpan,
  ) ?: return null

  buildState.rebuildRequested?.let {
    parentSpan.setAttribute("rebuildRequested", it)
    return null
  }

  if (!forceIncremental && checkIsFullRebuildRequired(
      buildState = buildState,
      log = log,
      sourceFileCount = sourceFileToDigest.size,
      parentSpan = parentSpan,
    )) {
    return null
  }

  val depState = tracer.span("load and check dependency state") { span ->
    val result = loadDependencyState(
      storageFile = depStateStorageFile,
      allocator = allocator,
      relativizer = sourceRelativizer,
      span = span,
    )
    if (result != null && classPath.size != result.size) {
      span.addEvent("class path size (${classPath.size}) and dependency file size (${result.size}) must be equal")
      null
    }
    else {
      result ?: hashMap(classPath.size)
    }
  } ?: return null

  return buildState to DependencyStateResult(depState)
}

private fun checkIsFullRebuildRequired(
  buildState: LoadSourceFileStateResult,
  log: RequestLog,
  sourceFileCount: Int,
  parentSpan: Span,
): Boolean {
  val incrementalEffort = buildState.changedOrAddedFiles.size + buildState.deletedFiles.size
  val rebuildThreshold = sourceFileCount * thresholdPercentage
  val forceFullRebuild = incrementalEffort >= rebuildThreshold
  log.out.appendLine("incrementalEffort=$incrementalEffort, rebuildThreshold=$rebuildThreshold, isFullRebuild=$forceFullRebuild")

  if (parentSpan.isRecording) {
    // do not use toRelative - print as is to show the actual path
    parentSpan.setAttribute(AttributeKey.stringArrayKey("changedFiles"), buildState.changedOrAddedFiles.map { it.toString() })
    parentSpan.setAttribute(AttributeKey.stringArrayKey("deletedFiles"), buildState.deletedFiles.map { it.toString() })

    parentSpan.setAttribute("incrementalEffort", incrementalEffort.toLong())
    parentSpan.setAttribute("rebuildThreshold", rebuildThreshold)
    parentSpan.setAttribute("forceFullRebuild", forceFullRebuild)
  }
  return forceFullRebuild
}
