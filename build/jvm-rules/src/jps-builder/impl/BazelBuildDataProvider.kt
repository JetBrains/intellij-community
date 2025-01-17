@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps.impl

import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.jps.SourceDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.storage.*
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal class BazelBuildDataProvider(
  @JvmField val relativizer: PathTypeAwareRelativizer,
  actualDigestMap: Map<Path, ByteArray>,
  private val sourceToDescriptor: HashMap<Path, SourceDescriptor>,
  @JvmField val storeFile: Path,
  @JvmField val allocator: RootAllocator,
) : BuildDataProvider {
  private val stamp = BazelStampStorage(actualDigestMap = actualDigestMap, map = sourceToDescriptor)

  @JvmField val sourceToOutputMapping = BazelSourceToOutputMapping(map = sourceToDescriptor, relativizer = relativizer)

  private val sourceToForm = object : OneToManyPathMapping {
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
    val list = synchronized(sourceToDescriptor) {
      sourceToDescriptor.values.toTypedArray()
    }
    list.sortBy { it.sourceFile }
    return list
  }

  override fun clearCache() {
  }

  override fun removeStaleTarget(targetId: String, targetTypeId: String) {
  }

  override fun getFileStampStorage(target: BuildTarget<*>): BazelStampStorage = stamp

  override fun getSourceToOutputMapping(target: BuildTarget<*>): BazelSourceToOutputMapping = sourceToOutputMapping

  override fun getOutputToTargetMapping(): OutputToTargetMapping = throw UnsupportedOperationException("Must not be used")

  override fun getSourceToForm(target: BuildTarget<*>): OneToManyPathMapping = sourceToForm

  override fun closeTargetMaps(target: BuildTarget<*>) {
  }

  override fun removeAllMaps() {
  }

  override fun commit() {
  }

  override fun close() {
  }
}

internal class BazelStampStorage(
  private val actualDigestMap: Map<Path, ByteArray>,
  private val map: HashMap<Path, SourceDescriptor>,
) : StampsStorage<ByteArray> {
  override fun updateStamp(sourceFile: Path, buildTarget: BuildTarget<*>?, currentFileTimestamp: Long) {
    val actualDigest = requireNotNull(actualDigestMap.get(sourceFile)) {
      "No digest is provided for $sourceFile"
    }

    synchronized(map) {
      map.computeIfAbsent(sourceFile) { SourceDescriptor(sourceFile = it) }.digest = actualDigest
    }
  }

  override fun removeStamp(sourceFile: Path, buildTarget: BuildTarget<*>?) {
    //synchronized(map) {
    //  map.get(sourceFile)?.digest = null
    //}
    throw UnsupportedOperationException("Must not be used")
  }

  override fun getCurrentStampIfUpToDate(file: Path, buildTarget: BuildTarget<*>?, attrs: BasicFileAttributes?): ByteArray? {
    //val key = createKey(file)
    //val storedDigest = synchronized(map) { map.get(key)?.digest } ?: return null
    //val actualDigest = actualDigestMap.get(file) ?: return null
    //return actualDigest.takeIf { storedDigest.contentEquals(actualDigest) }
    throw UnsupportedOperationException("Must not be used")
  }

  fun isDirty(sourceFile: Path): Boolean {
    val storedDigest = synchronized(map) { map.get(sourceFile)?.digest } ?: return true
    val actualDigest = actualDigestMap.get(sourceFile) ?: return true
    return !storedDigest.contentEquals(actualDigest)
  }
}

internal class BazelSourceToOutputMapping(
  private val map: HashMap<Path, SourceDescriptor>,
  private val relativizer: PathTypeAwareRelativizer,
) : SourceToOutputMapping {
  override fun setOutputs(sourceFile: Path, outputPaths: List<Path>) {
    val relativeOutputPaths = if (outputPaths.isEmpty()) null else outputPaths.map { relativizer.toRelative(it, RelativePathType.OUTPUT) }
    synchronized(map) {
      val value = if (relativeOutputPaths == null) {
        map.get(sourceFile) ?: return
      }
      else {
        map.computeIfAbsent(sourceFile) { SourceDescriptor(sourceFile = it) }
      }

      value.outputs = relativeOutputPaths
    }
  }

  override fun appendOutput(sourcePath: String, outputPath: String) {
    val sourceFile = Path.of(sourcePath)
    val relativeOutputPath = relativizer.toRelative(outputPath, RelativePathType.OUTPUT)
    synchronized(map) {
      val sourceInfo = map.computeIfAbsent(sourceFile) { SourceDescriptor(sourceFile = it) }
      val existingOutputs = sourceInfo.outputs
      if (existingOutputs == null) {
        sourceInfo.outputs = java.util.List.of(relativeOutputPath)
      }
      else if (!existingOutputs.contains(relativeOutputPath)) {
        sourceInfo.outputs = existingOutputs + relativeOutputPath
      }
    }
  }

  override fun remove(sourceFile: Path) {
    synchronized(map) {
      map.get(sourceFile)?.outputs = null
    }
  }

  override fun removeOutput(sourcePath: String, outputPath: String) {
    val sourceFile = Path.of(sourcePath)
    val relativeOutputPath = relativizer.toRelative(outputPath, RelativePathType.OUTPUT)
    synchronized(map) {
      val sourceInfo = map.get(sourceFile) ?: return
      val existingOutputs = sourceInfo.outputs ?: return
      if (existingOutputs.contains(relativeOutputPath)) {
        if (existingOutputs.size == 1) {
          sourceInfo.outputs = null
        }
        else {
          sourceInfo.outputs = existingOutputs - relativeOutputPath
        }
      }
    }
  }

  override fun getOutputs(sourcePath: String): Collection<String>? {
    val sourceFile = Path.of(sourcePath)
    return synchronized(map) { map.get(sourceFile)?.outputs }?.map { relativizer.toAbsolute(it, RelativePathType.OUTPUT) }
  }

  override fun getOutputs(sourceFile: Path): Collection<Path>? {
    return synchronized(map) { map.get(sourceFile)?.outputs }?.map { relativizer.toAbsoluteFile(it, RelativePathType.OUTPUT) }
  }

  fun getDescriptor(sourceFile: Path): SourceDescriptor? {
    return synchronized(map) { map.get(sourceFile) }
  }

  fun findAffectedSources(affectedSources: List<List<String>>): List<Path> {
    val result = ArrayList<Path>(affectedSources.size)
    synchronized(map) {
      for (descriptor in map.values) {
        val outputs = descriptor.outputs ?: continue
        for (output in outputs) {
          if (affectedSources.any { it.contains(output) }) {
            result.add(descriptor.sourceFile)
            break
          }
        }
      }
    }
    return result
  }

  fun findDeletedAndUnsetDigest(known: Map<Path, *>): List<Path> {
    synchronized(map) {
      return map.values.asSequence()
        .filter { !known.containsKey(it.sourceFile) }
        .onEach { it.digest = null }
        .map { it.sourceFile }
        .toList()
    }
  }

  override fun getSourceFileIterator(): Iterator<Path> {
    throw UnsupportedOperationException("Must not be used")
    //return synchronized(map) { map.keys.toTypedArray() }.asSequence()
    //  .map { relativizer.toAbsoluteFile(it, RelativePathType.SOURCE) }
    //  .iterator()
  }

  override fun getSourcesIterator(): Iterator<String> {
    throw UnsupportedOperationException("Must not be used")
    //return synchronized(map) { map.keys.toTypedArray() }.asSequence()
    //  .map { relativizer.toAbsolute(it, RelativePathType.SOURCE) }
    //  .iterator()
  }

  override fun cursor(): SourceToOutputMappingCursor {
    throw UnsupportedOperationException("Must not be used")
  }
}