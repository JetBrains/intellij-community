// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.worker.core

import androidx.collection.ScatterMap
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.worker.state.SourceDescriptor
import org.jetbrains.bazel.jvm.util.emptyStringArray
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.storage.BuildDataProvider
import org.jetbrains.jps.incremental.storage.OneToManyPathMapping
import org.jetbrains.jps.incremental.storage.OutputToTargetMapping
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.jps.incremental.storage.SourceToOutputMappingCursor
import org.jetbrains.jps.incremental.storage.StampsStorage
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

class BazelBuildDataProvider(
  @JvmField val relativizer: BazelPathTypeAwareRelativizer,
  private val sourceToDescriptor: ScatterMap<Path, SourceDescriptor>,
  @JvmField val storeFile: Path,
  @JvmField val allocator: RootAllocator,
  @JvmField val isCleanBuild: Boolean,
  @JvmField val dependencyFileToDigest: ScatterMap<Path, ByteArray>,
) : BuildDataProvider {
  @JvmField
  val stampStorage = BazelStampStorage(sourceToDescriptor)

  @JvmField
  val sourceToOutputMapping = BazelSourceToOutputMapping(map = sourceToDescriptor, relativizer = relativizer)

  @JvmField
  val sourceToForm = object : OneToManyPathMapping {
    override fun getOutputs(path: String): Collection<String>? = sourceToOutputMapping.getOutputs(path)

    override fun getOutputs(file: Path): Collection<Path>? = sourceToOutputMapping.getOutputs(file)

    override fun setOutputs(path: Path, outPaths: List<Path>) {
      sourceToOutputMapping.setOutputs(path, outPaths)
    }

    override fun remove(key: Path) {
      sourceToOutputMapping.remove(key)
    }
  }

  fun getFinalList(): Array<SourceDescriptor> {
    @Suppress("UNCHECKED_CAST") val result = synchronized(sourceToDescriptor) {
      val r = arrayOfNulls<SourceDescriptor>(sourceToDescriptor.size)
      var index = 0
      sourceToDescriptor.forEachValue {
        r[index++] = it
      }
      r as Array<SourceDescriptor>
    }
    result.sortBy { it.sourceFile }
    return result
  }

  override fun clearCache() {
  }

  override fun commit() {
  }

  override fun removeAllMaps() {
  }

  override fun removeStaleTarget(targetId: String, targetTypeId: String) {
  }

  override fun getFileStampStorage(target: BuildTarget<*>): BazelStampStorage = stampStorage

  override fun getSourceToOutputMapping(target: BuildTarget<*>): BazelSourceToOutputMapping = sourceToOutputMapping

  override fun getOutputToTargetMapping(): OutputToTargetMapping = throw UnsupportedOperationException("Must not be used")

  override fun getSourceToForm(target: BuildTarget<*>): OneToManyPathMapping = sourceToForm

  override fun closeTargetMaps(target: BuildTarget<*>) {
  }

  override fun close() {
  }
}

class BazelStampStorage(private val map: ScatterMap<Path, SourceDescriptor>) : StampsStorage<ByteArray> {
  override fun updateStamp(sourceFile: Path, buildTarget: BuildTarget<*>?, currentFileTimestamp: Long) {
    throw IllegalStateException()
  }

  fun markAsUpToDate(sourceFiles: Collection<Path>) {
    synchronized(map) {
      for (sourceFile in sourceFiles) {
        requireNotNull(map.get(sourceFile)) { "Source file is unknown: $sourceFile" }.isChanged = false
      }
    }
  }

  override fun removeStamp(sourceFile: Path, buildTarget: BuildTarget<*>?) {
    // used by BazelKotlinFsOperationsHelper (markChunk -> fsState.markDirty)
    markChanged(sourceFile)
  }

  fun markChanged(sourceFile: Path) {
    synchronized(map) {
      map.get(sourceFile)?.isChanged = true
    }
  }

  override fun getCurrentStampIfUpToDate(file: Path, buildTarget: BuildTarget<*>?, attrs: BasicFileAttributes?): ByteArray? {
    throw UnsupportedOperationException("Must not be used")
  }
}

class BazelSourceToOutputMapping(
  private val map: ScatterMap<Path, SourceDescriptor>,
  private val relativizer: PathTypeAwareRelativizer,
) : SourceToOutputMapping {
  override fun setOutputs(sourceFile: Path, outputPaths: List<Path>) {
    val relativeOutputPaths = if (outputPaths.isEmpty()) {
      emptyStringArray
    }
    else {
      Array(outputPaths.size) { relativizer.toRelative(outputPaths[it], RelativePathType.OUTPUT) }
    }
    synchronized(map) {
      val value = if (relativeOutputPaths.isEmpty()) {
        map.get(sourceFile) ?: return
      }
      else {
        getDescriptorOrError(sourceFile)
      }

      value.outputs = relativeOutputPaths
    }
  }

  private fun getDescriptorOrError(sourceFile: Path): SourceDescriptor {
    return requireNotNull(map.get(sourceFile)) { "Source file is unknown: $sourceFile" }
  }

  override fun appendOutput(sourcePath: String, outputPath: String) {
    appendRawRelativeOutput(Path.of(sourcePath), relativizer.toRelative(outputPath, RelativePathType.OUTPUT))
  }

  fun appendRawRelativeOutput(sourceFile: Path, relativeOutputPath: String) {
    synchronized(map) {
      val sourceInfo = getDescriptorOrError(sourceFile)
      val existingOutputs = sourceInfo.outputs
      if (existingOutputs.isEmpty()) {
        sourceInfo.outputs = arrayOf(relativeOutputPath)
      }
      else if (!existingOutputs.contains(relativeOutputPath)) {
        sourceInfo.outputs = existingOutputs + relativeOutputPath
      }
    }
  }

  override fun remove(sourceFile: Path) {
    synchronized(map) {
      map.get(sourceFile)?.outputs = emptyStringArray
    }
  }

  fun remove(sourceFiles: Collection<Path>) {
    synchronized(map) {
      for (sourceFile in sourceFiles) {
        map.get(sourceFile)?.outputs = emptyStringArray
      }
    }
  }

  override fun removeOutput(sourcePath: String, outputPath: String) {
    val sourceFile = Path.of(sourcePath)
    val relativeOutputPath = relativizer.toRelative(outputPath, RelativePathType.OUTPUT)
    synchronized(map) {
      val sourceInfo = map.get(sourceFile) ?: return
      val existingOutputs = sourceInfo.outputs.takeIf { it.isNotEmpty() } ?: return
      val indexToRemove = existingOutputs.indexOf(relativeOutputPath)
      if (indexToRemove != -1) {
        if (existingOutputs.size == 1) {
          sourceInfo.outputs = emptyStringArray
        }
        else {
          sourceInfo.outputs = Array(existingOutputs.size - 1) {
            existingOutputs[if (it < indexToRemove) it else it + 1]
          }
        }
      }
    }
  }

  override fun getOutputs(sourcePath: String): Collection<String>? {
    val sourceFile = Path.of(sourcePath)
    return synchronized(map) { map.get(sourceFile)?.outputs }
      ?.map { relativizer.toAbsolute(it, RelativePathType.OUTPUT) }
  }

  override fun getOutputs(sourceFile: Path): Collection<Path>? {
    return synchronized(map) { map.get(sourceFile)?.outputs }
      ?.map { relativizer.toAbsoluteFile(it, RelativePathType.OUTPUT) }
  }

  fun getAndClearOutputs(sourceFile: Path): Array<String>? {
    synchronized(map) {
      // must be not null - probably, later we should add a warning here
      val descriptor = map.get(sourceFile) ?: return null
      val result = descriptor.outputs.takeIf { it.isNotEmpty() } ?: return null
      descriptor.outputs = emptyStringArray
      return result
    }
  }

  fun collectAffectedOutputs(sourceFiles: Collection<Path>, to: MutableList<Array<String>>) {
    synchronized(map) {
      for (sourceFile in sourceFiles) {
        val descriptor = map.get(sourceFile) ?: continue
        to.add(descriptor.outputs)
      }
    }
  }

  fun findAffectedSources(affectedSources: List<Array<String>>): List<SourceDescriptor> {
    val result = ArrayList<SourceDescriptor>(affectedSources.size)
    synchronized(map) {
      map.forEachValue { descriptor ->
        for (output in descriptor.outputs) {
          // see KTIJ-197
          if (!output.endsWith(".kotlin_module") && affectedSources.any { it.contains(output) }) {
            result.add(descriptor)
            break
          }
        }
      }
    }
    return result
  }

  override fun getSourceFileIterator(): Iterator<Path> {
    throw UnsupportedOperationException("Must not be used")
  }

  override fun getSourcesIterator(): Iterator<String> {
    throw UnsupportedOperationException("Must not be used")
  }

  override fun cursor(): SourceToOutputMappingCursor {
    throw UnsupportedOperationException("Must not be used")
  }
}