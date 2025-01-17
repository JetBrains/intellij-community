@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps.impl

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import org.jetbrains.bazel.jvm.jps.RequestLog
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.storage.RelativePathType
import java.nio.file.Path

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

// if more than 50% files were changed, perform a full rebuild
private const val thresholdPercentage = 0.5

internal fun initTargetFsStateForNonInitialBuild(
  context: CompileContext,
  target: BuildTarget<*>,
  log: RequestLog,
  dataManager: BazelBuildDataProvider,
): List<Path> {
  val projectDescriptor = context.projectDescriptor
  val buildRootIndex = projectDescriptor.buildRootIndex as BazelBuildRootIndex

  require(context.getUserData(BuildFSState.CURRENT_ROUND_DELTA_KEY) == null)
  require(context.getUserData(BuildFSState.NEXT_ROUND_DELTA_KEY) == null)

  // linked - stable results
  val toRecompile = Object2ObjectArrayMap<BuildRootDescriptor, Set<Path>>()
  val deletedFiles = ArrayList<Path>()
  val completelyMarkedDirty = dataManager.stampStorage.checkIsDirtyAndUnsetStampIfDirty(toRecompile, deletedFiles, buildRootIndex.descriptors)

  if (log.isDebugEnabled) {
    val relativizer = context.projectDescriptor.dataManager.relativizer.typeAwareRelativizer!!
    log.info(
      "changed: ${toRecompile.keys.joinToString(separator = "\n") { relativizer.toRelative(it.file, RelativePathType.SOURCE) }.prependIndent("  ")}" +
        "\ndeleted: ${deletedFiles.joinToString(separator = "\n") { relativizer.toRelative(it, RelativePathType.SOURCE) }.prependIndent("  ")}"
    )
  }

  val incrementalEffort = toRecompile.size + deletedFiles.size
  val rebuildThreshold = dataManager.stampStorage.actualSourceCount * thresholdPercentage
  val isFullRebuild = incrementalEffort >= rebuildThreshold
  val message = "incrementalEffort=$incrementalEffort, rebuildThreshold=$rebuildThreshold, " +
    "isFullRebuild=$isFullRebuild, completelyMarkedDirty=$completelyMarkedDirty"
  log.info(message)
  if (isFullRebuild || completelyMarkedDirty) {
    throw RebuildRequestedException(RuntimeException(message))
  }

  val fsState = projectDescriptor.fsState
  fsState.getDelta(target).initRecompile(toRecompile)
  fsState.markInitialScanPerformed(target)

  return deletedFiles
}

