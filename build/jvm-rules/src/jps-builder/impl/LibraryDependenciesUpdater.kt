@file:Suppress("PackageDirectoryMismatch", "unused", "UnstableApiUsage")

package org.jetbrains.jps.incremental.dependencies

import com.intellij.openapi.util.io.FileUtil
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.bazel.jvm.jps.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelLibraryRoots
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.linkedSet
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Path

// all libs in bazel in the same lib
internal const val BAZEl_LIB_CONTAINER_NS = "ns"

internal class LibraryDependenciesUpdater internal constructor(
  private val libState: BazelLibraryRoots,
) {
  /**
   * @return true if you can continue incrementally, false if non-incremental
   */
  fun update(context: CompileContext, chunk: ModuleChunk, target: BazelModuleBuildTarget, span: Span): Boolean {
    val classpath = target.module.container.getChild(BazelConfigurationHolder.KIND).classPath
    val projectDescriptor = context.projectDescriptor
    val dataManager = projectDescriptor.dataManager
    val graphConfig = dataManager.dependencyGraph!!

    val graph = graphConfig.graph
    val pathMapper = graphConfig.pathMapper
    val isFullRebuild = (context as BazelCompileContext).scope.isRebuild

    val updated = linkedSet<Path>()
    val deleted = libState.checkState(updated, classpath)

    if (updated.isEmpty() && deleted.isEmpty()) {
      return true
    }

    val toUpdate = updated.map { it to pathMapper.toNodeSource(it) }
    val delta = graph.createDelta(
      /* sourcesToProcess = */ toUpdate.map { it.second },
      /* deletedSources = */ deleted.map { pathMapper.toNodeSource(it) },
      /* isSourceOnly = */ false,
    )
    val nodesBuilder = LibraryNodesBuilder(graphConfig)
    for ((_, src) in toUpdate) {
      var nodeCount = 0
      val sources = setOf(src)
      for (node in nodesBuilder.processLibraryRoot(BAZEl_LIB_CONTAINER_NS, src)) {
        nodeCount++
        delta.associate(node, sources)
      }
    }
    val diffParams = DifferentiateParametersBuilder.create("libraries of ${chunk.name}")
      .calculateAffected(!isFullRebuild)
      .get()
    val diffResult = graph.differentiate(delta, diffParams)

    if (!diffResult.isIncremental) {
      if (!isFullRebuild) {
        throw RebuildRequestedException(RuntimeException("diffResult is non incremental: $diffResult"))
      }
    }
    else if (diffParams.isCalculateAffected) {
      val affectedSources = diffResult.affectedSources
      span.addEvent("affected files by lib tracking", Attributes.of(AttributeKey.longKey("count"), affectedSources.count().toLong()))
      markAffectedFilesDirty(context, chunk, affectedSources.map { pathMapper.toPath(it) })
    }

    graph.integrate(diffResult)

    libState.removeRoots(deleted)
    return diffResult.isIncremental
  }
}

private const val MODULE_INFO_FILE = "module-info.java"

private fun markAffectedFilesDirty(context: CompileContext, chunk: ModuleChunk, affectedFiles: Iterable<Path>) {
  if (affectedFiles.none()) {
    return
  }

  val projectDescriptor = context.projectDescriptor
  val buildRootIndex = projectDescriptor.buildRootIndex
  val moduleIndex = JpsJavaExtensionService.getInstance().getJavaModuleIndex(projectDescriptor.project)
  val targetsToMark = HashSet<ModuleBuildTarget>()
  for (path in affectedFiles) {
    if (MODULE_INFO_FILE == path.fileName.toString()) {
      val asFile = path.toFile()
      val rootDescr = buildRootIndex.findJavaRootDescriptor(context, asFile)
      if (rootDescr != null) {
        val target = rootDescr.getTarget()
        if (FileUtil.filesEqual(moduleIndex.getModuleInfoFile(target.module, target.isTests), asFile)) {
          targetsToMark.add(target)
        }
      }
    }
    else {
      FSOperations.markDirtyIfNotDeleted(context, CompilationRound.CURRENT, path)
    }
  }
  if (chunk.targets.any { targetsToMark.contains(it) }) {
    // ensure all chunk's targets are compiled together
    targetsToMark.addAll(chunk.targets)
  }
  for (target in targetsToMark) {
    context.markNonIncremental(target)
    FSOperations.markDirty(context, CompilationRound.CURRENT, target, null)
  }
}