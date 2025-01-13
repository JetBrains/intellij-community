@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.impl

import org.jetbrains.bazel.jvm.jps.ConsoleMessageHandler
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.fs.FilesDelta
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.jps.incremental.storage.StampsStorage

internal fun initTargetFsStateForNonInitialBuild(context: CompileContext, target: BuildTarget<*>, messageHandler: ConsoleMessageHandler) {
  val projectDescriptor = context.projectDescriptor
  val dataManager = projectDescriptor.dataManager
  val buildRootIndex = projectDescriptor.buildRootIndex as BazelBuildRootIndex
  val fsState = projectDescriptor.fsState

  val currentDelta = context.getUserData(BuildFSState.CURRENT_ROUND_DELTA_KEY)
  val nextDelta = context.getUserData(BuildFSState.NEXT_ROUND_DELTA_KEY)
  val filesDelta = fsState.getDelta(target)

  val stampStorage = dataManager.getFileStampStorage(target)!!
  markDirtyFiles(
    context = context,
    target = target,
    stampStorage = stampStorage,
    buildRootIndex = buildRootIndex,
    fsState = fsState,
    filesDelta = filesDelta,
    messageHandler = messageHandler,
  )

  val fileToDescriptors = buildRootIndex.fileToDescriptors

  // handle deleted paths
  val sourceToOutputMap = dataManager.getSourceToOutputMap(target)

  for (file in sourceToOutputMap.sourceFileIterator) {
    if (fileToDescriptors.contains(file)) {
      continue
    }

    currentDelta?.addDeleted(file)
    nextDelta?.addDeleted(file)
    filesDelta.addDeleted(file)
    stampStorage.removeStamp(file, target)
  }
  fsState.markInitialScanPerformed(target)
}

private fun markDirtyFiles(
  context: CompileContext,
  target: BuildTarget<*>,
  stampStorage: StampsStorage<*>,
  buildRootIndex: BazelBuildRootIndex,
  fsState: BuildFSState,
  filesDelta: FilesDelta,
  messageHandler: ConsoleMessageHandler,
) {
  var completelyMarkedDirty = true
  for (rootDescriptor in buildRootIndex.descriptors) {
    fsState.clearRecompile(rootDescriptor)

    val file = rootDescriptor.file
    val markDirty = stampStorage.getCurrentStampIfUpToDate(file, rootDescriptor.target, null) == null
    if (!markDirty) {
      completelyMarkedDirty = false
      continue
    }

    val target = rootDescriptor.target
    context.getUserData(BuildFSState.CURRENT_ROUND_DELTA_KEY)?.markRecompile(rootDescriptor, file)
    filesDelta.lockData()
    try {
      val marked = filesDelta.markRecompile(rootDescriptor, file)
      if (marked) {
        if (messageHandler.isDebugEnabled) {
          val relativePath = context.projectDescriptor.dataManager.relativizer.typeAwareRelativizer!!
            .toRelative(file, RelativePathType.SOURCE)
          messageHandler.debug("dirty: $relativePath")
        }
        stampStorage.removeStamp(file, target)
      }
    }
    finally {
      filesDelta.unlockData()
    }
  }

  if (completelyMarkedDirty) {
    FSOperations.addCompletelyMarkedDirtyTarget(context, target)
  }
}

