// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleBased

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.jetbrains.plugin.structure.base.utils.exists
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import java.nio.file.Path

class OriginalModuleRepositoryImpl(context: CompilationContext) : OriginalModuleRepository {
  private val repositoryForCompiledModulesPath: Path
  override val rawRepositoryData: RawRuntimeModuleRepositoryData

  init {
    CompilationTasks.create(context).generateRuntimeModuleRepository()

    repositoryForCompiledModulesPath = context.classesOutputDirectory.resolve(JAR_REPOSITORY_FILE_NAME)
    if (!repositoryForCompiledModulesPath.exists()) {
      context.messages.error("Runtime module repository wasn't generated during compilation: $repositoryForCompiledModulesPath doesn't exist")
    }
    rawRepositoryData = try {
      RuntimeModuleRepositorySerialization.loadFromJar(repositoryForCompiledModulesPath)
    }
    catch (e: MalformedRepositoryException) {
      context.messages.error("Failed to load runtime module repository: ${e.message}", e)
      throw e
    }
  }

  override val repository: RuntimeModuleRepository by lazy { 
    RuntimeModuleRepositorySerialization.loadFromRawData(repositoryForCompiledModulesPath, rawRepositoryData)
  }
}
