// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.junit.jupiter.api.Test

/**
 * The labels of `build/dev_dist_reference_inputs.bzl`: the generator resolves the runtime classpath seeds through the
 * targets JSON, and it refuses a name that the reference macro cannot resolve at load time.
 */
class DevDistReferenceInputsTest {
  private fun module(name: String, vararg moduleLibraryJars: String): Pair<String, BazelTargetsInfo.TargetsFileModuleDescription> {
    val libraries = if (moduleLibraryJars.isEmpty()) {
      emptyMap()
    }
    else {
      mapOf("#" to BazelTargetsInfo.LibraryDescription(target = "@lib//:$name-lib", jars = emptyList(), jarTargets = moduleLibraryJars.toList(), sourceJars = emptyList()))
    }
    return name to BazelTargetsInfo.TargetsFileModuleDescription(
      productionTargets = listOf("//$name:$name.jar"),
      productionJars = emptyList(),
      testTargets = emptyList(),
      testJars = emptyList(),
      moduleLibraries = libraries,
    )
  }

  private val targets = BazelTargetsInfo.TargetsFile(
    modules = mapOf(
      module("build"),
      module("core", "@lib//:core-lib.jar"),
      module("seed"),
      module("dependency"),
      module("frontend.root"),
      module("frontend.core"),
    ),
    projectLibraries = mapOf(
      "guava" to BazelTargetsInfo.LibraryDescription(target = "@lib//:guava", jars = emptyList(), jarTargets = listOf("@lib//:guava.jar"), sourceJars = emptyList()),
    ),
    pluginDistributionTargets = emptyMap(),
  )

  private val model = DevDistReferenceInputModel(
    targets = targets,
    moduleDependencies = { name -> if (name == "seed") listOf("dependency", "core") else emptyList() },
    projectLibraryReferences = { name -> if (name == "core" || name == "frontend.core") listOf("guava") else emptyList() },
  )

  private fun payload(modules: List<String> = emptyList(), projectLibraries: List<String> = emptyList(), moduleSets: List<String> = emptyList()) =
    DevDistReferencePayload(modules = modules, projectLibraries = projectLibraries, moduleSets = moduleSets)

  @Test
  fun `the runtime classpath is the closure of the seeds with the libraries of each module`() {
    val inputs = resolveDevDistReferenceInputs(
      products = listOf(
        DevDistReferenceProduct(
          platformPrefix = "ide",
          buildModules = listOf("build", "core"),
          runtimeClasspathModules = emptyList(),
          runtimeModuleRepository = payload(modules = listOf("frontend.root")),
        ),
        DevDistReferenceProduct(
          platformPrefix = "server",
          buildModules = listOf("build"),
          runtimeClasspathModules = listOf("seed"),
          runtimeModuleRepository = null,
        ),
      ),
      model = model,
    )
    // The reference macro resolves the build modules and the repository root, so the generator states no label of them.
    assertThat(inputs.getValue("ide")).isEqualTo(DevDistReferenceInputs(runtimeClasspath = emptyList()))
    assertThat(inputs.getValue("server").runtimeClasspath).containsExactly(
      "//core:core.jar",
      "//dependency:dependency.jar",
      "//seed:seed.jar",
      "@lib//:core-lib.jar",
      "@lib//:guava.jar",
    )
  }

  @Test
  fun `a name that the targets JSON does not have fails the run`() {
    assertThatThrownBy {
      resolveDevDistReferenceInputs(
        products = listOf(DevDistReferenceProduct(
          platformPrefix = "ide",
          buildModules = listOf("removed.build"),
          runtimeClasspathModules = listOf("removed.seed"),
          runtimeModuleRepository = payload(modules = listOf("removed.root")),
        )),
        model = model,
      )
    }
      .hasMessageContaining("product 'ide', build modules: unknown module 'removed.build'")
      .hasMessageContaining("product 'ide', payload 'platform_lib', runtime classpath: unknown module 'removed.seed'")
      .hasMessageContaining("product 'ide', payload 'platform_runtime_module_repository': unknown module 'removed.root'")
  }

  @Test
  fun `a repository payload that names a module set or a project library fails the run`() {
    assertThatThrownBy {
      resolveDevDistReferenceInputs(
        products = listOf(DevDistReferenceProduct(
          platformPrefix = "ide",
          buildModules = emptyList(),
          runtimeClasspathModules = emptyList(),
          runtimeModuleRepository = payload(modules = listOf("frontend.root"), projectLibraries = listOf("guava"), moduleSets = listOf("top")),
        )),
        model = model,
      )
    }
      .hasMessageContaining("The reference macro resolves a module name only")
      .hasMessageContaining("product 'ide', payload 'platform_runtime_module_repository': module set 'top'")
      .hasMessageContaining("product 'ide', payload 'platform_runtime_module_repository': project library 'guava'")
  }

  @Test
  fun `a repository payload module with libraries fails the run`() {
    assertThatThrownBy {
      resolveDevDistReferenceInputs(
        products = listOf(DevDistReferenceProduct(
          platformPrefix = "ide",
          buildModules = emptyList(),
          runtimeClasspathModules = emptyList(),
          runtimeModuleRepository = payload(modules = listOf("core", "frontend.core", "frontend.root")),
        )),
        model = model,
      )
    }
      .hasMessageContaining("The reference macro resolves a module name only")
      .hasMessageContaining("product 'ide', payload 'platform_runtime_module_repository': module 'core' has libraries")
      .hasMessageContaining("product 'ide', payload 'platform_runtime_module_repository': module 'frontend.core' has libraries")
      .hasMessageNotContaining("'frontend.root'")
  }

  @Test
  fun `the labels that every row states are one constant and a product without a runtime classpath has no row`() {
    val text = renderDevDistReferenceInputs(
      inputs = linkedMapOf(
        "a" to DevDistReferenceInputs(runtimeClasspath = listOf("//x:x.jar", "//y:y.jar")),
        "b" to DevDistReferenceInputs(runtimeClasspath = listOf("//x:x.jar")),
        "c" to DevDistReferenceInputs(runtimeClasspath = emptyList()),
      ),
      generatedByHeader = "# header\n",
    )
    // The body after the comment block of the header.
    assertThat(text.substringAfter("has no meaning.\n")).isEqualTo("""
      |_RUNTIME_CLASSPATH = [
      |    "//x:x.jar",
      |]
      |
      |DEV_DIST_REFERENCE_INPUTS = {
      |    "a": struct(
      |        runtime_classpath = _RUNTIME_CLASSPATH + [
      |            "//y:y.jar",
      |        ],
      |    ),
      |    "b": struct(
      |        runtime_classpath = _RUNTIME_CLASSPATH,
      |    ),
      |}
      |""".trimMargin())
  }
}
