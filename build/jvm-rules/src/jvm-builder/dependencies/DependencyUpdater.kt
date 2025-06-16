// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch", "unused", "UnstableApiUsage", "ReplaceGetOrSet", "SSBasedInspection", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.worker.dependencies

import androidx.collection.MutableObjectList
import androidx.collection.MutableScatterMap
import androidx.collection.MutableScatterSet
import androidx.collection.ObjectList
import androidx.collection.ScatterMap
import com.dynatrace.hash4j.hashing.Hashing
import com.sun.nio.file.ExtendedOpenOption
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.h2.mvstore.MVMap
import org.h2.mvstore.type.ByteArrayDataType
import org.jetbrains.bazel.jvm.mvStore.ModernStringDataType
import org.jetbrains.bazel.jvm.mvStore.MvStoreMapFactory
import org.jetbrains.bazel.jvm.worker.impl.markAffectedFilesDirty
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.util.emptySet
import org.jetbrains.bazel.jvm.util.toLinkedSet
import org.jetbrains.bazel.jvm.worker.INCREMENTAL_CACHE_DIRECTORY_SUFFIX
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.worker.core.BazelStampStorage
import org.jetbrains.bazel.jvm.worker.storage.StorageInitializer
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.Graph
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder
import org.jetbrains.jps.dependency.impl.memoryFactory
import org.jetbrains.jps.dependency.java.DiffCapableHashStrategy
import org.jetbrains.jps.dependency.java.SubclassesIndex
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.collections.contentEquals
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name

// for now, ADDED or DELETED not possible - in configureClasspath we compute DEPENDENCY_PATH_LIST,
// so, if a dependency list is changed, then we perform rebuild
private enum class DependencyState {
  CHANGED, ADDED, DELETED
}

internal data class NodeUpdateItem(
  // null if deleted
  @JvmField val newNode: Node<*, *>?,
  // null if added
  @JvmField val oldNode: Node<*, *>?,
  @JvmField val source: AbiJarSource,
)

/**
 * @return true if you can continue incrementally, false if non-incremental
 */
internal suspend fun checkDependencies(
  context: BazelCompileContext,
  target: BazelModuleBuildTarget,
  relativizer: PathTypeAwareRelativizer,
  tracer: Tracer,
  stampStorage: BazelStampStorage,
  dependencyAnalyzer: DependencyAnalyzer,
  dependencyFileToDigest: ScatterMap<Path, ByteArray>,
  span: Span,
  asyncTaskScope: CoroutineScope,
) {
  val trackableDependencyFiles = target.module.container.getChild(BazelConfigurationHolder.KIND).trackableDependencyFiles

  val changedOrAdded = MutableObjectList<DependencyDescriptor>()
  val dataManager = context.projectDescriptor.dataManager
  val usedAbiDir = dataManager.dataPaths.dataStorageDir
  val filesToCopy = MutableObjectList<FileTask>()

  val isRebuild = context.scope.isRebuild
  if (isRebuild) {
    filesToCopy.ensureCapacity(trackableDependencyFiles.size)

    val toAdd = ArrayList<Pair<String, ByteArray>>(trackableDependencyFiles.size)
    trackableDependencyFiles.forEach { file ->
      val digest = requireNotNull(dependencyFileToDigest.get(file)) { "cannot find actual digest for $file" }
      val relativePath = relativizer.toRelative(file, RelativePathType.SOURCE)
      toAdd.add(relativePath to digest)
      filesToCopy.add(FileTask(originalFile = file, linkFile = usedAbiDir.resolve(computeCacheName(relativePath, digest)), toDelete = null))
    }
    asyncTaskScope.launch {
      copyUsedAbi(usedAbiDir = usedAbiDir, filesToCopy = filesToCopy, isRebuild = false)
    }

    initMap(dataManager.containerFactory.mvstoreMapFactory, toAdd)
  }
  else {
    val changedOrAddedNodes = MutableScatterMap<AbiJarSource, NodeUpdateItem>()
    val deletedNodes = MutableScatterMap<AbiJarSource, NodeUpdateItem>()
    updateMapAndComputeDiff(
      dependencyFileToDigest = dependencyFileToDigest,
      mvstoreMapFactory = dataManager.containerFactory.mvstoreMapFactory,
      relativizer = relativizer,
      trackableDependencyFiles = trackableDependencyFiles,
    ) { descriptor, relativePath, state ->
      changedOrAdded.add(descriptor)
      val oldDigest = descriptor.oldDigest
      if (oldDigest != null) {
        val newFile = descriptor.file
        val newDigest = descriptor.digest!!
        val cachedOldFile = usedAbiDir.resolve(computeCacheName(relativePath, oldDigest))
        filesToCopy.add(FileTask(
          originalFile = newFile,
          linkFile = usedAbiDir.resolve(computeCacheName(relativePath, newDigest)),
          toDelete = cachedOldFile,
        ))
        val result = dependencyAnalyzer.computeForChanged(
          dependencyDescriptor = descriptor,
          oldFile = cachedOldFile,
          newFile = newFile,
          fileBazelDigestHash = Hashing.xxh3_64().hashBytesToLong(newDigest),
        )
        changedOrAddedNodes.putAll(result.first)
        deletedNodes.putAll(result.second)
      }
    }

    if (changedOrAddedNodes.isEmpty() && deletedNodes.isEmpty()) {
      return
    }

    asyncTaskScope.launch {
      copyUsedAbi(usedAbiDir = usedAbiDir, filesToCopy = filesToCopy, isRebuild = true)
    }

    markAffectedByLibChange(
      graph = dataManager.depGraph as DependencyGraphImpl,
      changedOrAddedNodes = changedOrAddedNodes,
      deletedNodes = deletedNodes,
      changedOrAdded = changedOrAdded,
      tracer = tracer,
      context = context,
      stampStorage = stampStorage,
      target = target,
      relativizer = relativizer,
      span = span,
    )
  }
}

private data class FileTask(
  @JvmField val originalFile: Path,
  @JvmField val linkFile: Path,
  @JvmField val toDelete: Path?,
)

private suspend fun copyUsedAbi(usedAbiDir: Path?, filesToCopy: MutableObjectList<FileTask>, isRebuild: Boolean) {
  try {
    withContext(Dispatchers.IO.limitedParallelism(8) + NonCancellable) {
      filesToCopy.forEach { item ->
        launch {
          if (item.toDelete != null) {
            Files.deleteIfExists(item.toDelete)
          }
          if (!tryCreateLink(item.linkFile, item.originalFile)) {
            val dataDir = item.originalFile.resolveSibling(item.originalFile.name.removeSuffix(".abi.jar") + INCREMENTAL_CACHE_DIRECTORY_SUFFIX)
            createLinkAfterCopy(item.linkFile, item.originalFile, StorageInitializer.getTrashDirectory(dataDir).createDirectories())
          }
        }
      }
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    throw RebuildRequestedException(RuntimeException("cannot integrate ABI", e))
  }
}

private fun createLinkAfterCopy(linkFile: Path, originalFile: Path, tempDir: Path) {
  var index = 1
  do {
    val copyFile = tempDir.resolve(linkFile.name + "-${index++}")
    if (!copyFile.exists()) {
      var tempFile = Files.createTempFile(tempDir, null, null)
      try {
        Files.newOutputStream(tempFile, ExtendedOpenOption.NOSHARE_WRITE).use {
          Files.copy(originalFile, it)
        }

        try {
          Files.move(tempFile, copyFile, StandardCopyOption.ATOMIC_MOVE)
          tempFile = null
        }
        catch (_: AccessDeniedException) {
          // ATOMIC_MOVE uses MOVEFILE_REPLACE_EXISTING, ignore
        }
        catch (_: FileAlreadyExistsException) {
          // ignore
        }
      }
      finally {
        if (tempFile != null) {
          Files.delete(tempFile)
        }
      }
    }
  } while (!tryCreateLink(linkFile, copyFile))
}

private const val WINDOWS_ERROR_TOO_MANY_LINKS = "An attempt was made to create more links on a file than the file system supports"

private fun tryCreateLink(link: Path, existing: Path): Boolean {
  return try {
    Files.createLink(link, existing)
    true
  }
  catch (e: FileSystemException) {
    if (e.message?.endsWith(WINDOWS_ERROR_TOO_MANY_LINKS) == true) {
      false
    }
    else {
      throw e
    }
  }
}

private const val MAP_NAME = "dependencies"

private fun initMap(mvstoreMapFactory: MvStoreMapFactory, toAdd: MutableList<Pair<String, ByteArray>>) {
  val map = mvstoreMapFactory.openMap(MAP_NAME, singleWriterMapBuilder)
  require(map.isEmpty()) { "map should be empty" }
  toAdd.sortBy { it.first }
  for ((k, v) in toAdd) {
    map.append(k, v)
  }
}

private suspend fun markAffectedByLibChange(
  graph: DependencyGraphImpl,
  changedOrAddedNodes: ScatterMap<AbiJarSource, NodeUpdateItem>,
  deletedNodes: ScatterMap<AbiJarSource, NodeUpdateItem>,
  changedOrAdded: ObjectList<DependencyDescriptor>,
  tracer: Tracer,
  context: BazelCompileContext,
  stampStorage: BazelStampStorage,
  target: BazelModuleBuildTarget,
  relativizer: PathTypeAwareRelativizer,
  span: Span,
) {
  val nodeIdToSourcesMap = MutableScatterMap<ReferenceID, MutableSet<NodeSource>>(changedOrAddedNodes.size)

  @Suppress("InconsistentCommentForJavaParameter", "RedundantSuppression")
  val delta = AbiDeltaImpl(
    baseSources = toSourceSet(changedOrAddedNodes),
    deletedSources = toSourceSet(deletedNodes),
    nodeIdToSourcesMap = nodeIdToSourcesMap,
    subclassesIndex = lazy {
      val subclassesIndex = SubclassesIndex(mapletFactory = memoryFactory, isInMemory = true)
      // deduce dependencies - only subclassesIndex because lib node never has usages
      changedOrAddedNodes.forEachValue { item ->
        subclassesIndex.indexNode(item.newNode!!)
      }
      subclassesIndex
    }
  )

  val allProcessedSources = MutableScatterSet<NodeSource>(changedOrAdded.size + deletedNodes.size)
  val nodesAfter = ObjectOpenCustomHashSet<Node<*, *>>(changedOrAddedNodes.size, DiffCapableHashStrategy)

  // compute delta indexes - it is used for graph.differentiate (some differentiation rules can use delta indexes)
  tracer.span("associate dependencies") {
    changedOrAddedNodes.forEach { source, item ->
      val newNode = item.newNode!!

      nodeIdToSourcesMap.compute(newNode.referenceID) { _, v -> v ?: ObjectArraySet() }.add(source)
      allProcessedSources.add(source)
      nodesAfter.add(newNode)
    }
  }

  deletedNodes.forEachKey { allProcessedSources.add(it) }

  val diffResult = graph.differentiate(
    delta = delta,
    allProcessedSources = allProcessedSources.asSet(),
    nodesAfter = nodesAfter,
    getBeforeNodes = { source ->
      if (source is AbiJarSource) {
        source.oldNodes
      }
      else {
        graph.getNodes(source)
      }
    },
    params = DifferentiateParametersBuilder.create("deps").calculateAffected(true).get(),
  )
  if (!diffResult.isIncremental) {
    throw RebuildRequestedException(RuntimeException("diffResult is non-incremental: $diffResult"))
  }

  val affectedSources = diffResult.affectedSources
  val affectedCount = affectedSources.count()
  span.addEvent("affected files by lib tracking", Attributes.of(AttributeKey.longKey("count"), affectedCount.toLong()))
  if (affectedCount > 0) {
    markAffectedFilesDirty(
      context = context,
      stampStorage = stampStorage,
      target = target,
      affectedFiles = affectedSources.asSequence().map { relativizer.toAbsoluteFile(it.toString(), RelativePathType.SOURCE) },
    )
  }
}

// please note - in configureClasspath we compute UNTRACKED_DEPENDENCY_DIGEST_LIST,
// meaning that some files in the classpath are untracked,
// and any change of the untracked dependency leads to rebuild (this method will not be called)
private suspend fun updateMapAndComputeDiff(
  dependencyFileToDigest: ScatterMap<Path, ByteArray>,
  mvstoreMapFactory: MvStoreMapFactory,
  relativizer: PathTypeAwareRelativizer,
  trackableDependencyFiles: ObjectList<Path>,
  consumer: suspend (DependencyDescriptor, String, DependencyState) -> Unit,
) {
  val map = mvstoreMapFactory.openMap(MAP_NAME, mapBuilder)

  val newFiles = trackableDependencyFiles.toLinkedSet()
  val cursor = map.cursor(null)
  while (cursor.hasNext()) {
    val relativePath = cursor.next()
    val oldDigest = cursor.value

    val file = relativizer.toAbsoluteFile(relativePath, RelativePathType.SOURCE)
    val currentDigest = dependencyFileToDigest.get(file)
    if (currentDigest == null) {
      require(map.remove(relativePath, oldDigest)) {
        "Failed to remove dependency $relativePath (oldDigest=$oldDigest) (concurrent modification?)"
      }

      throw IllegalStateException("Deleted dependency should not be present in the state: $relativePath")
    }
    else {
      newFiles.remove(file)
      if (!currentDigest.contentEquals(oldDigest)) {
        require(map.replace(relativePath, oldDigest, currentDigest)) {
          "Failed to replace dependency $relativePath (oldDigest=$oldDigest, currentDigest=$currentDigest) (concurrent modification?)"
        }

        consumer(DependencyDescriptor(file = file, digest = currentDigest, oldDigest = oldDigest), relativePath, DependencyState.CHANGED)
      }
    }
  }

  if (!newFiles.isEmpty()) {
    for (file in newFiles) {
      val digest = requireNotNull(dependencyFileToDigest.get(file)) { "cannot find actual digest for $file" }
      val relativePath = relativizer.toRelative(file, RelativePathType.SOURCE)
      require(map.putIfAbsent(relativePath, digest) == null) {
        "Failed to put dependency $file (digest=$digest) (concurrent modification?)"
      }
      consumer(DependencyDescriptor(file = file, digest = digest, oldDigest = null), relativePath, DependencyState.ADDED)
    }
  }
}

private fun createMapBuilder(): MVMap.Builder<String, ByteArray> {
  return MVMap.Builder<String, ByteArray>()
    .keyType(ModernStringDataType)
    .valueType(ByteArrayDataType.INSTANCE)
}

private val mapBuilder = createMapBuilder()
private val singleWriterMapBuilder = createMapBuilder().singleWriter()

private fun toSourceSet(changedOrAddedNodes: ScatterMap<AbiJarSource, NodeUpdateItem>): Set<NodeSource> {
  if (changedOrAddedNodes.isEmpty()) {
    return emptySet()
  }

  return ObjectOpenHashSet<NodeSource>(changedOrAddedNodes.size).also { result ->
    changedOrAddedNodes.forEachKey { result.add(it) }
  }
}

private class AbiDeltaImpl(
  private val baseSources: Set<NodeSource>,
  private val deletedSources: Set<NodeSource>,
  private val nodeIdToSourcesMap: MutableScatterMap<ReferenceID, MutableSet<NodeSource>>,
  private val subclassesIndex: Lazy<SubclassesIndex>,
) : Graph, Delta {
  private val indices = lazy(LazyThreadSafetyMode.NONE) { java.util.List.of(subclassesIndex.value) }

  override fun getDependingNodes(id: ReferenceID): Iterable<ReferenceID> = emptyList()

  override fun getIndices(): List<BackDependencyIndex> = indices.value

  override fun getIndex(name: String) = if (name == SubclassesIndex.NAME) subclassesIndex.value else null

  override fun getSources(id: ReferenceID): Iterable<NodeSource> {
    return nodeIdToSourcesMap.get(id) ?: emptySet()
  }

  override fun getRegisteredNodes() = throw UnsupportedOperationException("Not used for deltas")

  override fun getSources() = baseSources

  override fun getBaseSources(): Set<NodeSource> = baseSources

  override fun getNodes(source: NodeSource): Iterable<Node<*, *>> {
    return (source as AbiJarSource).newNodes
  }

  override fun isSourceOnly(): Boolean = false

  override fun getDeletedSources(): Set<NodeSource> = deletedSources

  override fun associate(node: Node<*, *>, sources: Iterable<NodeSource>) = throw UnsupportedOperationException()
}

private fun computeCacheName(relativePath: String, fileBazelDigestHash: ByteArray): String {
  // get the last two path segments
  var slashIndex = relativePath.lastIndexOf('/')
  relativePath.lastIndexOf('/', startIndex = slashIndex - 1).also { index ->
    if (index != -1) {
      slashIndex = index
    }
  }

  // use a "z-" prefix to ensure that in the directory view it is listed last in a one group and denote from ordinal jar files
  return "z-" + relativePath.substring(slashIndex + 1, relativePath.length - 4).replace('/', '_') + '-' +
    longToString(Hashing.xxh3_64().hashStream().putByteArray(relativePath.toByteArray()).putByteArray(fileBazelDigestHash).asLong) +
    ".jar"
}

internal class AbiJarSource(
  private val fileBazelDigestHash: Long,
  private val innerPathHash: Long,

  @JvmField val oldNodes: Set<Node<*, *>>,
  @JvmField val newNodes: Set<Node<*, *>>,
) : NodeSource {
  override fun write(out: GraphDataOutput) {
    throw UnsupportedOperationException("Not used for a new store")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return other is AbiJarSource && fileBazelDigestHash == other.fileBazelDigestHash && innerPathHash == other.innerPathHash
  }

  override fun toString(): String {
    return longToString(fileBazelDigestHash) + '-' + longToString(innerPathHash)
  }

  override fun hashCode(): Int = 31 * fileBazelDigestHash.toInt() + innerPathHash.toInt()
}

private fun longToString(value: Long): String? = java.lang.Long.toUnsignedString(value, Character.MAX_RADIX)