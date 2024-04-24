// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productRunner

import com.intellij.platform.runtime.repository.RuntimeModuleId
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.VmOptionsGenerator
import kotlin.io.path.pathString

/**
 * Runs the product using the module-based loader which will take class-files from module output directories. 
 */
internal class ModuleBasedProductRunner(private val rootModuleForModularLoader: String, private val context: BuildContext) : IntellijProductRunner {
  override suspend fun runProduct(arguments: List<String>, additionalSystemProperties: Map<String, String>, isLongRunning: Boolean) {
    val systemProperties = mutableMapOf(
      "intellij.platform.runtime.repository.path" to context.originalModuleRepository.repositoryPath.pathString,
      "intellij.platform.root.module" to rootModuleForModularLoader,
      "intellij.platform.product.mode" to context.productProperties.productMode.id,
      "idea.vendor.name" to context.applicationInfo.shortCompanyName,
    )

    //todo include jna.boot.library.path, pty4j.preferred.native.folder and related properties? 
    val loaderModule = context.originalModuleRepository.repository.getModule(RuntimeModuleId.module("intellij.platform.runtime.loader"))
    val ideClasspath = loaderModule.moduleClasspath.map { it.pathString }
    systemProperties.putAll(additionalSystemProperties)
    runApplicationStarter(
      context, 
      ideClasspath = ideClasspath, 
      arguments = arguments,
      systemProperties = systemProperties,
      vmOptions =  VmOptionsGenerator.computeVmOptions(context) 
                   + context.productProperties.additionalIdeJvmArguments
                   + context.productProperties.getAdditionalContextDependentIdeJvmArguments(context),
    )
  }
}