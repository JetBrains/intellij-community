// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleBased

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.product.serialization.RawProductModules
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.jetbrains.plugin.structure.base.utils.inputStream
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.findFileInModuleSources
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

internal class OriginalModuleRepositoryImpl(private val context: CompilationContext) : OriginalModuleRepository {
  override val repositoryPath: Path = context.classesOutputDirectory.resolve(JAR_REPOSITORY_FILE_NAME)

  override val rawRepositoryData: RawRuntimeModuleRepositoryData

  init {
    if (Files.notExists(repositoryPath)) {
      context.messages.error("Runtime module repository wasn't generated during compilation: $repositoryPath doesn't exist. If you run scripts from the IDE, please make sure that DevKit plugin is installed and enabled.")
    }
    rawRepositoryData = try {
      RuntimeModuleRepositorySerialization.loadFromJar(repositoryPath)
    }
    catch (e: MalformedRepositoryException) {
      context.messages.error("Failed to load runtime module repository: ${e.message}", e)
      throw e
    }
  }

  override fun loadRawProductModules(rootModuleName: String, productMode: ProductMode): RawProductModules {
    val productModulesFile = findProductModulesFile(context, rootModuleName)
                             ?: error("Cannot find product-modules.xml file in $rootModuleName")
    val resolver = object : ResourceFileResolver {
      override fun readResourceFile(moduleId: RuntimeModuleId, relativePath: String): InputStream? {
        return findFileInModuleSources(context.findRequiredModule(moduleId.stringId), relativePath)?.inputStream()
      }

      override fun toString(): String {
        return "source file based resolver for '${context.paths.projectHome}' project"
      }
    }
    return ProductModulesSerialization.readProductModulesAndMergeIncluded(productModulesFile.inputStream(), productModulesFile.pathString, resolver)
  }

  override suspend fun loadProductModules(rootModuleName: String, productMode: ProductMode): ProductModules {
    val repository = context.getOriginalModuleRepository().repository
    val productModulesFile = findProductModulesFile(context, rootModuleName)
                             ?: error("Cannot find product-modules.xml file in $rootModuleName")
    return ProductModulesSerialization.loadProductModules(productModulesFile, productMode, repository)
  }

  override val repository: RuntimeModuleRepository by lazy { 
    RuntimeModuleRepositorySerialization.loadFromRawData(repositoryPath, rawRepositoryData)
  }
}

internal fun findProductModulesFile(context: CompilationContext, clientMainModuleName: String): Path? {
  return findFileInModuleSources(context.findRequiredModule(clientMainModuleName), "META-INF/$clientMainModuleName/product-modules.xml")
}
