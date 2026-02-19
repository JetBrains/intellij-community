// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleBased

import com.intellij.devkit.runtimeModuleRepository.generator.ResourcePathsSchema
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator.COMPACT_REPOSITORY_FILE_NAME
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal suspend fun buildOriginalModuleRepository(context: CompilationContext): OriginalModuleRepository {
  return spanBuilder("generate runtime module repository").use {
    val targetDirectory = context.paths.tempDir.resolve("module-descriptors")
    try {
      val outputProvider = context.outputProvider
      val moduleOutputs = context.project.modules.associateBy({ it.name }, { outputProvider.getModuleOutputRoots(it, forTests = false) })
      val testModuleOutputs = context.project.modules.associateBy({ it.name }, { outputProvider.getModuleOutputRoots(it, forTests = true) })
      val resourcePathsSchema = AbsolutePathsResourcePathsSchema(moduleOutputs, testModuleOutputs)
      val moduleDescriptors = RuntimeModuleRepositoryGenerator.generateRuntimeModuleDescriptorsForWholeProject(context.project, resourcePathsSchema)
      withContext(Dispatchers.IO) {
        RuntimeModuleRepositoryGenerator.saveModuleRepository(moduleDescriptors, targetDirectory)
      }
    }
    catch (e: Throwable) {
      context.messages.logErrorAndThrow("Failed to generate runtime module repository for compiled classes: ${e.message}", e)
    }
    OriginalModuleRepositoryImpl(targetDirectory.resolve(COMPACT_REPOSITORY_FILE_NAME), context)
  }
}

internal class OriginalModuleRepositoryImpl(override val repositoryPath: Path, context: CompilationContext) : OriginalModuleRepository {
  override val rawRepositoryData: RawRuntimeModuleRepositoryData

  init {
    if (Files.notExists(repositoryPath)) {
      context.messages.logErrorAndThrow("Runtime module repository wasn't generated: $repositoryPath doesn't exist.")
    }
    rawRepositoryData = try {
      RuntimeModuleRepositorySerialization.loadFromCompactFile(repositoryPath)
    }
    catch (e: MalformedRepositoryException) {
      context.messages.logErrorAndThrow("Failed to load runtime module repository: ${e.message}", e)
      throw e
    }
  }

  override val repository: RuntimeModuleRepository by lazy {
    RuntimeModuleRepositorySerialization.loadFromRawData(repositoryPath, rawRepositoryData)
  }
}

/**
 * The schema for a temporary repository which doesn't need to be portable, so it's not necessary to store relative paths in it.
 */
private class AbsolutePathsResourcePathsSchema(
  private val moduleOutputs: Map<String, List<Path>>,
  private val testModuleOutputs: Map<String, List<Path>>,
) : ResourcePathsSchema {
  override fun moduleOutputPaths(module: JpsModule): List<String> {
    return moduleOutputs[module.name]?.map { it.invariantSeparatorsPathString } ?: emptyList()
  }

  override fun moduleTestOutputPaths(module: JpsModule): List<String> {
    return testModuleOutputs[module.name]?.map { it.invariantSeparatorsPathString } ?: emptyList()
  }

  override fun libraryPaths(library: JpsLibrary): List<String> {
    return library.getPaths(JpsOrderRootType.COMPILED).map { it.invariantSeparatorsPathString }
  }
} 
