// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.buildScripts.testFramework

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.SoftAssertions
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.ModuleStructureValidator
import org.jetbrains.intellij.build.impl.createDistributionBuilderState
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

@ExtendWith(SoftAssertionsExtension::class)
abstract class IdeStructureTestBase {
  protected open val projectHome: Path
    get() = Path.of(PathManager.getHomePathFor(javaClass)!!)

  protected abstract fun createProductProperties(projectHome: Path): ProductProperties
  protected abstract fun createBuildTools(): ProprietaryBuildTools
  protected open val missingModulesException: Set<MissingModuleException>
    get() = emptySet()

  data class MissingModuleException(val fromModule: String, val toModule: String, val scope: JpsJavaDependencyScope)

  private fun createBuildContext(): BuildContext {
    val productProperties = createProductProperties(projectHome)
    return runBlocking(Dispatchers.Default) {
      createBuildContext(homeDir = projectHome, productProperties = productProperties, buildTools = createBuildTools())
    }
  }

  @Test
  fun moduleStructureValidation(softly: SoftAssertions) {
    val context = createBuildContext()
    val state = runBlocking {
      createDistributionBuilderState(context = context)
    }

    println("Packed modules:")
    for (item in state.platform.includedModules) {
      println("  ${item.moduleName} ${item.relativeOutputFile}")
    }

    val validator = ModuleStructureValidator(context, state.platform.includedModules)
    val errors = validator.validate()
    for (error in errors) {
      softly.collectAssertionError(error)
    }
  }
}