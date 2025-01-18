@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps.impl

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.fs.BuildFSState
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

