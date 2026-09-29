// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.jps.model.JpsElementFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The two passes of the dev-distribution generator: where each pass reads and writes, how the community pass spells a
 * label, and which output the community pass refuses.
 */
class DevDistGenerationRootTest {
  @TempDir
  lateinit var dir: Path

  @Test
  fun `the community half reads and writes under community and writes only community packages`() {
    val root = DevDistGenerationRoot.community(dir)

    assertThat(root.projectRoot).isEqualTo(dir)
    assertThat(root.outputRoot).isEqualTo(dir.resolve("community"))
    assertThat(root.communityRoot).isEqualTo(dir.resolve("community"))
    assertThat(root.runConfigurationsDir).isEqualTo(dir.resolve("community/.idea/runConfigurations"))
    assertThat(root.macrosBzl).isEqualTo("//build:intellij_dev_community.bzl")
    assertThat(root.writesPackage("community/plugins/c")).isTrue()
    assertThat(root.writesPackage("build/dev-dist-descriptors/intellij.c")).isFalse()
    assertThat(root.dependentIsCommunity).isTrue()
  }

  @Test
  fun `the community pass drops the community repository of a label and keeps every other repository`() {
    val root = DevDistGenerationRoot.community(dir)

    assertThat(root.respellLabel("@community//platform/core:core")).isEqualTo("//platform/core:core")
    assertThat(root.respellLabel("@lib//:kotlin-stdlib")).isEqualTo("@lib//:kotlin-stdlib")
    assertThat(root.respellLabel("@dev_launch_restarter_extracted//:x")).isEqualTo("@dev_launch_restarter_extracted//:x")
    assertThat(root.respellLabel("//build/dev-dist-descriptors/intellij.java.plugin:x"))
      .isEqualTo("//build/dev-dist-descriptors/intellij.java.plugin:x")
    assertThat(root.respellQuotedLabels("""load("@community//platform/build-scripts/bazel-rules:dev_plugin.bzl", "dev_plugin")"""))
      .isEqualTo("""load("//platform/build-scripts/bazel-rules:dev_plugin.bzl", "dev_plugin")""")
    // A comment names a label in backquotes, and the respelling keeps it.
    assertThat(root.respellQuotedLabels("# `@community//build:dev_dist_product_info`"))
      .isEqualTo("# `@community//build:dev_dist_product_info`")
  }

  @Test
  fun `the community pass writes a project-relative path relative to community and refuses a path outside it`() {
    val root = DevDistGenerationRoot.community(dir)

    assertThat(root.outputRelativePath("community/android/adt-ui/resources/META-INF/adt-ui.xml"))
      .isEqualTo("android/adt-ui/resources/META-INF/adt-ui.xml")
    assertThatThrownBy { root.outputRelativePath("licenseCommon/generated/META-INF/x.xml") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("community pass")
      .hasMessageContaining("licenseCommon/generated/META-INF/x.xml")
  }

  @Test
  fun `the community pass plans only the split products of the community registry`() {
    val root = DevDistGenerationRoot.community(dir)

    assertThat(root.splitProducts(listOf("community", "Idea", "AndroidStudio"))).containsExactly("AndroidStudio", "Idea")
  }

  @Test
  fun `an index of the community pass spells a plan label for a community package`() {
    val ultimateIndex = syntheticIndex(dir, "intellij.community" to "@community//plugins/community:community.jar")
    val communityIndex = DevDistBazelIndex(
      targets = ultimateIndex.targets,
      projectRoot = dir,
      communityRoot = dir.resolve("community"),
      planPackageIsCommunity = true,
    )

    assertThat(ultimateIndex.contentModuleJarLabel("intellij.community", dependentIsCommunity = ultimateIndex.planPackageIsCommunity))
      .isEqualTo("@community//plugins/community:community_content_module_jar")
    assertThat(communityIndex.contentModuleJarLabel("intellij.community", dependentIsCommunity = communityIndex.planPackageIsCommunity))
      .isEqualTo("//plugins/community:community_content_module_jar")
    assertThat(communityIndex.packageDir("intellij.community")).isEqualTo(dir.resolve("community/plugins/community"))
    assertThat(snapshotDevDistBazelIndex(communityIndex).planPackageIsCommunity).isTrue()
  }

  @Test
  fun `a section states the refusals of every mode`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val context = DevSectionContext(
      mainModule = "intellij.c",
      isCommunity = true,
      index = syntheticIndex(dir, "intellij.c" to "@community//plugins/c:c.jar"),
      outputProvider = SourceRootModuleOutputProvider(project),
      warn = {},
    )
    val leaf = PluginDescriptorLeaf(
      variant = "",
      descriptor = "resources/META-INF/plugin.xml",
      descriptorModules = listOf("intellij.c.frontend"),
      descriptors = emptyMap(),
      libraryDescriptors = emptyMap(),
      refusedContentModules = emptyList(),
      modeRefusedContentModules = mapOf("frontend" to listOf("intellij.c.frontend")),
      separateJar = emptyList(),
      markers = emptyList(),
      versionSuffix = "",
      compatibleBuildRange = null,
      directoryName = "",
      embedContentModules = true,
      exactVersion = false,
      retainProductDescriptor = false,
      embeddedProductDescriptor = null,
    )
    val draft = PendingDevSection(
      context = context,
      content = PluginContentModules(listOf("intellij.c.frontend")),
      descriptors = listOf(leaf),
      record = DevSectionRecord(descriptorTargets = emptyMap()),
      loadStatements = emptyList(),
    )

    val body = draft.render(packaging = null).body
    assertThat(body).contains("mode_refused_content_modules")
    assertThat(body).contains("descriptor_modules = [\"intellij.c.frontend\"]")
  }

  @Test
  fun `a jar path of the community converter resolves below the community output of the monorepo`() {
    val module = BazelTargetsInfo.TargetsFileModuleDescription(
      productionTargets = listOf("@community//platform/core-api:core.jar"),
      productionJars = listOf("out/bazel-out/jvm-fastbuild/bin/platform/core-api/core.jar"),
      testTargets = emptyList(),
      testJars = listOf("out/bazel-out/jvm-fastbuild/bin/platform/core-api/core_test_lib.jar"),
      exports = emptyList(),
      moduleLibraries = emptyMap(),
    )
    val library = BazelTargetsInfo.LibraryDescription(
      target = "@lib//ant/lib:ant",
      jars = listOf("external/lib+/ant/lib/ant.jar"),
      jarTargets = listOf("@lib//ant/lib:ant.jar"),
      sourceJars = emptyList(),
    )
    val targets = BazelTargetsInfo.TargetsFile(
      modules = mapOf("intellij.platform.core" to module),
      projectLibraries = mapOf("Ant" to library),
      pluginDistributionTargets = mapOf(
        "intellij.x" to BazelTargetsInfo.PluginDistributionTargetDescription(
          target = "@community//plugins/x:x",
          distributionDirectory = "out/bazel-out/jvm-fastbuild/bin/plugins/x/x",
        ),
      ),
    )

    val remapped = communityTargetsSeenFromMonorepo(targets)

    val core = remapped.modules.getValue("intellij.platform.core")
    assertThat(core.productionTargets).isEqualTo(module.productionTargets)
    assertThat(core.productionJars).containsExactly("out/bazel-out/jvm-fastbuild/bin/external/community+/platform/core-api/core.jar")
    assertThat(core.testJars).containsExactly("out/bazel-out/jvm-fastbuild/bin/external/community+/platform/core-api/core_test_lib.jar")
    assertThat(remapped.projectLibraries.getValue("Ant")).isEqualTo(library)
    assertThat(remapped.pluginDistributionTargets.getValue("intellij.x").distributionDirectory)
      .isEqualTo("out/bazel-out/jvm-fastbuild/bin/external/community+/plugins/x/x")
  }

  @Test
  fun `the capability check accepts the products of the community registry`() {
    requireHalfCapabilities(
      root = DevDistGenerationRoot.community(dir),
      registryProducts = listOf("community", "Idea", "AndroidStudio"),
      plannedProducts = listOf("AndroidStudio", "Idea", "Idea"),
      generatedPluginFiles = emptyList(),
      hasPlatformPatches = false,
      runtimeModuleRepositoryProducts = emptyList(),
    )
  }

  @Test
  fun `the capability check fails for a product outside the community registry and names it`() {
    assertThatThrownBy {
      requireHalfCapabilities(
        root = DevDistGenerationRoot.community(dir),
        registryProducts = listOf("community", "Idea", "AndroidStudio"),
        plannedProducts = listOf("Idea", "Other"),
        generatedPluginFiles = emptyList(),
        hasPlatformPatches = false,
        runtimeModuleRepositoryProducts = emptyList(),
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("[Other]")
      .hasMessageContaining("dev-build.json")
  }

  @Test
  fun `the capability check fails for an output that the half has no capability for`() {
    fun check(
      generatedPluginFiles: List<String> = emptyList(),
      hasPlatformPatches: Boolean = false,
      runtimeModuleRepositoryProducts: List<String> = emptyList(),
    ) {
      requireHalfCapabilities(
        root = DevDistGenerationRoot.community(dir),
        registryProducts = listOf("Idea"),
        plannedProducts = listOf("Idea"),
        generatedPluginFiles = generatedPluginFiles,
        hasPlatformPatches = hasPlatformPatches,
        runtimeModuleRepositoryProducts = runtimeModuleRepositoryProducts,
      )
    }

    assertThatThrownBy { check(generatedPluginFiles = listOf("plugins/x/embedded.xml")) }.hasMessageContaining("embedded descriptor")
    assertThatThrownBy { check(hasPlatformPatches = true) }.hasMessageContaining("platform patch")
    assertThatThrownBy { check(runtimeModuleRepositoryProducts = listOf("Idea")) }.hasMessageContaining("runtime module repository")
  }
}
