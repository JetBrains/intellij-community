@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import kotlinx.coroutines.ensureActive
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.messages.DoneSomethingNotification
import org.jetbrains.jps.incremental.messages.FileDeletedEvent
import java.io.File
import java.io.IOException
import java.nio.file.Files
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
  parentSpan: Span,
) {
  val dirsToDelete = HashSet<Path>()
  val deletedOutputFiles = ArrayList<Path>()
  val sourceToOutputMapping = dataManager.sourceToOutputMapping

  context.projectDescriptor.fsState.processFilesToRecompile(context, target, object : FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget> {
    @Suppress("SameReturnValue")
    override fun apply(target: ModuleBuildTarget, ioFile: File, sourceRoot: JavaSourceRootDescriptor): Boolean {
      val sourceFile = ioFile.toPath()
      val outputs = sourceToOutputMapping.getAndClearOutputs(sourceFile)?.takeIf { it.isNotEmpty() } ?: return true
      try {
        for (i in outputs.size - 1 downTo 0) {
          val outputFile = outputs.get(i)
          try {
            if (Files.deleteIfExists(outputFile)) {
              outputs.removeAt(i)
              outputFile.parent?.let {
                dirsToDelete.add(it)
              }
              deletedOutputFiles.add(outputFile)
            }
          }
          catch (e: IOException) {
            parentSpan.recordException(e, Attributes.of(
              AttributeKey.stringKey("message"), "cannot delete output file",
              AttributeKey.stringKey("sourceFile"), sourceFile.toString(),
            ))
          }
        }
      }
      finally {
        if (outputs.isNotEmpty()) {
          sourceToOutputMapping.setOutputs(sourceFile, outputs)
          parentSpan.addEvent("some outputs were not removed", Attributes.of(
            AttributeKey.stringKey("sourceFile"), sourceFile.toString(),
            AttributeKey.stringArrayKey("outputs"), outputs.map { it.toString() },
          ))
        }
      }
      return true
    }
  })

  if (!deletedOutputFiles.isEmpty()) {
    if (JavaBuilderUtil.isCompileJavaIncrementally(context) && parentSpan.isRecording) {
      parentSpan.addEvent("allDeletedOutputPaths", Attributes.of(
        AttributeKey.stringArrayKey("deletedOutputFiles"), deletedOutputFiles.map { it.toString() },
      ))
    }

    context.processMessage(FileDeletedEvent(deletedOutputFiles.map { it.toString() }))
  }

  // attempting to delete potentially empty directories
  FSOperations.pruneEmptyDirs(context, dirsToDelete)
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