// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleBased

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.serialization.IncludedProductModulesResolver
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.product.serialization.RawProductModules
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.inputStream
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.pathString

class OriginalModuleRepositoryImpl(private val context: CompilationContext) : OriginalModuleRepository {
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

  override fun loadRawProductModules(rootModuleName: String, productMode: ProductMode): RawProductModules {
    val productModulesFile = findProductModulesFile(context, rootModuleName)
                             ?: error("Cannot find product-modules.xml file in $rootModuleName")
    val resolver = object : IncludedProductModulesResolver {
      override fun readProductModules(moduleId: RuntimeModuleId): InputStream? {
        return findProductModulesFile(context, moduleId.stringId)?.inputStream()
      }
    }
    return ProductModulesSerialization.readProductModulesAndMergeIncluded(productModulesFile.inputStream(), productModulesFile.pathString,
                                                                          resolver)
  }

  override fun loadProductModules(rootModuleName: String, productMode: ProductMode): ProductModules {
    val repository = context.originalModuleRepository.repository
    val productModulesFile = findProductModulesFile(context, rootModuleName)
                             ?: error("Cannot find product-modules.xml file in $rootModuleName")
    return ProductModulesSerialization.loadProductModules(productModulesFile, productMode, repository)
  }

  override val repository: RuntimeModuleRepository by lazy { 
    RuntimeModuleRepositorySerialization.loadFromRawData(repositoryForCompiledModulesPath, rawRepositoryData)
  }
}

internal fun findProductModulesFile(context: CompilationContext, clientMainModuleName: String): Path? =
  context.findFileInModuleSources(context.findRequiredModule(clientMainModuleName), "META-INF/$clientMainModuleName/product-modules.xml")
