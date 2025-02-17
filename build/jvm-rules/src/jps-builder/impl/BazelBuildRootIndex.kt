@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.openapi.util.io.FileFilters
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildRootIndex
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.CompileContext
import java.io.File
import java.io.FileFilter
import java.nio.file.Path

internal class BazelBuildRootIndex(target: BazelModuleBuildTarget) : BuildRootIndex {
  @JvmField val fileToDescriptors = hashMap<Path, JavaSourceRootDescriptor>()
  @JvmField val descriptors = target.computeRootDescriptors()

  init {
    val descriptors = descriptors
    for (descriptor in descriptors) {
      val old = fileToDescriptors.put(descriptor.rootFile, descriptor)
      require(old == null) {
        "Duplicated root (old=$old, new=$descriptor)"
      }
    }
  }

  override fun <R : BuildRootDescriptor> getRootDescriptors(
    root: File,
    types: Collection<BuildTargetType<out BuildTarget<R>>>?,
    context: CompileContext?,
  ): List<R> {
    val descriptor = fileToDescriptors.get(root.toPath()) ?: return java.util.List.of()
    if (types == null || types.any { it == JavaSourceRootDescriptor::class.java }) {
      @Suppress("UNCHECKED_CAST")
      return java.util.List.of(descriptor as R)
    }
    return java.util.List.of()
  }

  @Suppress("UNCHECKED_CAST")
  override fun <R : BuildRootDescriptor> getTargetRoots(target: BuildTarget<R>, context: CompileContext?): List<R> = descriptors as List<R>

  override fun <R : BuildRootDescriptor?> getTempTargetRoots(target: BuildTarget<R?>, context: CompileContext): List<R> = java.util.List.of()

  override fun <R : BuildRootDescriptor> associateTempRoot(context: CompileContext, target: BuildTarget<R>, root: R) {
    throw IllegalStateException("Should not be called")
  }

  // in Bazel, each root it is a file, so, no parent
  override fun <R : BuildRootDescriptor?> findParentDescriptor(
    file: File,
    types: Collection<BuildTargetType<out BuildTarget<R>>>,
    context: CompileContext?,
  ): R? {
    if (types.any { it == JavaSourceRootDescriptor::class.java }) {
      @Suppress("UNCHECKED_CAST")
      return fileToDescriptors.get(file.toPath()) as R
    }
    return null
  }

  override fun <R : BuildRootDescriptor> findAllParentDescriptors(
    file: File,
    types: Collection<BuildTargetType<out BuildTarget<R>>>?,
    context: CompileContext?,
  ): Collection<R> {
    if (types == null || types.any { it == JavaSourceRootDescriptor::class.java }) {
      return findAllParentDescriptors(file, context)
    }
    return java.util.List.of()
  }

  override fun <R : BuildRootDescriptor> findAllParentDescriptors(file: File, context: CompileContext?): Collection<R> {
    @Suppress("UNCHECKED_CAST")
    return fileToDescriptors.get(file.toPath())?.let { java.util.List.of(it as R) } ?: java.util.List.of()
  }

  override fun clearTempRoots(context: CompileContext): Collection<BuildRootDescriptor> = java.util.List.of()

  override fun findJavaRootDescriptor(context: CompileContext?, file: File): JavaSourceRootDescriptor? {
    return fileToDescriptors.get(file.toPath())
  }

  override fun getRootFilter(descriptor: BuildRootDescriptor): FileFilter = FileFilters.EVERYTHING

  override fun isFileAccepted(file: Path, descriptor: BuildRootDescriptor) = true

  override fun isDirectoryAccepted(dir: Path, descriptor: BuildRootDescriptor) = true
}