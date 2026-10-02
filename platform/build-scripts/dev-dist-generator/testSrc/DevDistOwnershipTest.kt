// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The package ownership of the two halves: each half writes only its own packages, and the ultimate half reuses a
 * community target when its own text is equal.
 */
class DevDistOwnershipTest {
  @TempDir
  lateinit var dir: Path

  /** The community plugin of the synthetic index, in `@community//plugins/c`. */
  private val communityPlugin = "intellij.c"

  /** A half below the monorepo root that owns every package outside `community/`, as the ultimate half does. */
  private val monorepoHalf = object : DevDistHalf by CommunityDevDistHalf {
    override val name: String
      get() = "monorepo"

    override val rootDirectory: String
      get() = ""

    override fun ownsPackage(directory: String): Boolean = !isCommunityDirectory(directory)
  }

  @Test
  fun `each half writes only the packages of its own half`() {
    assertThat(monorepoHalf.ownsPackage("community/plugins/c")).isFalse()
    assertThat(monorepoHalf.ownsPackage("plugins/x")).isTrue()
    assertThat(monorepoHalf.ownsPackage(DEV_DIST_CONTENT_MODULE_JARS_PACKAGE)).isTrue()
    // The community half writes relative to its root, so every package it names is a community package.
    assertThat(CommunityDevDistHalf.ownsPackage("plugins/c")).isTrue()
    assertThat(CommunityDevDistHalf.ownsPackage("build/dev-dist-descriptors/intellij.c")).isTrue()
  }

  @Test
  fun `a write outside the packages of the half fails and names the path`() {
    monorepoHalf.requireWritable("build/dev-dist-descriptors/$communityPlugin/BUILD.bazel")
    assertThatThrownBy { monorepoHalf.requireWritable("community/plugins/c/intellij.c.dev-plan.json") }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("community/plugins/c/intellij.c.dev-plan.json")
      .hasMessageContaining("monorepo half")

    CommunityDevDistHalf.requireWritable("plugins/c/BUILD.bazel")
  }

  @Test
  fun `the ultimate half reuses an equal community section and moves a differing one into a product package`() {
    val own = mapOf("intellij.equal" to "dev_dist_plugin(a)", "intellij.other" to "dev_dist_plugin(b)", "intellij.unplanned" to "dev_dist_plugin(c)")
    val upstream = mapOf("intellij.equal" to "dev_dist_plugin(a)", "intellij.other" to "dev_dist_plugin(x)")

    assertThat(divergentDevSections(plugins = own.keys, sections = own, otherSections = upstream))
      .containsExactly("intellij.other", "intellij.unplanned")
  }

  @Test
  fun `the ultimate half reuses an equal content_module_jar call and relocates a differing one`() {
    val index = syntheticIndex(dir, communityPlugin to "@community//plugins/c:c.jar", "intellij.x" to "//plugins/x:x.jar")
    val call = "content_module_jar(module = \":c\")\n"

    assertThat(isRelocatedContentModuleJarCall(communityPlugin, call, upstreamCalls = mapOf(communityPlugin to call), index = index)).isFalse()
    assertThat(isRelocatedContentModuleJarCall(communityPlugin, call, upstreamCalls = mapOf(communityPlugin to "other\n"), index = index)).isTrue()
    assertThat(isRelocatedContentModuleJarCall(communityPlugin, call, upstreamCalls = emptyMap(), index = index)).isTrue()
    // An ultimate module and a run without a community result relocate nothing.
    assertThat(isRelocatedContentModuleJarCall("intellij.x", call, upstreamCalls = emptyMap(), index = index)).isFalse()
    assertThat(isRelocatedContentModuleJarCall(communityPlugin, call, upstreamCalls = null, index = index)).isFalse()
  }

  @Test
  fun `a relocated call names the community module and its libraries in the ultimate spelling`() {
    val index = syntheticIndex(dir, communityPlugin to "@community//plugins/c:c.jar")
    val jar = ContentModuleJarTarget(
      libraryTargetLabels = listOf("//libraries/x", "@lib//:y"),
      modulesBefore = listOf("//platform/core"),
      modulesAfter = emptyList(),
      sources = listOf(JarSourceRecipe(input = communityPlugin, kind = "module", filter = "module-v1")),
    )

    val call = relocatedContentModuleJarCall(communityPlugin, jar, index)

    assertThat(call).startsWith("content_module_jar(\n    name = \"intellij.c_content_module_jar\",\n")
    assertThat(call).contains("module = \"@community//plugins/c:c\"")
    assertThat(call).contains("\"@community//libraries/x\"").contains("\"@lib//:y\"")
    assertThat(call).contains("modules_before = [\"@community//platform/core\"]")
    assertThat(relocatedContentModuleJarLabel(communityPlugin)).isEqualTo("//build/dev-dist-content-module-jars:intellij.c_content_module_jar")
    assertThat(renderRelocatedContentModuleJarPackage(emptyMap(), monorepoHalf)).isNull()
    assertThat(renderRelocatedContentModuleJarPackage(mapOf(communityPlugin to call), monorepoHalf))
      .contains("load(\"@community//platform/build-scripts/bazel-rules:content_module_jar.bzl\", \"content_module_jar\")")
      .endsWith(call)
  }

  @Test
  fun `the ultimate half reuses the plan files and the calls of an equal complex community plugin`() {
    val plans = communityPlans(planText = "{\"library\": \"//libraries/x\"}\n", call = "dev_dist_complex_plugin(descriptor = \"//plugins/c:intellij.c_dev_descriptor\")\n")
    val ultimateText = mapOf("intellij.c.dev-plan.json" to "{\"library\": \"@community//libraries/x\"}\n")
    val ultimateCall = "dev_dist_complex_plugin(descriptor = \"@community//plugins/c:intellij.c_dev_descriptor\")\n"

    assertThat(plans.acceptsUpstreamPlans(communityPlugin, ultimateText, ultimateCall)).isTrue()
    val home = plans.upstreamHome(communityPlugin)
    assertThat(home.directory).isEqualTo("community/plugins/c")
    assertThat(home.packageLabel).isEqualTo("@community//plugins/c")
    assertThat(home.callIsCrossHalf).isFalse()

    assertThat(plans.acceptsUpstreamPlans(communityPlugin, mapOf("intellij.c.dev-plan.json" to "{}\n"), ultimateCall)).isFalse()
    assertThat(plans.acceptsUpstreamPlans(communityPlugin, ultimateText, ultimateCall.replace("_dev_descriptor", "_other"))).isFalse()
    assertThat(plans.acceptsUpstreamPlans(communityPlugin, ultimateText, crossHalfCalls = null)).isFalse()
    assertThat(plans.acceptsUpstreamPlans("intellij.unknown", ultimateText, ultimateCall)).isFalse()
  }

  @Test
  fun `the ultimate half keeps a complex plugin whose equal texts name a label outside the community call labels`() {
    val call = "dev_dist_complex_plugin(libraries = {\"@dev_launch_{platform}_jcef//:files\": \"x\"})\n"
    val plans = communityPlans(planText = "{}\n", call = call)

    assertThat(plans.acceptsUpstreamPlans(communityPlugin, mapOf("intellij.c.dev-plan.json" to "{}\n"), call)).isFalse()
  }

  @Test
  fun `a key of one class and one launch model is shared, and one class with two models fails`() {
    val half = monorepoHalf
    val ultimate = mapOf("Idea" to DevDistLaunchModel("IdeaCommunityProperties", "{\"a\": 1}\n"), "AndroidStudio" to DevDistLaunchModel("A", "{}\n"))

    assertThat(sharedLaunchModels(half, ultimate, mapOf("Idea" to DevDistLaunchModel("IdeaCommunityProperties", "{\"a\": 1}\n")))).containsExactly("Idea")
    // A key with two classes states two products, so neither half reuses.
    assertThat(sharedLaunchModels(half, ultimate, mapOf("AndroidStudio" to DevDistLaunchModel("B", "{\"b\": 2}\n")))).isEmpty()
    // A key the other half does not state is not shared.
    assertThat(sharedLaunchModels(half, ultimate, emptyMap())).isEmpty()
    assertThatThrownBy { sharedLaunchModels(half, ultimate, mapOf("Idea" to DevDistLaunchModel("IdeaCommunityProperties", "{\"a\": 2}\n"))) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'Idea'")
      .hasMessageContaining("IdeaCommunityProperties")
  }

  @Test
  fun `each half states the refusals of every stated mode`() {
    val community = listOf("Idea" to "monolith", "AndroidStudio" to "monolith")

    assertThat(devDistRefusingModeIds(community)).containsExactly("frontend")
    assertThat(devDistRefusingModeIds(community + ("Client" to "frontend"))).containsExactly("frontend")
    assertThatThrownBy { devDistRefusingModeIds(listOf("Server" to "backend")) }
      .hasMessageContaining("[Server]")
      .hasMessageContaining("backend")
  }

  @Test
  fun `a resource statement that the community half does not declare is reported with its layout`() {
    val ultimate = DevDistResourceStatements()
    ultimate.addDirectory("@community//python", "helpers/pydev", requester = "intellij.python")
    ultimate.addDirectory("@community//python", "helpers/common", requester = "intellij.python")
    val community = DevDistResourceStatements()
    community.addDirectory("//python", "helpers/common")

    assertThat(ultimate.missingIn(community, "@community//python")).containsExactly("directory helpers/pydev")
    assertThat(ultimate.requesters("@community//python")).containsExactly("intellij.python")
    community.addDirectory("//python", "helpers/pydev")
    assertThat(ultimate.missingIn(community, "@community//python")).isEmpty()
  }

  /** The plans of a community half whose own package of [communityPlugin] holds one plan file and one call. */
  private fun communityPlans(planText: String, call: String): DevDistOwnPackagePlans {
    return DevDistOwnPackagePlans(
      homes = mapOf(communityPlugin to DevDistPluginPlanHome("plugins/c", "@community//plugins/c", callIsCrossHalf = false, exportsPlanFiles = false)),
      planFiles = mapOf(communityPlugin to mapOf("intellij.c.dev-plan.json" to planText)),
      sectionCalls = mapOf(communityPlugin to call),
      rootDirectory = "community",
    )
  }
}
