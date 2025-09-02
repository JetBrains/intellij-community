// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.worker.core

import org.jetbrains.bazel.jvm.util.emptyList
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.fs.BuildFSState
import java.nio.file.Path

class BazelDirtyFileHolder(
  @JvmField val context: CompileContext,
  @JvmField val fsState: BuildFSState,
  @PublishedApi @JvmField internal val target: BazelModuleBuildTarget
) : DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> {
  override fun hasDirtyFiles(): Boolean {
    var hasDirtyFiles = false
    processFilesToRecompile { file ->
      hasDirtyFiles = true
      false
    }
    return hasDirtyFiles
  }

  override fun hasRemovedFiles(): Boolean {
    return !context.getUserData(Utils.REMOVED_SOURCES_KEY).isNullOrEmpty()
  }

  override fun processDirtyFiles(processor: FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>) {
    val delta = fsState.getEffectiveFilesDelta(context, target)
    delta.lockData()
    try {
      for ((root, fileSet) in delta.sourceMapToRecompile.entries) {
        for (file in fileSet) {
          if (!processor.apply(target, file.toFile(), root as JavaSourceRootDescriptor)) {
            return
          }
        }
      }
    }
    finally {
      delta.unlockData()
    }
  }

  override fun getRemoved(target: ModuleBuildTarget): Collection<Path> {
    return context.getUserData(Utils.REMOVED_SOURCES_KEY)?.get(target) ?: emptyList()
  }

  override fun getRemovedFiles(target: ModuleBuildTarget): Collection<String> = getRemoved(target).map { it.toString() }

  inline fun processFilesToRecompile(processor: (Set<Path>) -> Boolean): Boolean {
    val delta = fsState.getEffectiveFilesDelta(context, target)
    delta.lockData()
    try {
      for (fileSet in delta.sourceMapToRecompile.values) {
        if (!processor(fileSet)) {
          return false
        }
      }
      return true
    }
    finally {
      delta.unlockData()
    }
  }
}