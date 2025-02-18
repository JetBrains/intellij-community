// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.JpsBuildBundle
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.ProjectBuildException
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.dataTypes.LibraryRoots
import org.jetbrains.jps.incremental.storage.dataTypes.LibraryRootsImpl
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<BuildDataManager>()

internal class DefaultBuildDataProvider(
  private val dataPaths: BuildDataPaths,
  private val relativizer: PathRelativizerService,
  private val targetStateManager: BuildTargetsState,
) : BuildDataProvider {
  private val buildTargetToSourceToOutputMapping = ConcurrentHashMap<BuildTarget<*>, SourceToOutputMappingWrapper>()
  private val libraryRoots = LibraryRootsImpl(dataPaths, relativizer)
  private val outputToTargetMapping = OutputToTargetRegistry(getOutputToSourceRegistryRoot(dataPaths).resolve("data"), relativizer)
  private val sourceToFormMap = OneToManyPathsMapping(getSourceToFormsRoot(dataPaths).resolve("data"), relativizer)

  fun getChildStorages(): Iterable<StorageOwner> {
    return buildTargetToSourceToOutputMapping.values.asSequence().map { it.delegate }.asIterable()
  }

  override fun getLibraryRoots(): LibraryRoots = libraryRoots

  override fun flushStorage(memoryCachesOnly: Boolean) {
    libraryRoots.flush(memoryCachesOnly)
    sourceToFormMap.flush(memoryCachesOnly)
    outputToTargetMapping.flush(memoryCachesOnly)
  }

  override fun getSourceToOutputMapping(target: BuildTarget<*>): SourceToOutputMapping {
    try {
      return buildTargetToSourceToOutputMapping.computeIfAbsent(target, ::createSourceToOutputMap)
    }
    catch (e: BuildDataCorruptedException) {
      LOG.info(e)
      throw e.cause ?: e
    }
  }

  private fun createSourceToOutputMap(target: BuildTarget<*>): SourceToOutputMappingWrapper {
    val map = try {
      val file = dataPaths.getTargetDataRootDir(target)
        .resolve(BuildDataManager.SRC_TO_OUTPUT_STORAGE).resolve(BuildDataManager.SRC_TO_OUTPUT_FILE_NAME)
      SourceToOutputMappingImpl(file, relativizer)
    }
    catch (e: IOException) {
      LOG.info(e)
      throw BuildDataCorruptedException(e)
    }
    return SourceToOutputMappingWrapper(
      delegate = map,
      buildTargetId = targetStateManager.impl.getBuildTargetId(target),
      outputToTargetMapping = outputToTargetMapping,
    )
  }

  override fun closeTargetMaps(target: BuildTarget<*>) {
    buildTargetToSourceToOutputMapping.remove(target)?.delegate?.close()
  }

  override fun getSourceToForm(target: BuildTarget<*>) = sourceToFormMap

  override fun getFileStampStorage(target: BuildTarget<*>): StampsStorage<*> {
    TODO("Not yet implemented")
  }

  override fun getOutputToTargetMapping() = outputToTargetMapping

  override fun removeStaleTarget(targetId: String, targetType: BuildTargetType<*>) {
    // see note in BuildDataManager why impl is empty here
  }

  override fun clearCache() {
  }

  override fun wipeStorage() {
    runAllCatching(
      {
        try {
          libraryRoots.clean()
        }
        catch (e: Throwable) {
          LOG.error(ProjectBuildException(JpsBuildBundle.message("build.message.error.cleaning.library.roots.storage"), e))
        }
      },
      { buildTargetToSourceToOutputMapping.clear() },
      { BuildDataManager.wipeStorage(getSourceToFormsRoot(dataPaths), sourceToFormMap) },
      { BuildDataManager.wipeStorage(getOutputToSourceRegistryRoot(dataPaths), outputToTargetMapping) },
    )
  }

  override fun close() {
    runAllCatching(
      { libraryRoots.close() },
      { buildTargetToSourceToOutputMapping.clear() },
      { outputToTargetMapping.close() },
      { sourceToFormMap.close() },
    )
  }
}

@ApiStatus.Internal
fun runAllCatching(vararg tasks: () -> Unit) {
  var exception: Throwable? = null
  for (action in tasks) {
    try {
      action()
    }
    catch (e: Throwable) {
      if (exception == null) {
        exception = e
      }
      else {
        exception.addSuppressed(e)
      }
    }
  }
  exception?.let { throw it }
}

private class SourceToOutputMappingWrapper(
  @JvmField val delegate: SourceToOutputMappingImpl,
  private val buildTargetId: Int,
  private val outputToTargetMapping: OutputToTargetRegistry,
) : SourceToOutputMapping {
  override fun setOutputs(sourceFile: Path, outputs: List<Path>) {
    try {
      delegate.setOutputs(sourceFile, outputs)
    }
    finally {
      outputToTargetMapping.addMappings(buildTargetId, outputs)
    }
  }

  override fun appendOutput(sourcePath: String, outputPath: String) {
    try {
      delegate.appendOutput(sourcePath, outputPath)
    }
    finally {
      outputToTargetMapping.addMapping(outputPath, buildTargetId)
    }
  }

  override fun remove(sourceFile: Path) {
    delegate.remove(sourceFile)
  }

  override fun removeOutput(sourcePath: String, outputPath: String) {
    delegate.removeOutput(sourcePath, outputPath)
  }

  override fun getOutputs(sourcePath: String) = delegate.getOutputs(sourcePath)

  override fun getOutputs(sourceFile: Path) = delegate.getOutputs(sourceFile)

  override fun getSourceFileIterator() = delegate.sourceFileIterator

  override fun getSourcesIterator() = delegate.getSourcesIterator()

  override fun cursor() = delegate.cursor()
}

private fun getOutputToSourceRegistryRoot(dataPaths: BuildDataPaths): Path {
  return dataPaths.getDataStorageDir().resolve("out-target")
}

private fun getSourceToFormsRoot(dataPaths: BuildDataPaths): Path {
  return dataPaths.getDataStorageDir().resolve("src-form")
}