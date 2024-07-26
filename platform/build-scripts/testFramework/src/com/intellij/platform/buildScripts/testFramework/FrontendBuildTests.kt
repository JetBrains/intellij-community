// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.platform.runtime.product.ProductMode
import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.ProductInfoLayoutItemKind
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.readBuiltinModulesFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path

/**
 * Checks that frontend distribution (ex JetBrains Client) described by [frontendProperties] can be built successfully.
 */
fun runTestBuildForFrontend(
  homePath: Path, frontendProperties: ProductProperties, buildTools: ProprietaryBuildTools,
  testInfo: TestInfo, softly: SoftAssertions,
) {
  runTestBuild(
    homeDir = homePath,
    productProperties = frontendProperties,
    buildTools = buildTools,
    testInfo = testInfo,
    onSuccess = { context ->
      verifyBuiltInModules(context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-builtinModules.json"))
      assertTrue(context.useModularLoader) { "Frontend distribution must use the modular loader, but $frontendProperties doesn't use it" }
      val rootModule = frontendProperties.rootModuleForModularLoader
      assertNotNull(rootModule) { "Root module for the modular loader is not specified in $frontendProperties" }
      RuntimeModuleRepositoryChecker.checkProductModules(rootModule!!, ProductMode.FRONTEND, context, softly)
    }
  )
}

private fun verifyBuiltInModules(file: Path) {
  val data = readBuiltinModulesFile(file)
  assertTrue(data.fileExtensions.isEmpty())
  val modules = data.layout.asSequence().filter { it.kind == ProductInfoLayoutItemKind.pluginAlias }.map { it.name }.toHashSet()
  assertTrue(modules.contains("com.intellij.jetbrains.client"))
  assertTrue(modules.contains("com.intellij.modules.platform"))
  assertFalse(modules.contains("com.intellij.modules.externalSystem"))
}
