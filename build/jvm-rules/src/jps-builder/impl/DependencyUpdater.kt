@file:Suppress("PackageDirectoryMismatch", "unused", "UnstableApiUsage", "ReplaceGetOrSet", "SSBasedInspection", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.jps.incremental.dependencies

import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import io.netty.buffer.Unpooled
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.jetbrains.bazel.jvm.emptySet
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.fileToNodeSource
import org.jetbrains.bazel.jvm.jps.impl.markAffectedFilesDirty
import org.jetbrains.bazel.jvm.jps.output.ABI_IC_NODE_FORMAT_VERSION
import org.jetbrains.bazel.jvm.jps.state.DependencyDescriptor
import org.jetbrains.bazel.jvm.jps.state.DependencyState
import org.jetbrains.bazel.jvm.span
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.impl.DeltaImpl
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder
import org.jetbrains.jps.dependency.storage.NettyBufferGraphDataInput
import org.jetbrains.jps.dependency.impl.PathSource
import org.jetbrains.jps.dependency.java.JvmClass
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

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
) {
  val deleted = ArrayList<DependencyDescriptor>()
  val changedOrAdded = ArrayList<DependencyDescriptor>()
  for (descriptor in dataProvider.libRootManager.state.dependencies) {
    if (descriptor.state == DependencyState.DELETED) {
      deleted.add(descriptor)
    }
    else if (descriptor.state != DependencyState.UNCHANGED) {
      changedOrAdded.add(descriptor)
    }
  }
  if (changedOrAdded.isEmpty() && deleted.isEmpty()) {
    return
  }

  val graph = context.projectDescriptor.dataManager.depGraph
  val changedOrAddedNodes = toNodeSet(changedOrAdded, relativizer)
  val deletedNodes = toNodeSet(deleted, relativizer)
  @Suppress("InconsistentCommentForJavaParameter", "RedundantSuppression")
  val delta = DeltaImpl(baseSources = changedOrAddedNodes, deletedSources = deletedNodes)
  tracer.span("associate dependencies") {
    associate(
      changedOrAddedNodes = changedOrAddedNodes,
      changedOrAdded = changedOrAdded,
      delta = delta,
      dependencyAnalyzer = context.scope.dependencyAnalyzer!!,
    )
  }

  val isRebuild = context.scope.isRebuild
  val diffResult = graph.differentiate(delta, DifferentiateParametersBuilder.create("deps").calculateAffected(!isRebuild).get())
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

  graph.integrate(diffResult)
}

private fun toNodeSet(
  descriptors: ArrayList<DependencyDescriptor>,
  relativizer: PathTypeAwareRelativizer
): Set<PathSource> {
  val size = descriptors.size
  when (size) {
    0 -> return emptySet()
    1 -> return java.util.Set.of(fileToNodeSource(descriptors.get(0).file, relativizer))
    2 -> return java.util.Set.of(fileToNodeSource(descriptors.get(0).file, relativizer), fileToNodeSource(descriptors.get(1).file, relativizer))
    else -> {
      val result = ObjectLinkedOpenHashSet<PathSource>(size)
      for (descriptor in descriptors) {
        result.add(fileToNodeSource(descriptor.file, relativizer))
      }
      return result
    }
  }
}

private fun CoroutineScope.associate(
  changedOrAddedNodes: Set<NodeSource>,
  changedOrAdded: List<DependencyDescriptor>,
  delta: DeltaImpl,
  dependencyAnalyzer: DependencyAnalyzer,
) {
  val channel = Channel<Pair<NodeSource, List<Node<*, *>>>>(capacity = 4)

  launch {
    for ((source, nodes) in channel) {
      delta.associateNodes(source, nodes)
    }
  }

  launch {
    for ((index, libNode) in changedOrAddedNodes.withIndex()) {
      val dependency = changedOrAdded.get(index)
      launch {
        val nodes = dependencyAnalyzer.analyze(dependency)
        channel.send(libNode to nodes)
      }
    }
  }.invokeOnCompletion { channel.close(it) }
}

class DependencyAnalyzer(private val coroutineScope: CoroutineScope) {
  private val cache = Caffeine.newBuilder()
    .expireAfterAccess(2.minutes.toJavaDuration())
    .executor { coroutineScope.launch { it.run() } }
    .buildAsync(AsyncCacheLoader<DependencyDescriptor, List<Node<*, *>>> { key, executor ->
      coroutineScope.future {
        val result = ArrayList<Node<*, *>>()
        readZipFile(key.file) { name, dataProvider ->
          if (!name.endsWith(".class.n") || name.startsWith("META-INF/")) {
            return@readZipFile
          }

          val byteBuf = Unpooled.wrappedBuffer(dataProvider())
          val formatVersion = byteBuf.readIntLE()
          if (formatVersion != ABI_IC_NODE_FORMAT_VERSION) {
            throw RuntimeException("Unsupported ABI IC node format version: $formatVersion")
          }
          val input = NettyBufferGraphDataInput(byteBuf)
          val node = JvmClass(input)
          result.add(node)
        }
        result
      }
    })

  suspend fun analyze(descriptor: DependencyDescriptor): List<Node<*, *>> {
    return cache.get(descriptor).asDeferred().await()
  }
}