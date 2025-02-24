@file:Suppress("PackageDirectoryMismatch", "unused", "UnstableApiUsage")

package org.jetbrains.jps.incremental.dependencies

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.bazel.jvm.jps.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelLibraryRoots
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.storage.dataTypes.LibRootUpdateResult
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Path

private val isLibTrackingEnabled = JavaBuilderUtil.isTrackLibraryDependenciesEnabled()

// all libs in bazel in the same lib
private const val BAZEl_LIB_CONTAINER_NS = "ns"

internal class LibraryDependenciesUpdater internal constructor(
  private val libState: BazelLibraryRoots,
) {
  private var isInitialized = false
  // namespace -> collection of roots, not associated with any module in the project
  private val nsToDeletedRoots = hashMap<String, MutableList<Path>>()
  // library root -> namespace
  private val libRootPathToNamespace = hashMap<Path, String>()
  private var totalTimeNano = 0L

  private val deletedRoots = hashSet<Path>()

  fun init(target: BazelModuleBuildTarget) {
    isInitialized = true

    // libraryRoot -> collection of library names, which include the root
    val classpath = target.module.container.getChild(BazelConfigurationHolder.KIND).classPath
    if (!classpath.isEmpty()) {
      for (k in classpath) {
        libRootPathToNamespace.put(k, BAZEl_LIB_CONTAINER_NS)
      }
    }

    for (deletedRoot in (libState.getFiles() - classpath)) {
      nsToDeletedRoots.computeIfAbsent(BAZEl_LIB_CONTAINER_NS) { ArrayList() }?.add(deletedRoot)
    }

    val deletedNamespaces = HashSet<String>(nsToDeletedRoots.keys)
    deletedNamespaces.removeAll(libRootPathToNamespace.values)
    // add all deleted roots that won't fit in any namespace
    for (ns in deletedNamespaces) {
      nsToDeletedRoots.remove(ns)?.let { deletedRoots.addAll(it) }
    }

    //LOG.info("LibraryDependencyUpdater initialized in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms")
  }

  /**
   * @return true if you can continue incrementally, false if non-incremental
   */
  @Synchronized
  fun update(context: CompileContext, chunk: ModuleChunk): Boolean {
    if (!isLibTrackingEnabled) {
      return true
    }

    val start = System.nanoTime()

    val target = chunk.targets.single() as BazelModuleBuildTarget

    val projectDescriptor = context.projectDescriptor
    val dataManager = projectDescriptor.dataManager
    val graphConfig = dataManager.dependencyGraph!!

    val graph = graphConfig.graph
    val pathMapper = graphConfig.pathMapper
    val isFullRebuild = (context as BazelCompileContext).scope.isRebuild

    val updatedRoots = hashSet<Path>()
    try {
      val classpath = target.module.container.getChild(BazelConfigurationHolder.KIND).classPath
      for (libRoot in classpath) {
        val namespace = libRootPathToNamespace.get(libRoot)
        // include in the delta those deleted roots that produced nodes for the same namespace,
        // where libsRootsToUpdate are going to contribute to
        nsToDeletedRoots.remove(namespace)?.let { deletedRoots.addAll(it) }

        val libRootUpdateResult = libState.updateIfExists(libRoot, namespace!!)
        if (libRootUpdateResult == LibRootUpdateResult.EXISTS_AND_MODIFIED) {
          updatedRoots.add(libRoot)
        }
        else if (libRootUpdateResult == LibRootUpdateResult.DOES_NOT_EXIST) {
          // the library is defined in the project, but does not exist on disk => is effectively deleted
          deletedRoots.add(libRoot)
        }
      }

      if (updatedRoots.isEmpty() && deletedRoots.isEmpty()) {
        return true
      }

      //sendProgress(
      //  context, JpsBuildBundle.message("progress.message.updating.library.state", updatedRoots.size, count(deletedRoots), chunk.presentableShortName)
      //)

      val toUpdate = updatedRoots.map { it to pathMapper.toNodeSource(it) }
      val delta = graph.createDelta(
        /* sourcesToProcess = */ toUpdate.map { it.second },
        /* deletedSources = */ deletedRoots.map { pathMapper.toNodeSource(it) },
        /* isSourceOnly = */ false,
      )
      val nodesBuilder = LibraryNodesBuilder(graphConfig)
      for ((libRoot, src) in toUpdate) {
        var nodeCount = 0
        val sources = setOf(src)
        for (node in nodesBuilder.processLibraryRoot(libRootPathToNamespace.get(libRoot)!!, src)) {
          nodeCount++
          delta.associate(node, sources)
        }
        //sendProgress(context, JpsBuildBundle.message("progress.message.processing.library", libRoot.fileName, nodeCount))
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
        //sendProgress(
        //  context, JpsBuildBundle.message("progress.message.dependency.analysis.found.0.affected.files", count(affectedSources))
        //)
        markAffectedFilesDirty(context, chunk, affectedSources.map { pathMapper.toPath(it) })
      }

      graph.integrate(diffResult)

      libState.removeRoots(deletedRoots)
      return diffResult.isIncremental
    }
    catch (e: Throwable) {
      for (path in updatedRoots) {
        // data from these libraries can be updated only partially
        // ensure they will be parsed next time
        libState.removeRoots(path)
      }
      throw e
    }
    finally {
      totalTimeNano += (System.nanoTime() - start)
    }
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