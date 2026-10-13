// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.buildScripts.concurrency.SharedTaskOwner
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.config.PlatformLibraryCopyException
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.dependency.JpsProjectBuilder
import org.jetbrains.intellij.build.productLayout.dependency.TestPluginGraphBuilder
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.PlatformLibraryCopyError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Tests for [PlatformLibraryCopyValidator].
 *
 * The cases model `intellij.libraries.google.auth` before and after IJAI-3760. The Air plugin bundles that module.
 * Before the fix, its module-level libraries packed Guava and Gson, and the core classloader holds both.
 */
@ExtendWith(TestFailureLogger::class)
class PlatformLibraryCopyValidatorTest {
  private val owner = SharedTaskOwner("PlatformLibraryCopyValidatorTest")

  @AfterEach
  fun closeSharedTasks() {
    owner.close()
  }

  @Test
  fun `reports a module library that packs an embedded platform library`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      platformWrappers()
      module("intellij.libraries.google.auth") {
        mavenModuleLibrary("google.auth.library.oauth2.http", listOf(
          "com.google.auth:google-auth-library-oauth2-http:1.40.0",
          "com.google.http-client:google-http-client:2.0.2",
          "com.google.guava:guava:33.5.0-android",
          "com.google.code.gson:gson:2.12.1",
        ))
      }
    }

    val errors = runRule(jps.outputProvider) { airWithGoogleAuth() }

    val violations = (errors.single() as PlatformLibraryCopyError).violations
    assertThat(violations.map { "${it.coordinate}:${it.version} ${it.platformModule}" }).containsExactlyInAnyOrder(
      "com.google.guava:guava:33.5.0-android intellij.libraries.guava",
      "com.google.code.gson:gson:2.12.1 intellij.libraries.gson",
    )
    assertThat(violations.map { it.contentModule }.distinct()).containsExactly("intellij.libraries.google.auth")
    assertThat(violations.map { it.plugin }.distinct()).containsExactly("intellij.air.plugin")
    assertThat(violations.map { it.library }.distinct()).containsExactly("google.auth.library.oauth2.http")
    assertThat(violations.flatMap { it.products }.distinct()).containsExactly("IDEA")
  }

  @Test
  fun `accepts an excluded copy with a module dependency on the wrapper`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      platformWrappers()
      module("intellij.libraries.google.auth") {
        mavenModuleLibrary("google.auth.library.oauth2.http", listOf(
          "com.google.auth:google-auth-library-oauth2-http:1.40.0",
          "com.google.http-client:google-http-client:2.0.2",
        ))
        moduleDep("intellij.libraries.guava")
        moduleDep("intellij.libraries.gson", scope = JpsJavaDependencyScope.PROVIDED)
      }
    }

    assertThat(runRule(jps.outputProvider) { airWithGoogleAuth() }).isEmpty()
  }

  @Test
  fun `ignores a platform library module that is not embedded`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      module("intellij.libraries.grpc") {
        mavenModuleLibrary("grpc-core", listOf("io.grpc:grpc-core:1.73.0", "io.grpc:grpc-api:1.73.0"))
      }
      module("intellij.libraries.google.auth") {
        mavenModuleLibrary("google.auth.library.oauth2.http", listOf(
          "com.google.auth:google-auth-library-oauth2-http:1.40.0",
          "io.grpc:grpc-api:1.70.0",
        ))
      }
    }

    val errors = runRule(jps.outputProvider) {
      product("IDEA") {
        includesModuleSet("rpc")
        bundlesPlugin("intellij.air.plugin")
      }
      moduleSet("rpc") {
        module("intellij.libraries.grpc")
      }
      plugin("intellij.air.plugin") {
        content("intellij.libraries.google.auth", namespace = null)
      }
    }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `ignores a module library in the test scope`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      platformWrappers()
      module("intellij.libraries.google.auth") {
        mavenModuleLibrary("guava-testlib", listOf("com.google.guava:guava-testlib:33.5.0-android", "com.google.guava:guava:33.5.0-android"),
                           scope = JpsJavaDependencyScope.TEST)
      }
    }

    assertThat(runRule(jps.outputProvider) { airWithGoogleAuth() }).isEmpty()
  }

  @Test
  fun `drops a listed copy and reports a copy that the entry does not list`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val jps = jpsProject(tempDir) {
      platformWrappers()
      module("intellij.libraries.google.auth") {
        mavenModuleLibrary("google.auth.library.oauth2.http", listOf(
          "com.google.auth:google-auth-library-oauth2-http:1.40.0",
          "com.google.guava:guava:33.5.0-android",
          "com.google.code.gson:gson:2.12.1",
        ))
      }
    }

    val suppressionConfig = SuppressionConfig(platformLibraryCopies = mapOf(
      ContentModuleName("intellij.libraries.google.auth") to PlatformLibraryCopyException(coordinates = setOf("com.google.guava:guava")),
    ))
    val errors = runRule(jps.outputProvider, suppressionConfig) { airWithGoogleAuth() }

    val violations = (errors.single() as PlatformLibraryCopyError).violations
    assertThat(violations.map { it.coordinate }).containsExactly("com.google.code.gson:gson")
  }

  private fun runRule(
    outputProvider: ModuleOutputProvider,
    suppressionConfig: SuppressionConfig = SuppressionConfig(),
    block: TestPluginGraphBuilder.() -> Unit,
  ): List<ValidationError> {
    val model = testGenerationModel(pluginGraph(block), outputProvider = outputProvider, suppressionConfig = suppressionConfig, owner = owner)
    return runValidationRule(PlatformLibraryCopyValidator, model)
  }
}

private fun JpsProjectBuilder.platformWrappers() {
  module("intellij.libraries.guava") {
    mavenModuleLibrary("guava", listOf("com.google.guava:guava:33.6.0-jre"))
  }
  module("intellij.libraries.gson") {
    mavenModuleLibrary("gson", listOf("com.google.code.gson:gson:2.14.0"))
  }
}

private fun TestPluginGraphBuilder.airWithGoogleAuth() {
  product("IDEA") {
    includesModuleSet("libraries")
    bundlesPlugin("intellij.air.plugin")
  }
  moduleSet("libraries") {
    module("intellij.libraries.guava", loading = ModuleLoadingRuleValue.EMBEDDED)
    module("intellij.libraries.gson", loading = ModuleLoadingRuleValue.EMBEDDED)
  }
  plugin("intellij.air.plugin") {
    content("intellij.libraries.google.auth", namespace = null)
  }
}
