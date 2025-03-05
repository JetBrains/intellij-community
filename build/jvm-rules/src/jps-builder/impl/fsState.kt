// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import kotlinx.coroutines.ensureActive
import org.jetbrains.bazel.jvm.jps.OutputSink
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.messages.DoneSomethingNotification
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

internal fun initFsStateForCleanBuild(context: CompileContext, target: BuildTarget<*>) {
  val fsState = context.projectDescriptor.fsState
  // JPS calls markRecompile on it, but for clean rebuild it should always be null
  require(context.getUserData(BuildFSState.CURRENT_ROUND_DELTA_KEY) == null)
  val rootIndex = context.projectDescriptor.buildRootIndex as BazelBuildRootIndex
  val map = Object2ObjectArrayMap<BuildRootDescriptor, Set<Path>>(
    Array(rootIndex.descriptors.size) { rootIndex.descriptors[it] },
    Array(rootIndex.fileToDescriptors.size) { index -> ObjectArraySet<Path>(arrayOf(rootIndex.descriptors[index].rootFile)) }
  )
  fsState.getDelta(target).initRecompile(map)
  FSOperations.addCompletelyMarkedDirtyTarget(context, target)
  fsState.markInitialScanPerformed(target)
}

internal fun cleanOutputsCorrespondingToChangedFiles(
  context: CompileContext,
  target: BazelModuleBuildTarget,
  dataManager: BazelBuildDataProvider,
  outputSink: OutputSink,
  parentSpan: Span,
) {
  val deletedOutputFiles = ArrayList<String>()
  val sourceToOutputMapping = dataManager.sourceToOutputMapping
  val delta = context.projectDescriptor.fsState.getEffectiveFilesDelta(context, target)
  delta.lockData()
  try {
    for (entry in delta.sourceMapToRecompile.entries) {
      for (sourceFile in entry.value) {
        val outputs = sourceToOutputMapping.getAndClearOutputs(sourceFile)?.takeIf { it.isNotEmpty() } ?: continue
        outputSink.removeAll(outputs)
        deletedOutputFiles.addAll(outputs)
      }
    }
  }
  finally {
    delta.unlockData()
  }

  if (!deletedOutputFiles.isEmpty()) {
    if (JavaBuilderUtil.isCompileJavaIncrementally(context) && parentSpan.isRecording) {
      parentSpan.addEvent("deletedOutputs", Attributes.of(
        AttributeKey.stringArrayKey("deletedOutputFiles"), deletedOutputFiles,
      ))
    }

    //context.processMessage(FileDeletedEvent(deletedOutputFiles.map { it.toString() }))
  }
}

internal fun cleanOutputsCorrespondingToChangedFiles(
  files: Collection<Path>,
  dataManager: BazelBuildDataProvider,
  outputSink: OutputSink,
  parentSpan: Span,
) {
  val deletedOutputFiles = ArrayList<String>()
  val sourceToOutputMapping = dataManager.sourceToOutputMapping
  for (sourceFile in files) {
    val outputs = sourceToOutputMapping.getAndClearOutputs(sourceFile)?.takeIf { it.isNotEmpty() } ?: continue
    outputSink.removeAll(outputs)
    deletedOutputFiles.addAll(outputs)
  }

  if (!deletedOutputFiles.isEmpty()) {
    if (parentSpan.isRecording) {
      parentSpan.addEvent("deletedOutputs", Attributes.of(
        AttributeKey.stringArrayKey("deletedOutputFiles"), deletedOutputFiles,
      ))
    }
    //context.processMessage(FileDeletedEvent(deletedOutputFiles.map { it.toString() }))
  }
}

internal suspend fun markTargetUpToDate(
  context: CompileContext,
  target: ModuleBuildTarget,
  dataManager: BazelBuildDataProvider,
) {
  coroutineContext.ensureActive()

  if (Utils.errorsDetected(context)) {
    return
  }

  var doneSomething = dropRemovedPaths(context, target, dataManager)
  context.clearNonIncrementalMark(target)

  val fsState = context.projectDescriptor.fsState
  val delta = fsState.getDelta(target)
  delta.lockData()
  try {
    val rootToRecompile = delta.sourceMapToRecompile
    if (!rootToRecompile.isEmpty()) {
      for (entry in rootToRecompile) {
        dataManager.stampStorage.markAsUpToDate(entry.value)
        doneSomething = true
      }
      rootToRecompile.clear()
    }
  }
  finally {
    delta.unlockData()
  }

  if (doneSomething) {
    context.processMessage(DoneSomethingNotification.INSTANCE)
  }
}

private fun dropRemovedPaths(context: CompileContext, target: ModuleBuildTarget, dataManager: BazelBuildDataProvider): Boolean {
  val files = Utils.REMOVED_SOURCES_KEY.get(context)?.remove(target) ?: return false
  dataManager.sourceToOutputMapping.remove(files)
  return true
}