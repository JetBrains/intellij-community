// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "UnstableApiUsage", "ReplaceGetOrSet", "HardCodedStringLiteral", "RemoveRedundantQualifierName")

package org.jetbrains.bazel.jvm.worker.impl

import androidx.collection.MutableScatterMap
import com.intellij.openapi.util.text.Formats.formatDuration
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import org.jetbrains.bazel.jvm.worker.dependencies.DependencyAnalyzer
import org.jetbrains.bazel.jvm.worker.state.RemovedFileInfo
import org.jetbrains.bazel.jvm.worker.state.SourceFileStateResult
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.use
import org.jetbrains.bazel.jvm.util.slowEqualsAwareHashStrategy
import org.jetbrains.bazel.jvm.worker.core.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.worker.core.BazelBuildRootIndex
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelDirtyFileHolder
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.worker.core.BazelTargetBuildOutputConsumer
import org.jetbrains.bazel.jvm.worker.core.BazelTargetBuilder
import org.jetbrains.bazel.jvm.worker.core.RequestLog
import org.jetbrains.bazel.jvm.worker.core.cleanOutputsCorrespondingToChangedFiles
import org.jetbrains.bazel.jvm.worker.core.initFsStateForCleanBuild
import org.jetbrains.bazel.jvm.worker.core.markTargetUpToDate
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.incremental.BuildListener
import org.jetbrains.jps.incremental.Builder
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.ProjectBuildException
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.StopBuildException
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.fs.BuildFSState.CURRENT_ROUND_DELTA_KEY
import org.jetbrains.jps.incremental.fs.BuildFSState.NEXT_ROUND_DELTA_KEY
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.messages.FileDeletedEvent
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent
import org.jetbrains.jps.incremental.messages.ProgressMessage
import java.io.IOException
import java.nio.file.Path
import java.util.Map
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.map
import kotlin.collections.mapTo
import kotlin.coroutines.coroutineContext
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

internal class JpsTargetBuilder(
  private val log: RequestLog,
  private val tracer: Tracer,
  private val dataManager: BazelBuildDataProvider?,
) {
  private val builderToDuration = MutableScatterMap<Builder, AtomicLong>()
  private val numberOfSourcesProcessedByBuilder = MutableScatterMap<Builder, AtomicInteger>()

  suspend fun build(
    context: BazelCompileContext,
    moduleTarget: BazelModuleBuildTarget,
    builders: Array<out ModuleLevelBuilder>,
    buildState: SourceFileStateResult?,
    outputSink: OutputSink,
    parentSpan: Span,
    dependencyAnalyzer: DependencyAnalyzer?,
  ): Int {
    try {
      context.setDone(0.0f)
      if (dataManager != null) {
        context.addBuildListener(ChainedTargetsBuildListener(context, dataManager))
      }
      for (builder in builders) {
        builder.buildStarted(context)
      }

      try {
        buildTarget(
          context = context,
          target = moduleTarget,
          builders = builders,
          outputSink = outputSink,
          sourceFileState = buildState,
          dependencyAnalyzer = dependencyAnalyzer,
        )
      }
      finally {
        for (builder in builders) {
          builder.buildFinished(context)
        }
      }

      builderToDuration.forEach { builder, time ->
        val processedSources = numberOfSourcesProcessedByBuilder.get(builder)?.get() ?: 0
        val time = time.get().toDuration(DurationUnit.NANOSECONDS)
        val message = "Build duration: ${builder.presentableName} took ${formatDuration(time.toJavaDuration())}; " +
          processedSources + " sources processed (not unique if multiple outputs are produced for a source file)" +
          (if (processedSources == 0) "" else " (${time.inWholeMilliseconds / processedSources} ms per file)")
        parentSpan.addEvent(message)
      }
    }
    catch (e: StopBuildException) {
      // some builder decided to stop the build - report optional progress message if any
      e.message?.takeIf { it.isNotEmpty() }?.let {
        log.processMessage(ProgressMessage(it))
      }
      return if (log.hasErrors()) 1 else 0
    }
    catch (e: BuildDataCorruptedException) {
      parentSpan.recordException(
        e,
        Attributes.of(
          AttributeKey.stringKey("message"), "internal caches are corrupted or have outdated format, forcing project rebuild"
        )
      )
      throw RebuildRequestedException(e)
    }
    catch (e: ProjectBuildException) {
      val cause = e.cause
      if (cause is IOException || cause is BuildDataCorruptedException || (cause is RuntimeException && cause.cause is IOException)) {
        parentSpan.recordException(
          e,
          Attributes.of(
            AttributeKey.stringKey("message"), "internal caches are corrupted or have outdated format, forcing project rebuild"
          )
        )
        throw RebuildRequestedException(cause)
      }
      else {
        // should stop the build with an error
        throw e
      }
    }
    return 0
  }

  // return true if changed something, false otherwise
  private suspend fun runModuleLevelBuilders(
    context: BazelCompileContext,
    target: BazelModuleBuildTarget,
    builders: Array<out ModuleLevelBuilder>,
    outputSink: OutputSink,
    dependencyAnalyzer: DependencyAnalyzer?,
    asyncTaskScope: CoroutineScope,
    parentSpan: Span,
  ): Boolean {
    if (context.scope.isIncrementalCompilation && dependencyAnalyzer != null) {
      // before the first compilation round starts: find and mark dirty all classes that depend on removed or moved classes so
      // that all such files are compiled in the first round.
      tracer.span("markDirtyDependenciesForInitialRound") {
        markDirtyDependenciesForInitialRound(
          context = context,
          target = target,
          dirtyFilesHolder = BazelDirtyFileHolder(context, context.projectDescriptor.fsState, target),
          tracer = tracer,
          dependencyAnalyzer = dependencyAnalyzer,
          dataProvider = dataManager!!,
          asyncTaskScope = asyncTaskScope,
        )
      }
    }

    val chunk = ModuleChunk(java.util.Set.of<ModuleBuildTarget>(target))
    for (builder in builders) {
      builder.chunkBuildStarted(context, chunk)
    }

    if (!context.scope.isRebuild && dataManager != null) {
      completeRecompiledSourcesSet(context, target, dataManager)
    }

    var doneSomething = false
    val outputConsumer = BazelTargetBuildOutputConsumer(dataManager = dataManager, outputSink = outputSink)
    try {
      val fsState = context.projectDescriptor.fsState
      var rebuildFromScratchRequested = false
      var nextPassRequired: Boolean
      do {
        nextPassRequired = false
        fsState.beforeNextRoundStart(context, chunk)

        if (dataManager != null && !context.scope.isRebuild) {
          cleanOutputsCorrespondingToChangedFiles(
            context = context,
            target = target,
            dataManager = dataManager,
            outputSink = outputSink,
            parentSpan = parentSpan,
          )
        }

        val dirtyFilesHolder = BazelDirtyFileHolder(context, fsState, target)
        try {
          var buildersPassed = 0
          for (builder in builders) {
            val deletedFiles = fsState.getAndClearDeletedPaths(target)
            require(deletedFiles.isEmpty()) {
              "Unexpected files to delete: $deletedFiles"
            }

            val start = System.nanoTime()
            val processedSourcesBefore = outputConsumer.getNumberOfProcessedSources()
            val buildResult = tracer.span("runBuilder") {
              if (builder is BazelTargetBuilder) {
                builder.build(
                  context = context,
                  module = target.module,
                  chunk = chunk,
                  dirtyFilesHolder = dirtyFilesHolder,
                  target = target,
                  outputConsumer = outputConsumer,
                  outputSink = outputSink,
                )
              }
              else {
                builder.build(context, chunk, dirtyFilesHolder, outputConsumer)
              }
            }
            storeBuilderStatistics(
              builder = builder,
              elapsedTime = System.nanoTime() - start,
              processedFiles = outputConsumer.getNumberOfProcessedSources() - processedSourcesBefore,
            )

            if (buildResult != ModuleLevelBuilder.ExitCode.NOTHING_DONE) {
              doneSomething = true
            }
            if (buildResult == ModuleLevelBuilder.ExitCode.ABORT) {
              throw StopBuildException("Builder ${builder.presentableName} requested build stop")
            }

            coroutineContext.ensureActive()

            if (buildResult == ModuleLevelBuilder.ExitCode.ADDITIONAL_PASS_REQUIRED) {
              nextPassRequired = true
            }
            else if (buildResult == ModuleLevelBuilder.ExitCode.CHUNK_REBUILD_REQUIRED) {
              if (!rebuildFromScratchRequested && !context.scope.isRebuild) {
                var infoMessage = "Builder \"${builder.presentableName}\" requested rebuild of module chunk \"${target.targetLabel}\""
                infoMessage += ".\n"
                infoMessage += "Consider building whole project or rebuilding the module."
                context.compilerMessage(kind = Kind.INFO, message = infoMessage)
                // allow rebuild from scratch only once per chunk
                rebuildFromScratchRequested = true
                // forcibly mark all files in the chunk dirty
                fsState.clearContextRoundData(context)
                FSOperations.markDirty(context, CompilationRound.NEXT, chunk, null)
                // reverting to the beginning
                nextPassRequired = true
                outputConsumer.clear()
                break
              }
              else {
                parentSpan.addEvent("builder requested second chunk rebuild", Attributes.of(
                  AttributeKey.stringKey("builder"), target.targetLabel,
                ))
              }
            }

            buildersPassed++
          }
        }
        finally {
          val moreToCompile = dataManager != null && JavaBuilderUtil.updateMappingsOnRoundCompletion(context, dirtyFilesHolder, chunk)
          if (moreToCompile) {
            nextPassRequired = true
          }
          JavaBuilderUtil.clearDataOnRoundCompletion(context)
        }
      } while (nextPassRequired)
    }
    finally {
      outputConsumer.clear()
      for (builder in builders) {
        builder.chunkBuildFinished(context, chunk)
      }
      if (Utils.errorsDetected(context)) {
        context.compilerMessage(kind = Kind.JPS_INFO, message = "Errors occurred while compiling ${target.targetLabel}")
      }
    }

    return doneSomething
  }

  private suspend fun buildTarget(
    context: BazelCompileContext,
    target: BazelModuleBuildTarget,
    builders: Array<out ModuleLevelBuilder>,
    sourceFileState: SourceFileStateResult?,
    outputSink: OutputSink,
    dependencyAnalyzer: DependencyAnalyzer?,
  ) {
    val targets = java.util.Set.of<BuildTarget<*>>(target)
    try {
      context.setCompilationStartStamp(targets, System.currentTimeMillis())
      context.putUserData(Utils.ERRORS_DETECTED_KEY, false)

      var doneSomething = false

      val fsState = context.projectDescriptor.fsState
      require(!fsState.isInitialScanPerformed(target))
      tracer.spanBuilder("fs state init")
        .setAttribute("isRebuild", context.scope.isRebuild)
        .use { span ->
          if (context.scope.isRebuild || sourceFileState == null) {
            initFsStateForCleanBuild(context = context, target = target)
          }
          else {
            val projectDescriptor = context.projectDescriptor
            require(context.getUserData(CURRENT_ROUND_DELTA_KEY) == null)
            require(context.getUserData(NEXT_ROUND_DELTA_KEY) == null)
            val buildRootIndex = projectDescriptor.buildRootIndex as BazelBuildRootIndex
            val changedOrAddedFiles = sourceFileState.changedOrAddedFiles
            if (changedOrAddedFiles.isEmpty()) {
              fsState.getDelta(target).initRecompile(Map.of())
            }
            else {
              val k = Array<BuildRootDescriptor>(changedOrAddedFiles.size) {
                buildRootIndex.fileToDescriptors.get(changedOrAddedFiles.get(it))!!
              }
              val v = Array<Set<Path>>(changedOrAddedFiles.size) {
                ObjectArraySet(arrayOf(changedOrAddedFiles.get(it)))
              }
              fsState.getDelta(target).initRecompile(Object2ObjectArrayMap(k, v))
            }
            fsState.markInitialScanPerformed(target)

            val deletedFiles = sourceFileState.deletedFiles
            if (!deletedFiles.isEmpty()) {
              doneSomething = deleteOutputsAssociatedWithDeletedPaths(
                context = context,
                target = target,
                deletedFiles = deletedFiles,
                outputSink = outputSink,
                span = span,
              )
            }
          }
        }

      fsState.beforeChunkBuildStart(context, targets)

      tracer.span("runModuleLevelBuilders") { span ->
        if (runModuleLevelBuilders(
            context = context,
            target = target,
            builders = builders,
            outputSink = outputSink,
            dependencyAnalyzer = dependencyAnalyzer,
            asyncTaskScope = this@span,
            parentSpan = span,
          )) {
          doneSomething = true
        }
      }

      fsState.clearContextRoundData(context)
      fsState.clearContextChunk(context)

      if (doneSomething && dataManager != null) {
        markTargetUpToDate(context = context, target = target, dataManager = dataManager)
      }
    }
    finally {
      try {
        // restore deleted paths that were not processed
        val map = context.getUserData(Utils.REMOVED_SOURCES_KEY)
        if (map != null) {
          val fsState = context.projectDescriptor.fsState
          for (entry in map.entries) {
            for (file in entry.value) {
              fsState.registerDeleted(context, entry.key, file, null)
            }
          }
        }
      }
      finally {
        context.putUserData(Utils.REMOVED_SOURCES_KEY, null)
      }
    }
  }

  private fun storeBuilderStatistics(builder: Builder, elapsedTime: Long, processedFiles: Int) {
    builderToDuration.getOrPut(builder) { AtomicLong() }.addAndGet(elapsedTime)
    numberOfSourcesProcessedByBuilder.getOrPut(builder) { AtomicInteger() }.addAndGet(processedFiles)
  }
}

private class ChainedTargetsBuildListener(private val context: CompileContext, private val dataManager: BazelBuildDataProvider) : BuildListener {
  override fun filesGenerated(event: FileGeneratedEvent) {
    val projectDescriptor = context.projectDescriptor
    val fsState = projectDescriptor.fsState
    val buildRootIndex = projectDescriptor.buildRootIndex as BazelBuildRootIndex
    for (pair in event.paths) {
      val relativePath = pair.getSecond()
      val file = if (relativePath == ".") Path.of(pair.getFirst()) else Path.of(pair.getFirst(), relativePath)
      val buildRootDescriptor = buildRootIndex.fileToDescriptors.get(file) ?: continue
      val target = buildRootDescriptor.target
      if (event.sourceTarget != target) {
        fsState.markDirty(
          /* context = */ context,
          /* round = */ CompilationRound.NEXT,
          /* file = */ file,
          /* buildRootDescriptor = */ buildRootDescriptor,
          /* stampStorage = */ dataManager.getFileStampStorage(target),
          /* saveEventStamp = */ false,
        )
      }
    }
  }

  override fun filesDeleted(event: FileDeletedEvent) {
    val fsState = context.projectDescriptor.fsState
    val buildRootIndex = context.projectDescriptor.buildRootIndex as BazelBuildRootIndex
    for (path in event.filePaths) {
      val file = Path.of(path)
      val rootDescriptor = buildRootIndex.fileToDescriptors.get(file) ?: continue
      fsState.registerDeleted(context, rootDescriptor.target, file)
    }
  }
}

private fun deleteOutputsAssociatedWithDeletedPaths(
  context: CompileContext,
  target: ModuleBuildTarget,
  deletedFiles: List<RemovedFileInfo>,
  outputSink: OutputSink,
  span: Span,
): Boolean {
  var doneSomething = false
  // delete outputs associated with removed paths
  for (item in deletedFiles) {
    if (item.outputs.isEmpty()) {
      continue
    }

    outputSink.removeAll(item.outputs)
    doneSomething = true
    if (span.isRecording) {
      span.addEvent(
        "deleted files",
        Attributes.of(AttributeKey.stringArrayKey("deletedOutputFiles"), item.outputs.toList()),
      )
    }
    //context.processMessage(FileDeletedEvent(deletedOutputFiles.map { it.toString() }))
  }

  val removedSources = context.getUserData(Utils.REMOVED_SOURCES_KEY)?.get(target)
  if (removedSources == null) {
    context.putUserData(Utils.REMOVED_SOURCES_KEY, Map.of(target, deletedFiles.map { it.sourceFile } as Collection<Path>))
  }
  else {
    val set = ObjectLinkedOpenCustomHashSet(removedSources.size + deletedFiles.size, slowEqualsAwareHashStrategy<Path>())
    set.addAll(removedSources)
    deletedFiles.mapTo(set) { it.sourceFile }
    context.putUserData(Utils.REMOVED_SOURCES_KEY, Map.of(target, set as Collection<Path>))
  }

  return doneSomething
}

/**
 * if an output file is generated from multiple sources, make sure all of them are added for recompilation
 */
private fun completeRecompiledSourcesSet(context: CompileContext, target: BazelModuleBuildTarget, dataManager: BazelBuildDataProvider) {
  val projectDescriptor = context.projectDescriptor
  val affected = mutableListOf<Array<String>>()
  val sourceToOut = dataManager.sourceToOutputMapping
  val delta = projectDescriptor.fsState.getEffectiveFilesDelta(context, target)
  delta.lockData()
  try {
    if (!delta.sourceMapToRecompile.isEmpty()) {
      for (files in delta.sourceMapToRecompile.values) {
        sourceToOut.collectAffectedOutputs(files, affected)
      }
    }
  }
  finally {
    delta.unlockData()
  }

  if (affected.isEmpty()) {
    return
  }

  // one output can be produced by different sources, so, we find intersection by outputs
  val affectedSources = sourceToOut.findAffectedSources(affected)
  if (affectedSources.isEmpty()) {
    return
  }

  val fileToDescriptors = (context.projectDescriptor.buildRootIndex as BazelBuildRootIndex).fileToDescriptors
  val currentRoundFileDelta = context.getUserData(CURRENT_ROUND_DELTA_KEY)
  val fileDelta = context.projectDescriptor.fsState.getDelta(target)
  for (sourceDescriptor in affectedSources) {
    val sourceFile = sourceDescriptor.sourceFile
    val rootDescriptor = fileToDescriptors.get(sourceFile) ?: continue
    val marked = fileDelta.markRecompileIfNotDeleted(rootDescriptor, sourceFile)
    if (marked) {
      sourceDescriptor.isChanged = true
      currentRoundFileDelta?.markRecompile(rootDescriptor, sourceFile)
    }
  }
}