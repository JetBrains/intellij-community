// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.junit.jupiter.api.Test

/**
 * The labels of `build/dev_dist_reference_inputs.bzl`: the generator resolves the payload names through the targets
 * JSON, and the file states the labels that every product shares once.
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
      exports = emptyList(),
      moduleLibraries = libraries,
    )
  }

  private val targets = BazelTargetsInfo.TargetsFile(
    modules = mapOf(
      module("build"),
      module("core", "@lib//:core-lib.jar"),
      module("member"),
      module("nested"),
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
    moduleDependencies = { name -> if (name == "seed") listOf("dependency") else emptyList() },
    projectLibraryReferences = { name -> if (name == "core") listOf("guava") else emptyList() },
  )

  private val moduleSets = mapOf(
    "top" to ModuleSetData(name = "top", modules = listOf("member"), nested = listOf("inner")),
    "inner" to ModuleSetData(name = "inner", modules = listOf("nested"), nested = emptyList()),
  )

  private fun payload(modules: List<String> = emptyList(), moduleSets: List<String> = emptyList(), runtimeClasspathModules: List<String> = emptyList()) =
    DevDistReferencePayload(modules = modules, projectLibraries = emptyList(), moduleSets = moduleSets, runtimeClasspathModules = runtimeClasspathModules)

  @Test
  fun `a runtime module repository reference reads the platform of its product and of the embedded frontend`() {
    val inputs = resolveDevDistReferenceInputs(
      products = listOf(
        DevDistReferenceProduct(
          platformPrefix = "ide",
          buildModules = listOf("build", "core"),
          embeddedFrontend = "frontend",
          platformLib = payload(modules = listOf("core"), moduleSets = listOf("top"), runtimeClasspathModules = listOf("seed")),
          runtimeModuleRepository = payload(modules = listOf("frontend.root")),
        ),
        DevDistReferenceProduct(
          platformPrefix = "frontend",
          buildModules = emptyList(),
          embeddedFrontend = null,
          platformLib = payload(modules = listOf("frontend.core")),
          runtimeModuleRepository = null,
        ),
        DevDistReferenceProduct(
          platformPrefix = "server",
          buildModules = listOf("build"),
          embeddedFrontend = null,
          platformLib = payload(modules = listOf("core")),
          runtimeModuleRepository = null,
        ),
      ),
      moduleSets = moduleSets,
      model = model,
    )
    assertThat(inputs.getValue("ide")).isEqualTo(DevDistReferenceInputs(
      // A build module declares its output and no library.
      buildModules = listOf("//build:build.jar", "//core:core.jar"),
      runtimeClasspath = listOf("//dependency:dependency.jar", "//seed:seed.jar"),
      platformLib = listOf(
        "//core:core.jar",
        "//dependency:dependency.jar",
        "//member:member.jar",
        "//nested:nested.jar",
        "//seed:seed.jar",
        "@lib//:core-lib.jar",
        "@lib//:guava.jar",
      ),
      runtimeModuleRepository = listOf("//frontend.root:frontend.root.jar"),
    ))
    assertThat(inputs.getValue("frontend").platformLib).containsExactly("//frontend.core:frontend.core.jar")
    assertThat(inputs.getValue("server").platformLib).describedAs("no runtime module repository reference lays out this platform").isNull()
  }

  @Test
  fun `a name that the targets JSON does not have fails the run`() {
    assertThatThrownBy {
      resolveDevDistReferenceInputs(
        products = listOf(DevDistReferenceProduct(
          platformPrefix = "ide",
          buildModules = listOf("removed.build"),
          embeddedFrontend = null,
          platformLib = payload(moduleSets = listOf("removed.set")),
          runtimeModuleRepository = payload(),
        )),
        moduleSets = moduleSets,
        model = model,
      )
    }
      .hasMessageContaining("product 'ide', build modules: unknown module 'removed.build'")
      .hasMessageContaining("product 'ide', payload 'platform_lib': unknown module set 'removed.set'")
  }

  @Test
  fun `the labels that every product states are one constant`() {
    val text = renderDevDistReferenceInputs(
      inputs = linkedMapOf(
        "a" to DevDistReferenceInputs(buildModules = listOf("//x:x.jar", "//y:y.jar"), runtimeClasspath = emptyList(), platformLib = null, runtimeModuleRepository = null),
        "b" to DevDistReferenceInputs(buildModules = listOf("//x:x.jar"), runtimeClasspath = listOf("//r:r.jar"), platformLib = null, runtimeModuleRepository = null),
      ),
      generatedByHeader = "# header\n",
    )
    // The body after the comment block of the header.
    assertThat(text.substringAfter("has no meaning.\n")).isEqualTo("""
      |_BUILD_MODULES = [
      |    "//x:x.jar",
      |]
      |
      |DEV_DIST_REFERENCE_INPUTS = {
      |    "a": struct(
      |        build_modules = _BUILD_MODULES + [
      |            "//y:y.jar",
      |        ],
      |    ),
      |    "b": struct(
      |        build_modules = _BUILD_MODULES,
      |        runtime_classpath = [
      |            "//r:r.jar",
      |        ],
      |    ),
      |}
      |""".trimMargin())
  }
}
