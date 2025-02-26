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
import org.jetbrains.bazel.jvm.jps.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.fileToNodeSource
import org.jetbrains.bazel.jvm.jps.impl.markAffectedFilesDirty
import org.jetbrains.bazel.jvm.span
import org.jetbrains.intellij.build.io.suspendAwareReadZipFile
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
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

  val changedOrAdded = dataProvider.libRootManager.checkState(target.module.container.getChild(BazelConfigurationHolder.KIND).classPath)
  if (changedOrAdded.isEmpty()) {
    return true
  }

  val nodesToProcess = changedOrAdded.map { fileToNodeSource(it, relativizer) }
  @Suppress("InconsistentCommentForJavaParameter", "RedundantSuppression")
  val delta = graph.createDelta(
    /* sourcesToProcess = */ nodesToProcess,
    /* deletedSources = */ emptyList(),
    /* isSourceOnly = */ false,
  )
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


private fun CoroutineScope.associate(nodesToProcess: List<NodeSource>, changedOrAdded: List<Path>, delta: Delta) {
  val channel = Channel<Pair<Node<*, *>, List<NodeSource>>>(capacity = Channel.BUFFERED)

  launch {
    for ((node, sources) in channel) {
      delta.associate(node, sources)
    }
  }

  launch {
    for ((index, node) in nodesToProcess.withIndex()) {
      val jarFile = changedOrAdded.get(index)
      launch {
        val sources = listOf(node)
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
            channel.send(node to sources)
          }
        }
      }
    }
  }.invokeOnCompletion { channel.close(it) }
}