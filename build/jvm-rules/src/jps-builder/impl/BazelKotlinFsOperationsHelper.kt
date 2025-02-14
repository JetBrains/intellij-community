// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.bazel.jvm.jps.OutputSink
import org.jetbrains.bazel.jvm.linkedSet
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations.addCompletelyMarkedDirtyTarget
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder.TargetFiles
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

internal class BazelKotlinFsOperationsHelper(
  private val context: CompileContext,
  private val chunk: ModuleChunk,
  private val span: Span,
  private val dataManager: BazelBuildDataProvider,
) {
  internal var hasMarkedDirty = false
    private set

  fun markChunk(excludeFiles: Set<File>) {
    val target = chunk.targets.single()
    var completelyMarkedDirty = true
    val stampStorage = dataManager.getFileStampStorage(target)
    for (rootDescriptor in (context.projectDescriptor.buildRootIndex as BazelBuildRootIndex).descriptors) {
      val file = rootDescriptor.rootFile
      val filePath = file.toString()
      if (!(FileUtilRt.extensionEquals(filePath, "kt") || FileUtilRt.extensionEquals(filePath, "kts")) ||
        excludeFiles.contains(file.toFile())) {
        completelyMarkedDirty = false
        continue
      }

      hasMarkedDirty = true

      // if it is a full project rebuild, all storages are already completely cleared;
      // so passing null because there is no need to access the storage to clear non-existing data
      val marker = if (dataManager.isCleanBuild) null else stampStorage
      context.projectDescriptor.fsState.markDirty(context, CompilationRound.NEXT, file, rootDescriptor, marker, false)
    }

    if (completelyMarkedDirty) {
      addCompletelyMarkedDirtyTarget(context, target)
    }
  }

  fun markFilesForCurrentRound(files: Sequence<Path>, targetDirtyFiles: TargetFiles?) {
    val buildRootIndex = context.projectDescriptor.buildRootIndex as BazelBuildRootIndex
    for (file in files) {
      val root = buildRootIndex.fileToDescriptors.get(file) ?: continue
      targetDirtyFiles?._markDirty(file, root)
    }

    markFilesImpl(files = files, currentRound = true, span = span) { it.exists() }
  }

  /**
   * Marks given [files] as dirty for current round.
   */
  fun markFilesForCurrentRound(
    files: Collection<Path>,
    targetDirtyFiles: TargetFiles?,
    outputSink: OutputSink,
    parentSpan: Span,
  ) {
    val buildRootIndex = context.projectDescriptor.buildRootIndex as BazelBuildRootIndex
    for (file in files) {
      targetDirtyFiles._markDirty(file, buildRootIndex.fileToDescriptors.get(file) ?: continue)
    }

    markFilesImpl(files.asSequence(), currentRound = true, span = span) { Files.exists(it) }
    cleanOutputsCorrespondingToChangedFiles(files = files, dataManager = dataManager, outputSink = outputSink, parentSpan = parentSpan)
  }

  fun markFiles(files: Sequence<Path>) {
    markFilesImpl(files = files, currentRound = false, span = span) { it.exists() }
  }

  fun markInChunkOrDependents(files: Sequence<Path>, excludeFiles: Set<Path>) {
    markFilesImpl(files = files, currentRound = false, span = span) {
      !excludeFiles.contains(it) && it.exists()
    }
  }

  private inline fun markFilesImpl(
    files: Sequence<Path>,
    currentRound: Boolean,
    span: Span,
    shouldMark: (Path) -> Boolean
  ) {
    val filesToMark = files.filterTo(linkedSet(), shouldMark)
    if (filesToMark.isEmpty()) {
      return
    }

    val compilationRound = if (currentRound) {
      CompilationRound.CURRENT
    }
    else {
      hasMarkedDirty = true
      CompilationRound.NEXT
    }

    val projectDescriptor = context.projectDescriptor
    val fileToDescriptors = (projectDescriptor.buildRootIndex as BazelBuildRootIndex).fileToDescriptors
    for (fileToMark in filesToMark) {
      val rootDescriptor = fileToDescriptors.get(fileToMark) ?: continue
      projectDescriptor.fsState.markDirty(
        /* context = */ context,
        /* round = */ compilationRound,
        /* file = */ fileToMark,
        /* buildRootDescriptor = */ rootDescriptor,
        /* stampStorage = */ projectDescriptor.dataManager.getFileStampStorage(rootDescriptor.target),
        /* saveEventStamp = */ false,
      )
    }
    span.addEvent("mark dirty", Attributes.of(
      AttributeKey.stringArrayKey("filesToMark"), filesToMark.map { it.toString() },
      AttributeKey.stringKey("compilationRound"), compilationRound.name,
    ))
  }
}