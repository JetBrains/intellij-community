// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch", "unused", "UnstableApiUsage", "ReplaceGetOrSet", "SSBasedInspection", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.worker.dependencies

import androidx.collection.MutableObjectList
import androidx.collection.MutableScatterMap
import androidx.collection.MutableScatterSet
import com.dynatrace.hash4j.hashing.Hashing
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.h2.mvstore.MVMap
import org.h2.mvstore.type.ByteArrayDataType
import org.jetbrains.bazel.jvm.mvStore.ModernStringDataType
import org.jetbrains.bazel.jvm.mvStore.MvStoreMapFactory
import org.jetbrains.bazel.jvm.worker.impl.markAffectedFilesDirty
import org.jetbrains.bazel.jvm.worker.state.DependencyDescriptor
import org.jetbrains.bazel.jvm.worker.state.DependencyState
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.util.emptySet
import org.jetbrains.bazel.jvm.util.toLinkedSet
import org.jetbrains.bazel.jvm.worker.core.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.worker.state.isDependencyTracked
import org.jetbrains.jps.ModuleChunk
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.collections.contentEquals
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

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
  chunk: ModuleChunk,
  target: BazelModuleBuildTarget,
  relativizer: PathTypeAwareRelativizer,
  span: Span,
  tracer: Tracer,
  dataProvider: BazelBuildDataProvider,
  dependencyAnalyzer: DependencyAnalyzer,
) {
  val changedOrAdded = MutableObjectList<DependencyDescriptor>()
  val dataManager = context.projectDescriptor.dataManager
  val usedAbiDir = dataManager.dataPaths.dataStorageDir.resolve("used-abi")

  val changedOrAddedNodes = MutableScatterMap<AbiJarSource, NodeUpdateItem>()
  val deletedNodes = MutableScatterMap<AbiJarSource, NodeUpdateItem>()
  val filesToCopy = MutableObjectList<Pair<Path, Path>>()
  updateMapAndComputeDiff(
    dataProvider = dataProvider,
    mvstoreMapFactory = dataManager.containerFactory.mvstoreMapFactory,
    relativizer = relativizer,
  ) { descriptor, state ->
    require(state != DependencyState.DELETED)

    changedOrAdded.add(descriptor)
    val newFile = descriptor.file
    val relativePath = relativizer.toRelative(newFile, RelativePathType.SOURCE)
    val oldFile = usedAbiDir.resolve(computeCacheName(relativePath))
    filesToCopy.add(newFile to oldFile)
    val result = dependencyAnalyzer.computeForChanged(
      dependencyDescriptor = descriptor,
      oldFile = oldFile,
      newFile = newFile,
      fileBazelDigestHash = Hashing.xxh3_64().hashBytesToLong(descriptor.digest),
    )
    changedOrAddedNodes.putAll(result.first)
    deletedNodes.putAll(result.second)
  }

  if (changedOrAddedNodes.isEmpty() && deletedNodes.isEmpty()) {
    return
  }

  val graph = dataManager.depGraph as DependencyGraphImpl
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

  val isRebuild = context.scope.isRebuild

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
    params = DifferentiateParametersBuilder.create("deps").calculateAffected(!isRebuild).get(),
  )
  if (!isRebuild) {
    if (!diffResult.isIncremental) {
      throw RebuildRequestedException(RuntimeException("diffResult is non-incremental: $diffResult"))
    }

    val affectedSources = diffResult.affectedSources
    val affectedCount = affectedSources.count()
    span.addEvent("affected files by lib tracking", Attributes.of(AttributeKey.longKey("count"), affectedCount.toLong()))
    if (affectedCount > 0) {
      markAffectedFilesDirty(
        context = context,
        dataProvider = dataProvider,
        target = target,
        affectedFiles = affectedSources.asSequence().map { relativizer.toAbsoluteFile(it.toString(), RelativePathType.SOURCE) },
      )
    }
  }

  coroutineContext.ensureActive()
  try {
    withContext(Dispatchers.IO + NonCancellable) {
      Files.createDirectories(usedAbiDir)
      filesToCopy.forEach { (newFile, oldFile) ->
        launch {
          if (!isRebuild) {
            Files.deleteIfExists(oldFile)
          }
          Files.createLink(oldFile, newFile)
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

// please note - in configureClasspath we compute UNTRACKED_DEPENDENCY_DIGEST_LIST,
// meaning that some files in the classpath are untracked,
// and any change of the untracked dependency leads to rebuild (this method will not be called)
private suspend fun updateMapAndComputeDiff(
  dataProvider: BazelBuildDataProvider,
  mvstoreMapFactory: MvStoreMapFactory,
  relativizer: PathTypeAwareRelativizer,
  consumer: suspend (DependencyDescriptor, DependencyState) -> Unit,
) {
  val map = mvstoreMapFactory.openMap("dependencies", MVMap.Builder<String, ByteArray>()
    .keyType(ModernStringDataType)
    .valueType(ByteArrayDataType.INSTANCE))

  val dependencyFileToDigest = dataProvider.libRootManager.dependencyFileToDigest

  val newFiles = dataProvider.libRootManager.trackableDependencyFiles.toLinkedSet()
  val cursor = map.cursor(null)
  while (cursor.hasNext()) {
    val path = cursor.next()
    if (!isDependencyTracked(path)) {
      continue
    }

    val oldDigest = cursor.value

    val file = relativizer.toAbsoluteFile(path, RelativePathType.SOURCE)
    val currentDigest = dependencyFileToDigest.get(file)
    if (currentDigest == null) {
      require(map.remove(path, oldDigest)) {
        "Failed to remove dependency $path (oldDigest=$oldDigest) (concurrent modification?)"
      }

      throw IllegalStateException("Deleted dependency should not be present in the state: $path")
    }
    else {
      newFiles.remove(file)
      if (!currentDigest.contentEquals(oldDigest)) {
        require(map.replace(path, oldDigest, currentDigest)) {
          "Failed to replace dependency $path (oldDigest=$oldDigest, currentDigest=$currentDigest) (concurrent modification?)"
        }

        consumer(DependencyDescriptor(file = file, digest = currentDigest, oldDigest = oldDigest), DependencyState.CHANGED)
      }
    }
  }

  if (!newFiles.isEmpty()) {
    for (file in newFiles) {
      val digest = requireNotNull(dependencyFileToDigest.get(file)) { "cannot find actual digest for $file" }
      require(map.putIfAbsent(relativizer.toRelative(file, RelativePathType.SOURCE), digest) == null) {
        "Failed to put dependency $file (digest=$digest) (concurrent modification?)"
      }
      consumer(DependencyDescriptor(file = file, digest = digest, oldDigest = null), DependencyState.ADDED)
    }
  }
}

private fun toSourceSet(changedOrAddedNodes: MutableScatterMap<AbiJarSource, NodeUpdateItem>): Set<NodeSource> {
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

private fun computeCacheName(relativePath: String): String {
  // get the last two path segments
  var slashIndex = relativePath.lastIndexOf('/')
  relativePath.lastIndexOf('/', startIndex = slashIndex - 1).also { index ->
    if (index != -1) {
      slashIndex = index
    }
  }

  return relativePath.substring(slashIndex + 1, relativePath.length - 4).replace('/', '_') + '-' +
    java.lang.Long.toUnsignedString(Hashing.xxh3_64().hashBytesToLong(relativePath.toByteArray()), Character.MAX_RADIX) +
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
    return java.lang.Long.toUnsignedString(fileBazelDigestHash, Character.MAX_RADIX) + '-' +
      java.lang.Long.toUnsignedString(innerPathHash, Character.MAX_RADIX)
  }

  override fun hashCode(): Int = 31 * fileBazelDigestHash.toInt() + innerPathHash.toInt()
}