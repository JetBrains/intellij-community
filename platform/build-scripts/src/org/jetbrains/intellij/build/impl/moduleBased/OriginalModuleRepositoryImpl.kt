// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleBased

import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator.COMPACT_REPOSITORY_FILE_NAME
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import java.nio.file.Files
import java.nio.file.Path

internal class OriginalModuleRepositoryImpl(private val context: CompilationContext, mapping: Map<String, String>? = null) : OriginalModuleRepository {
  override val repositoryPath: Path = context.classesOutputDirectory.resolve(COMPACT_REPOSITORY_FILE_NAME)

  override val rawRepositoryData: RawRuntimeModuleRepositoryData

  init {
    if (Files.notExists(repositoryPath)) {
      context.messages.logErrorAndThrow("Runtime module repository wasn't generated during compilation: $repositoryPath doesn't exist. If you run scripts from the IDE, please make sure that DevKit plugin is installed and enabled.")
    }
    val rawData = try {
      RuntimeModuleRepositorySerialization.loadFromCompactFile(repositoryPath)
    }
    catch (e: MalformedRepositoryException) {
      context.messages.logErrorAndThrow("Failed to load runtime module repository: ${e.message}", e)
      throw e
    }
    rawRepositoryData = if (mapping != null) withRemappedPaths(rawData, mapping) else rawData
  }

  override val repository: RuntimeModuleRepository by lazy {
    RuntimeModuleRepositorySerialization.loadFromRawData(repositoryPath, rawRepositoryData)
  }
}

private fun withRemappedPaths(data: RawRuntimeModuleRepositoryData, mapping: Map<String, String>): RawRuntimeModuleRepositoryData {
  val updatedDescriptorsMap: MutableMap<String?, RawRuntimeModuleDescriptor?> = HashMap(data.allIds.size)
  for (id in data.allIds) {
    val descriptor: RawRuntimeModuleDescriptor = data.findDescriptor(id)!!
    val resourcePaths = descriptor.resourcePaths
    val updatedPaths: MutableList<String?> = ArrayList(resourcePaths.size)
    var modified = false
    for (path in resourcePaths) {
      if (path.startsWith("production/") || path.startsWith("test/")) {
        val origin = data.basePath.resolve(path).toString()
        val replaced = mapping[origin]
        if (replaced == null || replaced == origin) {
          updatedPaths.add(path)
        }
        else {
          modified = true
          updatedPaths.add(replaced)
        }
      }
      else {
        updatedPaths.add(path)
      }
    }

    val updatedDescriptor: RawRuntimeModuleDescriptor?
    if (modified) {
      updatedDescriptor = RawRuntimeModuleDescriptor.create(
        descriptor.id,
        updatedPaths,
        descriptor.dependencies
      )
    }
    else {
      updatedDescriptor = descriptor
    }
    updatedDescriptorsMap[id] = updatedDescriptor
  }

  return RawRuntimeModuleRepositoryData(
    updatedDescriptorsMap,
    data.basePath,
    data.mainPluginModuleId
  )
}
