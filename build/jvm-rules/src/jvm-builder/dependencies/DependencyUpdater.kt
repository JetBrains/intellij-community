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
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.bazel.jvm.worker.impl.markAffectedFilesDirty
import org.jetbrains.bazel.jvm.worker.state.DependencyDescriptor
import org.jetbrains.bazel.jvm.worker.state.DependencyState
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.util.emptySet
import org.jetbrains.bazel.jvm.worker.core.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
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
  val usedAbiDir = context.projectDescriptor.dataManager.dataPaths.dataStorageDir.resolve("used-abi")

  val changedOrAddedNodes = MutableScatterMap<AbiJarSource, NodeUpdateItem>()
  val deletedNodes = MutableScatterMap<AbiJarSource, NodeUpdateItem>()
  val filesToCopy = MutableObjectList<Pair<Path, Path>>()
  dataProvider.libRootManager.state.dependencies.forEach { descriptor ->
    if (descriptor.state == DependencyState.DELETED) {
      throw IllegalStateException("Deleted dependency should not be present in the state: $descriptor")
    }
    else if (descriptor.state != DependencyState.UNCHANGED) {
      changedOrAdded.add(descriptor)
      val newFile = descriptor.file
      val relativePath = relativizer.toRelative(newFile, RelativePathType.SOURCE)
      val oldFile = usedAbiDir.resolve(computeCacheName(relativePath))
      filesToCopy.add(newFile to oldFile)
      val result = dependencyAnalyzer.computeForChanged(
        dependencyDescriptor = descriptor,
        oldFile = oldFile,
        newFile = newFile,
        fileBazelDigestHash = Hashing.xxh3_64().hashBytesToLong(descriptor.digest)
      )
      changedOrAddedNodes.putAll(result.first)
      deletedNodes.putAll(result.second)
    }
  }

  if (changedOrAddedNodes.isEmpty() && deletedNodes.isEmpty()) {
    return
  }

  val graph = context.projectDescriptor.dataManager.depGraph as DependencyGraphImpl
  @Suppress("InconsistentCommentForJavaParameter", "RedundantSuppression")
  val delta = AbiDeltaImpl(
    baseSources = toSourceSet(changedOrAddedNodes),
    changedOrAddedNodes = changedOrAddedNodes,
    deletedSources = toSourceSet(deletedNodes),
    depGraph = graph,
  )

  // compute delta indexes - it is used for graph.differentiate (some differentiation rules can use delta indexes)
  tracer.span("associate dependencies") {
    changedOrAddedNodes.forEach { source, item ->
      delta.associateNodes(source, item.newNode!!)
    }
  }

  val isRebuild = context.scope.isRebuild

  val allProcessedSources = MutableScatterSet<NodeSource>(changedOrAdded.size + deletedNodes.size)
  changedOrAddedNodes.forEachKey { allProcessedSources.add(it) }
  deletedNodes.forEachKey { allProcessedSources.add(it) }

  val nodesAfter = ObjectOpenCustomHashSet<Node<*, *>>(changedOrAddedNodes.size, DiffCapableHashStrategy)
  changedOrAddedNodes.forEachValue { info ->
    nodesAfter.add(info.newNode ?: return@forEachValue)
  }

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
  private val changedOrAddedNodes: MutableScatterMap<AbiJarSource, NodeUpdateItem>,
  private val depGraph: Graph,
) : Graph, Delta {
  private val subclassesIndex = SubclassesIndex(mapletFactory = memoryFactory, isInMemory = true)
  private val indices: List<BackDependencyIndex> = java.util.List.of(subclassesIndex)

  private val nodeIdToSourcesMap = MutableScatterMap<ReferenceID, ObjectOpenHashSet<NodeSource>>()

  override fun getDependingNodes(id: ReferenceID): Iterable<ReferenceID> = emptyList()

  override fun getIndices(): List<BackDependencyIndex> = indices

  override fun getIndex(name: String) = if (name == SubclassesIndex.NAME) subclassesIndex else null

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

  fun associateNodes(source: NodeSource, node: Node<*, *>) {
    nodeIdToSourcesMap.compute(node.referenceID) { _, v -> v ?: ObjectOpenHashSet() }.add(source)

    // deduce dependencies - only subclassesIndex because lib node never has usages
    subclassesIndex.indexNode(node)
  }
}

private fun computeCacheName(relativePath: String): String {
  // get the last two path segments
  var slashIndex = relativePath.lastIndexOf('/')
  relativePath.lastIndexOf('/', startIndex = slashIndex - 1).also { index ->
    if (index != -1) {
      slashIndex = index
    }
  }

  val cacheFilename = relativePath.substring(slashIndex + 1, relativePath.length - 4).replace('/', '_') + '-' +
    java.lang.Long.toUnsignedString(Hashing.xxh3_64().hashBytesToLong(relativePath.toByteArray()), Character.MAX_RADIX) +
    ".jar"
  return cacheFilename
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