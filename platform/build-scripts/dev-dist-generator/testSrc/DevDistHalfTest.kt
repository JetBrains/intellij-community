// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.jps.model.JpsElementFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The facts of a repository half that are paths or spellings: the root of each half, how an index of the half spells a
 * label and resolves a path, and which output the community half refuses.
 */
class DevDistHalfTest {
  @TempDir
  lateinit var dir: Path

  @Test
  fun `the community half has the community checkout as its root and owns every package below it`() {
    val half = CommunityDevDistHalf
    val root = half.root(dir)

    assertThat(root).isEqualTo(dir.resolve("community"))
    assertThat(half.communityRoot(root)).isEqualTo(root)
    assertThat(root.resolve(RUN_CONFIGURATIONS_DIRECTORY)).isEqualTo(dir.resolve("community/.idea/runConfigurations"))
    assertThat(half.macrosBzl).isEqualTo("//build:intellij_dev_community.bzl")
    assertThat(half.writesCommunityPackages).isTrue()
    assertThat(half.ownsPackage("plugins/c")).isTrue()
    assertThat(half.ownsPackage("build/dev-dist-descriptors/intellij.c")).isTrue()
    half.requireWritable("plugins/c/BUILD.bazel")
    assertThat(half.generatedModuleSetDescriptors.keys).containsExactly("platform/platform-resources/generated/META-INF")
  }

  @Test
  fun `a directory below the community checkout of the monorepo is a community directory`() {
    assertThat(isCommunityDirectory("community")).isTrue()
    assertThat(isCommunityDirectory("community/plugins/c")).isTrue()
    assertThat(isCommunityDirectory("communityx/plugins/c")).isFalse()
    assertThat(isCommunityDirectory("build/dev-dist-descriptors/intellij.c")).isFalse()
    assertThat(isCommunityDirectory("")).isFalse()
  }

  @Test
  fun `the community half plans only the split products of the community registry`() {
    assertThat(CommunityDevDistHalf.registrySplitProducts(listOf("community", "Idea", "AndroidStudio"))).containsExactly("AndroidStudio", "Idea")
  }

  @Test
  fun `an index of the community half spells a plan label for a community package`() {
    val monorepoIndex = syntheticIndex(dir, "intellij.community" to "@community//plugins/community:community.jar")
    val communityRoot = CommunityDevDistHalf.root(dir)
    val communityIndex = DevDistBazelIndex(
      targets = monorepoIndex.targets,
      projectRoot = communityRoot,
      communityRoot = communityRoot,
      planPackageIsCommunity = true,
    )

    assertThat(monorepoIndex.contentModuleJarLabel("intellij.community", dependentIsCommunity = monorepoIndex.planPackageIsCommunity))
      .isEqualTo("@community//plugins/community:community_content_module_jar")
    assertThat(communityIndex.contentModuleJarLabel("intellij.community", dependentIsCommunity = communityIndex.planPackageIsCommunity))
      .isEqualTo("//plugins/community:community_content_module_jar")
    assertThat(communityIndex.packageDir("intellij.community")).isEqualTo(dir.resolve("community/plugins/community"))
    assertThat(snapshotDevDistBazelIndex(communityIndex).planPackageIsCommunity).isTrue()

    assertThat(communityIndex.planLabel("@community//platform/core:core")).isEqualTo("//platform/core:core")
    assertThat(communityIndex.planLabel("@lib//:kotlin-stdlib")).isEqualTo("@lib//:kotlin-stdlib")
    assertThat(communityIndex.planLabel("@dev_launch_restarter_extracted//:x")).isEqualTo("@dev_launch_restarter_extracted//:x")
    assertThat(communityIndex.planLabel("//build/dev-dist-descriptors/intellij.java.plugin:x"))
      .isEqualTo("//build/dev-dist-descriptors/intellij.java.plugin:x")
    assertThat(monorepoIndex.planLabel("@community//platform/core:core")).isEqualTo("@community//platform/core:core")
  }

  @Test
  fun `an index resolves a path of its half against the community checkout`() {
    val monorepoIndex = syntheticIndex(dir)
    val communityRoot = CommunityDevDistHalf.root(dir)
    val communityIndex = DevDistBazelIndex(targets = monorepoIndex.targets, projectRoot = communityRoot, communityRoot = communityRoot)

    assertThat(monorepoIndex.communityRelativePath("community/plugins/c/x.xml")).isEqualTo("plugins/c/x.xml")
    assertThat(monorepoIndex.communityRelativePath("community")).isEqualTo("")
    assertThat(monorepoIndex.communityRelativePath("plugins/c/x.xml")).isNull()
    assertThat(communityIndex.communityRelativePath("plugins/c/x.xml")).isEqualTo("plugins/c/x.xml")

    assertThat(monorepoIndex.packageDirectory("@community//plugins/xpath:xpath")).isEqualTo("community/plugins/xpath")
    assertThat(monorepoIndex.packageDirectory("@community//:root")).isEqualTo("community")
    assertThat(monorepoIndex.packageDirectory("//plugins/tailwindcss:tailwindcss")).isEqualTo("plugins/tailwindcss")
    assertThat(communityIndex.packageDirectory("@community//plugins/xpath:xpath")).isEqualTo("plugins/xpath")
    assertThat(communityIndex.packageDirectory("@community//:root")).isEqualTo("")
  }

  @Test
  fun `a containing package label keeps the recorded form in both halves`() {
    val communityRoot = CommunityDevDistHalf.root(dir)
    for (directory in listOf("", "community", "community/plugins/c", "plugins/u")) {
      Files.createDirectories(dir.resolve(directory))
      Files.writeString(dir.resolve(directory).resolve("BUILD.bazel"), "")
    }
    val monorepoIndex = syntheticIndex(dir)
    val communityIndex = DevDistBazelIndex(targets = monorepoIndex.targets, projectRoot = communityRoot, communityRoot = communityRoot)

    assertThat(monorepoIndex.containingPackageLabel("community/plugins/c/resources/x.xml")).isEqualTo("@community//plugins/c:resources/x.xml")
    assertThat(monorepoIndex.containingPackageLabel("community/other/x.xml")).isEqualTo("@community//:other/x.xml")
    assertThat(monorepoIndex.containingPackageLabel("plugins/u/resources/x.xml")).isEqualTo("//plugins/u:resources/x.xml")
    assertThat(monorepoIndex.containingPackageLabel("licenseCommon/x.xml")).isEqualTo("//:licenseCommon/x.xml")
    assertThat(communityIndex.containingPackageLabel("plugins/c/resources/x.xml")).isEqualTo("@community//plugins/c:resources/x.xml")
    assertThat(communityIndex.containingPackageLabel("other/x.xml")).isEqualTo("@community//:other/x.xml")
  }

  @Test
  fun `a community path that reaches the monorepo root package has no label`() {
    Files.writeString(dir.resolve("BUILD.bazel"), "")
    val monorepoIndex = syntheticIndex(dir)

    assertThat(monorepoIndex.containingPackageLabel("community/plugins/c/x.xml")).isNull()
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
      half = CommunityDevDistHalf,
      projectRoot = CommunityDevDistHalf.root(dir),
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
        half = CommunityDevDistHalf,
      projectRoot = CommunityDevDistHalf.root(dir),
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
        half = CommunityDevDistHalf,
      projectRoot = CommunityDevDistHalf.root(dir),
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
