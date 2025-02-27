@file:Suppress("PackageDirectoryMismatch", "unused", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.jps.incremental.dependencies

import com.intellij.compiler.instrumentation.FailSafeClassReader
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.fileToNodeSource
import org.jetbrains.bazel.jvm.jps.impl.markAffectedFilesDirty
import org.jetbrains.bazel.jvm.span
import org.jetbrains.intellij.build.io.suspendAwareReadZipFile
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.impl.DeltaImpl
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import java.nio.file.Path

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
): Boolean {
  val projectDescriptor = context.projectDescriptor
  val dataManager = projectDescriptor.dataManager
  val graphConfig = dataManager.getDependencyGraph()

  val graph = graphConfig.graph

  val changedOrAdded = dataProvider.libRootManager.checkState()
  if (changedOrAdded.isEmpty()) {
    return true
  }

  val nodesToProcess = changedOrAdded.map { fileToNodeSource(it, relativizer) }
  @Suppress("InconsistentCommentForJavaParameter", "RedundantSuppression")
  val delta = graph.createDelta(
    /* sourcesToProcess = */ nodesToProcess,
    /* deletedSources = */ emptyList(),
    /* isSourceOnly = */ false,
  ) as DeltaImpl
  tracer.span("associate dependencies") {
    associate(nodesToProcess = nodesToProcess, changedOrAdded = changedOrAdded, delta = delta)
  }

  val isFullRebuild = context.scope.isRebuild
  val diffResult = graph.differentiate(delta, DifferentiateParametersBuilder.create("deps").calculateAffected(!isFullRebuild).get())
  if (!diffResult.isIncremental) {
    if (!isFullRebuild) {
      throw RebuildRequestedException(RuntimeException("diffResult is non incremental: $diffResult"))
    }
  }
  else if (!isFullRebuild) {
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
  return diffResult.isIncremental
}


private fun CoroutineScope.associate(nodesToProcess: List<NodeSource>, changedOrAdded: List<Path>, delta: DeltaImpl) {
  val channel = Channel<Pair<Node<*, *>, NodeSource>>(capacity = Channel.BUFFERED)

  launch {
    for ((node, source) in channel) {
      delta.associateSource(node, source)
    }
  }

  launch {
    for ((index, libNode) in nodesToProcess.withIndex()) {
      val jarFile = changedOrAdded.get(index)
      launch {
        doAssociate(jarFile, libNode) { node, source ->
          channel.send(node to source)
        }
      }
    }
  }.invokeOnCompletion { channel.close(it) }
}

private suspend inline fun doAssociate(
  jarFile: Path,
  libNode: NodeSource,
  crossinline associator: suspend (Node<*, *>, NodeSource) -> Unit,
) {
  val path = jarFile.toString()

  @Suppress("SpellCheckingInspection")
  val isAbiJar = path.endsWith(".abi.jar") || path.endsWith("-ijar.jar")
  suspendAwareReadZipFile(jarFile) { name, dataProvider ->
    if (!name.endsWith(".class") || name.startsWith("META-INF/")) {
      return@suspendAwareReadZipFile
    }

    val buffer = dataProvider()
    val size = buffer.remaining()
    if (size == 0) {
      return@suspendAwareReadZipFile
    }

    val classFileData = ByteArray(size)
    buffer.get(classFileData)
    val reader = FailSafeClassReader(classFileData)
    val node = JvmClassNodeBuilder.createForLibrary("\$cp/$name", reader).result
    if (isAbiJar || node.flags.isPublic) {
      associator(node, libNode)
    }
  }
}