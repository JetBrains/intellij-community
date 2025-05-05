// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.kotlin

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.bazel.jvm.util.linkedSet
import org.jetbrains.bazel.jvm.worker.core.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.worker.core.BazelBuildRootIndex
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.worker.core.cleanOutputsCorrespondingToChangedFiles
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations.addCompletelyMarkedDirtyTarget
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.fs.FilesDelta
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder.TargetFiles
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal class BazelKotlinFsOperationsHelper(
  private val context: CompileContext,
) {
  var hasMarkedDirty: Boolean = false
    private set

  fun markChunk(context: CompileContext, excludeFiles: Set<File>, dataManager: BazelBuildDataProvider, target: ModuleBuildTarget) {
    var completelyMarkedDirty = true
    val stampStorage = if (dataManager.isCleanBuild) null else dataManager.stampStorage
    val projectDescriptor = context.projectDescriptor
    for (rootDescriptor in (projectDescriptor.buildRootIndex as BazelBuildRootIndex).descriptors) {
      val file = rootDescriptor.rootFile
      val filePath = file.toString()
      if ((!filePath.endsWith(".kt") && !filePath.endsWith(".kts")) || excludeFiles.contains(file.toFile())) {
        completelyMarkedDirty = false
        continue
      }

      hasMarkedDirty = true

      val roundDelta = context.getUserData(BuildFSState.NEXT_ROUND_DELTA_KEY)
      roundDelta?.markRecompile(rootDescriptor, file)

      val filesDelta = projectDescriptor.fsState.getDelta(target)
      filesDelta.lockData()
      try {
        val marked = filesDelta.markRecompile(rootDescriptor, file)
        if (marked) {
          stampStorage?.markChanged(file)
        }
      }
      finally {
        filesDelta.unlockData()
      }
    }

    if (completelyMarkedDirty) {
      addCompletelyMarkedDirtyTarget(context, target)
    }
  }

  /**
   * Marks given [files] as dirty for current round.
   */
  fun markFilesForCurrentRound(
    files: Collection<Path>,
    targetDirtyFiles: TargetFiles?,
    outputSink: OutputSink,
    parentSpan: Span,
    target: BazelModuleBuildTarget,
    dataManager: BazelBuildDataProvider,
  ) {
    val fileToDescriptors = (context.projectDescriptor.buildRootIndex as BazelBuildRootIndex).fileToDescriptors
    for (file in files) {
      targetDirtyFiles._markDirty(file.toFile(), fileToDescriptors.get(file) ?: continue)
    }

    markFiles(
      files = files.filterTo(linkedSet()) { Files.exists(it) },
      currentRound = true,
      dataManager = dataManager,
      target = target,
      span = parentSpan,
    )
    cleanOutputsCorrespondingToChangedFiles(files = files, dataManager = dataManager, outputSink = outputSink, parentSpan = parentSpan)
  }

  fun markFiles(
    files: Collection<Path>,
    currentRound: Boolean,
    target: BazelModuleBuildTarget,
    dataManager: BazelBuildDataProvider,
    span: Span,
  ) {
    if (files.isEmpty()) {
      return
    }

    val roundDelta: FilesDelta?
    val compilationRound = if (currentRound) {
      roundDelta = context.getUserData(BuildFSState.CURRENT_ROUND_DELTA_KEY)
      CompilationRound.CURRENT
    }
    else {
      roundDelta = context.getUserData(BuildFSState.NEXT_ROUND_DELTA_KEY)
      hasMarkedDirty = true
      CompilationRound.NEXT
    }

    val projectDescriptor = context.projectDescriptor
    val stampStorage = dataManager.stampStorage
    val fileToDescriptors = (projectDescriptor.buildRootIndex as BazelBuildRootIndex).fileToDescriptors

    val filesDelta = projectDescriptor.fsState.getDelta(target)
    filesDelta.lockData()
    try {
      for (fileToMark in files) {
        val rootDescriptor = fileToDescriptors.get(fileToMark) ?: continue
        roundDelta?.markRecompile(rootDescriptor, fileToMark)
        val marked = filesDelta.markRecompile(rootDescriptor, fileToMark)
        if (marked) {
          stampStorage.markChanged(fileToMark)
        }
      }
    }
    finally {
      filesDelta.unlockData()
    }

    if (span.isRecording) {
      span.addEvent("mark dirty", Attributes.of(
        AttributeKey.stringArrayKey("filesToMark"), files.map { it.toString() },
        AttributeKey.stringKey("compilationRound"), compilationRound.name,
      ))
    }
  }
}

internal fun markFilesForCurrentRound(
  context: CompileContext,
  files: Set<File>,
  targetDirtyFiles: TargetFiles?,
  span: Span,
  target: BazelModuleBuildTarget,
  dataManager: BazelBuildDataProvider,
) {
  if (files.isEmpty()) {
    return
  }

  val buildRootIndex = context.projectDescriptor.buildRootIndex as BazelBuildRootIndex
  val fileToDescriptors = buildRootIndex.fileToDescriptors
  for (file in files) {
    val root = fileToDescriptors.get(file.toPath()) ?: continue
    targetDirtyFiles?._markDirty(file, root)
  }

  val stampStorage = dataManager.stampStorage
  val roundDelta = context.getUserData(BuildFSState.CURRENT_ROUND_DELTA_KEY)
  val fileDelta = context.projectDescriptor.fsState.getDelta(target)
  fileDelta.lockData()
  try {
    for (ioFile in files) {
      val file = ioFile.toPath()
      val rootDescriptor = fileToDescriptors.get(file) ?: continue
      roundDelta?.markRecompile(rootDescriptor, file)
      val marked = fileDelta.markRecompile(rootDescriptor, file)
      if (marked) {
        stampStorage.markChanged(file)
      }
    }
  }
  finally {
    fileDelta.unlockData()
  }
  if (span.isRecording) {
    span.addEvent("mark dirty", Attributes.of(
      AttributeKey.stringArrayKey("filesToMark"), files.map { it.toString() },
      AttributeKey.stringKey("compilationRound"), "CURRENT",
    ))
  }
}
