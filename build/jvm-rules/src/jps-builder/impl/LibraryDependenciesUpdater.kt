@file:Suppress("PackageDirectoryMismatch", "unused", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.jps.incremental.dependencies

import com.intellij.compiler.instrumentation.FailSafeClassReader
import com.intellij.openapi.util.io.FileUtil
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.bazel.jvm.jps.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildRootIndex
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.fileToNodeSource
import org.jetbrains.bazel.jvm.jps.state.DependencyStateStorage
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.dependency.GraphConfiguration
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Path


// all libs in bazel in the same lib
internal const val BAZEl_LIB_CONTAINER_NS = "ns"

@Suppress("InconsistentCommentForJavaParameter")
internal class LibraryDependenciesUpdater internal constructor(
  private val libState: DependencyStateStorage,
) {
  /**
   * @return true if you can continue incrementally, false if non-incremental
   */
  fun update(
    context: BazelCompileContext,
    chunk: ModuleChunk,
    target: BazelModuleBuildTarget,
    relativizer: PathTypeAwareRelativizer,
    span: Span,
  ): Boolean {
    val projectDescriptor = context.projectDescriptor
    val dataManager = projectDescriptor.dataManager
    val graphConfig = dataManager.getDependencyGraph()

    val graph = graphConfig.graph

    val changedOrAdded = libState.checkState(target.module.container.getChild(BazelConfigurationHolder.KIND).classPath)
    if (changedOrAdded.isEmpty()) {
      return true
    }

    val nodesToProcess = changedOrAdded.map { fileToNodeSource(it, relativizer) }
    val delta = graph.createDelta(
      /* sourcesToProcess = */ nodesToProcess,
      /* deletedSources = */ emptyList(),
      /* isSourceOnly = */ false,
    )
    for ((index, node) in nodesToProcess.withIndex()) {
      val sources = setOf(node)
      processLibraryRoot(jarFile = changedOrAdded.get(index), graphConfig = graphConfig) {
        delta.associate(it, sources)
      }
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
      span.addEvent("affected files by lib tracking", Attributes.of(AttributeKey.longKey("count"), affectedSources.count().toLong()))
      markAffectedFilesDirty(
        context = context,
        chunk = chunk,
        affectedFiles = affectedSources.asSequence().map { relativizer.toAbsoluteFile(it.toString(), RelativePathType.SOURCE) },
      )
    }

    graph.integrate(diffResult)
    return diffResult.isIncremental
  }
}

private const val MODULE_INFO_FILE = "module-info.java"

private fun markAffectedFilesDirty(context: CompileContext, chunk: ModuleChunk, affectedFiles: Sequence<Path>) {
  if (affectedFiles.none()) {
    return
  }

  val projectDescriptor = context.projectDescriptor
  val buildRootIndex = projectDescriptor.buildRootIndex as BazelBuildRootIndex
  val targetsToMark = hashSet<ModuleBuildTarget>()
  for (file in affectedFiles) {
    if (MODULE_INFO_FILE == file.fileName.toString()) {
      val asFile = file.toFile()
      val rootDescriptor = buildRootIndex.fileToDescriptors.get(file)
      if (rootDescriptor != null) {
        val moduleIndex = JpsJavaExtensionService.getInstance().getJavaModuleIndex(projectDescriptor.project)
        val target = rootDescriptor.getTarget()
        if (FileUtil.filesEqual(moduleIndex.getModuleInfoFile(target.module, target.isTests), asFile)) {
          targetsToMark.add(target)
        }
      }
    }
    else {
      FSOperations.markDirtyIfNotDeleted(context, CompilationRound.CURRENT, file)
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

private fun processLibraryRoot(
  jarFile: Path,
  graphConfig: GraphConfiguration,
  processor: (Node<*, *>) -> Unit = {},
) {
  readZipFile(jarFile) { name, dataProvider ->
    if (!LibraryDef.isClassFile(name)) {
      return@readZipFile
    }

    val buffer = dataProvider()
    val size = buffer.remaining()
    if (size == 0) {
      return@readZipFile
    }

    val classFileData = ByteArray(size)
    buffer.get(classFileData)
    val reader = FailSafeClassReader(classFileData)
    val node = JvmClassNodeBuilder.createForLibrary("$/$name", reader).result
    if (node.flags.isPublic) {
      // todo: maybe too restrictive
      processor(node)
    }
  }
}
