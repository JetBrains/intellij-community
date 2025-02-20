@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.incremental.CompileScope
import java.nio.file.Path

internal class BazelCompileScope(
  @JvmField val isIncrementalCompilation: Boolean,
  @JvmField val isRebuild: Boolean,
) : CompileScope() {
  override fun isAffected(target: BuildTarget<*>): Boolean = isWholeTargetAffected(target)

  override fun isWholeTargetAffected(target: BuildTarget<*>): Boolean = !isIncrementalCompilation || isRebuild

  override fun isAllTargetsOfTypeAffected(type: BuildTargetType<*>): Boolean = !isIncrementalCompilation || isRebuild

  override fun isBuildForced(target: BuildTarget<*>): Boolean = !isIncrementalCompilation || isRebuild

  override fun isBuildForcedForAllTargets(targetType: BuildTargetType<*>): Boolean = !isIncrementalCompilation || isRebuild

  override fun isBuildIncrementally(targetType: BuildTargetType<*>): Boolean = isIncrementalCompilation && !isRebuild

  override fun isAffected(target: BuildTarget<*>, file: Path): Boolean = true

  override fun markIndirectlyAffected(target: BuildTarget<*>?, file: Path) {
  }
}