// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productRunner

import com.intellij.platform.runtime.repository.RuntimeModuleId
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.VmProperties
import org.jetbrains.intellij.build.impl.VmOptionsGenerator
import kotlin.io.path.pathString

/**
 * Runs the product using the module-based loader which will take class-files from module output directories.
 */
internal class ModuleBasedProductRunner(private val rootModuleForModularLoader: String, private val context: BuildContext) : IntellijProductRunner {
  override suspend fun runProduct(args: List<String>, additionalVmProperties: VmProperties, isLongRunning: Boolean) {
    val systemProperties = VmProperties(
      mapOf(
        "intellij.platform.runtime.repository.path" to context.originalModuleRepository.repositoryPath.pathString,
        "intellij.platform.root.module" to rootModuleForModularLoader,
        "intellij.platform.product.mode" to context.productProperties.productMode.id,
        "idea.vendor.name" to context.applicationInfo.shortCompanyName,
      )
    )

    //todo include jna.boot.library.path, pty4j.preferred.native.folder and related properties? 
    val loaderModule = context.originalModuleRepository.repository.getModule(RuntimeModuleId.module("intellij.platform.runtime.loader"))
    val ideClasspath = loaderModule.moduleClasspath.map { it.pathString }
    runApplicationStarter(
      context = context,
      classpath = ideClasspath,
      args = args,
      vmProperties = systemProperties + additionalVmProperties,
      vmOptions = VmOptionsGenerator.computeVmOptions(context) +
                  context.productProperties.additionalIdeJvmArguments +
                  context.productProperties.getAdditionalContextDependentIdeJvmArguments(context),
    )
  }
}