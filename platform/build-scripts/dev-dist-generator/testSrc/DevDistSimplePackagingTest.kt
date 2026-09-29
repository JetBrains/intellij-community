// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicArtifact
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicArtifactCatalogue
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.PluginPackingProjection
import org.jetbrains.intellij.build.devDist.ReusableJarArtifact
import org.jetbrains.intellij.build.devDist.pluginPackingExecutionVersion
import org.jetbrains.intellij.build.devDist.pluginPackingLayoutSignature
import org.jetbrains.intellij.build.impl.PluginLayout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The simple-tier classification of a neutral plan whose non-jar assets are verbatim copies: a `withResource*` file or
 * directory, or the one file of a layout callback. Every other non-jar asset keeps the plan tier.
 */
class DevDistSimplePackagingTest {
  @TempDir
  lateinit var dir: Path

  /** The ultimate plugin of the synthetic index, in `//plugins/x`. */
  private val plugin = "intellij.x"

  /** The community plugin of the synthetic index, in `@community//plugins/c`. */
  private val communityPlugin = "intellij.c"

  private lateinit var index: DevDistBazelIndex

  @BeforeEach
  fun createIndex() {
    index = syntheticIndex(dir, plugin to "//plugins/x:x.jar", communityPlugin to "@community//plugins/c:c.jar")
  }

  /** The main jar of [plugin] with the descriptor patch, the one jar every simple plugin has. */
  private fun mainJar(plugin: String = this.plugin): PluginPackingAsset {
    val descriptor = devDistDescriptorInputId(plugin)
    val recipe = CanonicalJarRecipe(
      sources = listOf(
        JarSourceRecipe(descriptor, "file", "none", PLUGIN_XML_RELATIVE_PATH, options = listOf("patch")),
        JarSourceRecipe(plugin, "module", "module-v1"),
      ),
      writer = JarWriterRecipe(mergeEntities = true),
    )
    return PluginPackingAsset(destination = "lib/${plugin.removePrefix("intellij.")}.jar", inputs = listOf(descriptor, plugin), recipe = recipe)
  }

  /** A `withResource*` file at [destination], the mode the layout gives a copied file. */
  private fun fileCopy(destination: String, input: String, mode: Int = 493): PluginPackingAsset {
    return PluginPackingAsset(destination = destination, inputs = listOf(input), mode = mode, kind = "file", classPath = false)
  }

  /** A `withResource*` directory at [destination]. */
  private fun treeCopy(destination: String, input: String, normalizeTreeModes: Boolean = false): PluginPackingAsset {
    return PluginPackingAsset(destination = destination, inputs = listOf(input), kind = "tree", classPath = false, normalizeTreeModes = normalizeTreeModes)
  }

  private fun fileInput(id: String, label: String, kind: String = "file"): DevDistPluginRawInput {
    return DevDistPluginRawInput(id = id, label = label, kind = kind, fileName = label.substringAfterLast(':').substringAfterLast('/'))
  }

  /** The `withResource*` directory below [prefix] of the ultimate plugin, read through the package filegroup. */
  private fun treeInput(id: String, prefix: String): DevDistPluginRawInput {
    return DevDistPluginRawInput(
      id = id,
      label = "//plugins/x:dev_dist_resources",
      kind = "directory",
      fileName = prefix.substringAfterLast('/'),
      sourceTreePrefix = prefix,
    )
  }

  /** One neutral record of [plugin] over [assets], whose raw inputs are the module output and [inputs]. */
  private fun planEntry(
    assets: List<PluginPackingAsset>,
    inputs: List<DevDistPluginRawInput>,
    plugin: String = this.plugin,
    preparations: List<PluginPackingPreparation> = emptyList(),
  ): DevDistPluginPlanEntry {
    val signature = pluginPackingLayoutSignature(plugin, "", assets, preparations, emptyList())
    val moduleLabel = if (plugin == communityPlugin) "@community//plugins/c:c" else "//plugins/x:x"
    val plan = object : DevDistPluginBuildPlan {
      override val projection = PluginPackingProjection(
        version = pluginPackingExecutionVersion(assets),
        plugin = plugin,
        variant = "",
        layoutSignature = signature,
        assets = assets,
        preparations = preparations,
      )
      override val catalogue = PluginSymbolicArtifactCatalogue(
        artifacts = listOf(PluginSymbolicArtifact(id = plugin, kind = "directory", fileName = plugin)),
        moduleRoots = mapOf(plugin to listOf(plugin)),
        libraries = emptyList(),
      )
      override val requiredRawInputs = listOf(DevDistPluginRawInput(id = plugin, label = moduleLabel, kind = "directory", fileName = plugin)) + inputs
      override val requiredLibraries = emptyList<String>()
      override val reusableArtifacts = emptyList<ReusableJarArtifact>()
      override val layoutSignature = signature
    }
    val record = DevDistPluginPlanRecord(variant = PluginSymbolicVariant(id = ""), plan = plan)
    return DevDistPluginPlanEntry(
      product = "idea",
      tier = DevDistPluginTier.BUNDLED,
      mainModule = plugin,
      layouts = mapOf("" to PluginLayout.plugin(plugin)),
      records = mapOf("" to record),
    )
  }

  private fun classify(
    entry: DevDistPluginPlanEntry,
    contentModuleNames: List<String> = emptyList(),
    baseline: Boolean = true,
    refusedContentModules: Set<String> = emptySet(),
  ): DevDistSimplePackaging? {
    return classifySimplePluginPackaging(
      entry = entry,
      descriptorInput = devDistDescriptorInputId(entry.mainModule),
      contentModuleNames = contentModuleNames,
      ownDescriptorDeclared = true,
      contentModuleJarModules = emptySet(),
      index = index,
      baseline = baseline,
      refusedContentModules = refusedContentModules,
    )
  }

  private fun requireSimple(entry: DevDistPluginPlanEntry): DevDistSimplePackaging {
    return requireNotNull(classify(entry)) { "Plugin '${entry.mainModule}' keeps the plan tier" }
  }

  private fun assertKeepsPlanTier(entry: DevDistPluginPlanEntry) {
    val packaging: DevDistSimplePackaging? = classify(entry)
    assertThat(packaging).isNull()
  }

  @Test
  fun `a jar-only plugin states no copy`() {
    val packaging: DevDistSimplePackaging = requireSimple(planEntry(assets = listOf(mainJar()), inputs = emptyList()))

    assertThat(packaging.jars.keys).containsExactly("lib/x.jar")
    assertThat(packaging.files).isEmpty()
    assertThat(packaging.filePrefixes).isEmpty()
    assertThat(packaging.executableFiles).isEmpty()
    assertThat(packaging.fileLabels).isEmpty()
    assertThat(packaging.crossHalf).isFalse()
  }

  @Test
  fun `a withResource file gives files and executable_files`() {
    val input: DevDistPluginRawInput = fileInput("module-resource:0:source", "//plugins/x:helper.sh")
    val packaging: DevDistSimplePackaging = requireSimple(planEntry(assets = listOf(mainJar(), fileCopy("bin/helper.sh", input.id)), inputs = listOf(input)))

    assertThat(packaging.jars.keys).containsExactly("lib/x.jar")
    assertThat(packaging.files).containsExactly(entry("bin/helper.sh", "//plugins/x:helper.sh"))
    assertThat(packaging.filePrefixes).isEmpty()
    assertThat(packaging.executableFiles).containsExactly("bin/helper.sh")
    assertThat(packaging.fileLabels).containsExactly("//plugins/x:helper.sh")
    assertThat(packaging.classpathJars).isEmpty()
    assertThat(packaging.crossHalf).isFalse()
  }

  @Test
  fun `a withResource directory gives file_prefixes`() {
    val input: DevDistPluginRawInput = treeInput("module-resource:0:source", "plugins/x/helpers")
    val packaging: DevDistSimplePackaging = requireSimple(planEntry(assets = listOf(mainJar(), treeCopy("helpers", input.id)), inputs = listOf(input)))

    assertThat(packaging.files).containsExactly(entry("helpers", "//plugins/x:dev_dist_resources"))
    assertThat(packaging.filePrefixes).containsExactly(entry("helpers", "plugins/x/helpers"))
    assertThat(packaging.executableFiles).isEmpty()
    assertThat(packaging.crossHalf).isFalse()
  }

  @Test
  fun `the one archive of a layout callback is a file copy of mode 420`() {
    val input: DevDistPluginRawInput = fileInput("layout-source:custom-asset:0:0", "@dev_launch_air_openui_renderer//:files", kind = "archive")
    val copy = fileCopy("openui/air-openui-renderer.zip", input.id, mode = 420)
    val packaging: DevDistSimplePackaging = requireSimple(planEntry(assets = listOf(mainJar(), copy), inputs = listOf(input)))

    assertThat(packaging.files).containsExactly(entry("openui/air-openui-renderer.zip", "@dev_launch_air_openui_renderer//:files"))
    assertThat(packaging.filePrefixes).isEmpty()
    assertThat(packaging.executableFiles).isEmpty()
    assertThat(packaging.crossHalf).isFalse()
  }

  @Test
  fun `two copies of one label share the label`() {
    val script: DevDistPluginRawInput = fileInput("module-resource:0:source", "//plugins/x:helper.sh")
    val tree: DevDistPluginRawInput = treeInput("module-resource:1:source", "plugins/x/helpers")
    val assets = listOf(mainJar(), fileCopy("bin/helper.sh", script.id), treeCopy("helpers", tree.id), treeCopy("data", tree.id))
    val packaging: DevDistSimplePackaging = requireSimple(planEntry(assets = assets, inputs = listOf(script, tree)))

    assertThat(packaging.files.keys).containsExactly("bin/helper.sh", "helpers", "data")
    assertThat(packaging.filePrefixes.keys).containsExactly("helpers", "data")
    assertThat(packaging.fileLabels).containsExactly("//plugins/x:helper.sh", "//plugins/x:dev_dist_resources")
  }

  @Test
  fun `a baseline product that reuses fewer jars than the section names keeps the plan tier`() {
    val packaging: DevDistSimplePackaging? = classify(
      entry = planEntry(assets = listOf(mainJar()), inputs = emptyList()),
      contentModuleNames = listOf("intellij.x.backend"),
      baseline = true,
    )

    assertThat(packaging).isNull()
  }

  @Test
  fun `a divergent product that reuses fewer jars than the section names is simple and cross-half`() {
    val packaging: DevDistSimplePackaging? = classify(
      entry = planEntry(assets = listOf(mainJar()), inputs = emptyList()),
      contentModuleNames = listOf("intellij.x.backend"),
      baseline = false,
    )

    assertThat(packaging).isNotNull
    assertThat(requireNotNull(packaging).reusedModules).isEmpty()
    assertThat(packaging.crossHalf).isTrue()
  }

  @Test
  fun `a baseline product that refuses a content module stays simple`() {
    val packaging: DevDistSimplePackaging? = classify(
      entry = planEntry(assets = listOf(mainJar()), inputs = emptyList()),
      contentModuleNames = listOf("intellij.x.trial"),
      baseline = true,
      refusedContentModules = setOf("intellij.x.trial"),
    )

    assertThat(packaging).isNotNull
    assertThat(requireNotNull(packaging).reusedModules).isEmpty()
    assertThat(packaging.crossHalf).isFalse()
  }

  @Test
  fun `a symlink keeps the plan tier`() {
    val link = PluginPackingAsset(destination = "bin/link", inputs = emptyList(), symlinkTarget = "helper.sh", classPath = false)

    assertKeepsPlanTier(planEntry(assets = listOf(mainJar(), link), inputs = emptyList()))
  }

  @Test
  fun `a tree with normalized modes keeps the plan tier`() {
    val input: DevDistPluginRawInput = treeInput("module-resource:0:source", "plugins/x/helpers")
    val assets = listOf(mainJar(), treeCopy("helpers", input.id, normalizeTreeModes = true))

    assertKeepsPlanTier(planEntry(assets = assets, inputs = listOf(input)))
  }

  @Test
  fun `a transform output keeps the plan tier`() {
    val output = "layout-assets:custom-asset:0:output"
    val preparation = PluginPackingPreparation(id = "layout-assets:custom-asset:0", inputs = listOf(plugin), outputs = listOf(output), modelSignature = "sig")
    val assets = listOf(mainJar(), PluginPackingAsset(destination = "jcef", inputs = listOf(output), kind = "tree", classPath = false))

    assertKeepsPlanTier(planEntry(assets = assets, inputs = emptyList(), preparations = listOf(preparation)))
  }

  @Test
  fun `a copy of a mode the layout does not give keeps the plan tier`() {
    val input: DevDistPluginRawInput = fileInput("layout-source:custom-asset:0:0", "//plugins/x:helper.sh")

    assertKeepsPlanTier(planEntry(assets = listOf(mainJar(), fileCopy("bin/helper.sh", input.id, mode = 384)), inputs = listOf(input)))
  }

  @Test
  fun `a file copy over a directory input keeps the plan tier`() {
    val input: DevDistPluginRawInput = treeInput("module-resource:0:source", "plugins/x/helpers")

    assertKeepsPlanTier(planEntry(assets = listOf(mainJar(), fileCopy("helpers", input.id)), inputs = listOf(input)))
  }

  @Test
  fun `a tree over an input without a prefix keeps the plan tier`() {
    val input = DevDistPluginRawInput(id = "jupyter-frontend:x", label = "//plugins/x:frontend", kind = "directory", fileName = "frontend")

    assertKeepsPlanTier(planEntry(assets = listOf(mainJar(), treeCopy("frontend", input.id)), inputs = listOf(input)))
  }

  @Test
  fun `a copy whose input the plan does not name keeps the plan tier`() {
    assertKeepsPlanTier(planEntry(assets = listOf(mainJar(), fileCopy("bin/helper.sh", "module-resource:0:source")), inputs = emptyList()))
  }

  @Test
  fun `a copy below a copied directory keeps the plan tier`() {
    val script: DevDistPluginRawInput = fileInput("module-resource:0:source", "//plugins/x:helper.sh")
    val tree: DevDistPluginRawInput = treeInput("module-resource:1:source", "plugins/x/helpers")
    val assets = listOf(mainJar(), treeCopy("helpers", tree.id), fileCopy("helpers/bin/helper.sh", script.id))

    assertKeepsPlanTier(planEntry(assets = assets, inputs = listOf(script, tree)))
  }

  @Test
  fun `a copied directory over a jar keeps the plan tier`() {
    val tree: DevDistPluginRawInput = treeInput("module-resource:0:source", "plugins/x/lib")

    assertKeepsPlanTier(planEntry(assets = listOf(mainJar(), treeCopy("lib", tree.id)), inputs = listOf(tree)))
  }

  @Test
  fun `a community plugin that copies a community file is not cross-half`() {
    val input: DevDistPluginRawInput = fileInput("module-resource:0:source", "@community//plugins/c:helper.sh")
    val packaging: DevDistSimplePackaging = requireSimple(planEntry(assets = listOf(mainJar(communityPlugin), fileCopy("bin/helper.sh", input.id)), inputs = listOf(input), plugin = communityPlugin))

    assertThat(packaging.files).containsExactly(entry("bin/helper.sh", "@community//plugins/c:helper.sh"))
    assertThat(packaging.crossHalf).isFalse()
  }

  @Test
  fun `a community plugin that copies from a repository its package cannot name is cross-half`() {
    val input: DevDistPluginRawInput = fileInput("layout-source:custom-asset:0:0", "@dev_launch_air_openui_renderer//:files", kind = "archive")
    val copy = fileCopy("openui/air-openui-renderer.zip", input.id, mode = 420)
    val packaging: DevDistSimplePackaging = requireSimple(planEntry(assets = listOf(mainJar(communityPlugin), copy), inputs = listOf(input), plugin = communityPlugin))

    assertThat(packaging.files).containsExactly(entry("openui/air-openui-renderer.zip", "@dev_launch_air_openui_renderer//:files"))
    assertThat(packaging.crossHalf).isTrue()
  }

  @Test
  fun `the cross-half dev_plugin target states the copies in alphabetical position, sorted`() {
    val script: DevDistPluginRawInput = fileInput("module-resource:0:source", "@community//plugins/c:helper.sh")
    val archive: DevDistPluginRawInput = fileInput("layout-source:custom-asset:0:0", "@dev_launch_air_openui_renderer//:files", kind = "archive")
    val tree = DevDistPluginRawInput(
      id = "module-resource:1:source",
      label = "@community//plugins/c:dev_dist_resources",
      kind = "directory",
      fileName = "helpers",
      sourceTreePrefix = "community/plugins/c/helpers",
    )
    val assets = listOf(
      mainJar(communityPlugin),
      treeCopy("helpers", tree.id),
      fileCopy("openui/air-openui-renderer.zip", archive.id, mode = 420),
      fileCopy("bin/helper.sh", script.id),
    )
    val packaging: DevDistSimplePackaging = requireSimple(planEntry(assets = assets, inputs = listOf(script, archive, tree), plugin = communityPlugin))
    assertThat(packaging.crossHalf).isTrue()

    val target = renderCrossHalfDevPluginTarget(packaging, descriptorLabel = "//build/dev-dist-descriptors/intellij.c:intellij.c_dev_descriptor", index = index)

    assertThat(target).isEqualTo(
      """
      dev_plugin(
          name = "intellij.c_dev_plugin",
          descriptor = "//build/dev-dist-descriptors/intellij.c:intellij.c_dev_descriptor",
          executable_files = [
              "bin/helper.sh",
          ],
          file_prefixes = {
              "helpers": "community/plugins/c/helpers",
          },
          files = {
              "bin/helper.sh": "@community//plugins/c:helper.sh",
              "helpers": "@community//plugins/c:dev_dist_resources",
              "openui/air-openui-renderer.zip": "@dev_launch_air_openui_renderer//:files",
          },
          jars = {
              "lib/c.jar": [
                  "intellij.c",
              ],
          },
          main_module = "intellij.c",
          modules = {
              "@community//plugins/c": "intellij.c",
          },
          plugin_directory = "plugins/c",
      )
      """.trimIndent() + "\n",
    )
  }
}
