package org.jetbrains.intellij.build.dev

import com.intellij.openapi.util.JDOMUtil
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Namespace
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.intellij.build.devDist.planPluginPacking
import org.jetbrains.intellij.build.devDist.resolvePluginSymbolicManifest
import org.jetbrains.intellij.build.impl.LayoutPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class DevPluginCwmFrontendLayoutCallbackRuntimeTest {
  @Test
  fun `indexed callback binding keeps the original layout position`() {
    val layout = PluginLayout.plugin("test.main")
    layout.withModule("test.output")
    val omitted: LayoutPatcher = { _, _, _ -> }
    val selected: LayoutPatcher = { _, _, _ -> }
    layout.withPatch(omitted)
    layout.withPatch(selected)

    val adapter = DevPluginLayoutPreparationAdapter(
      layout = layout,
      filters = emptyList(),
      patchers = listOf(
        DevPluginLayoutPatcherInputs(
          callback = selected,
          sources = listOf(DevPluginLayoutSourceInput("test.input", "value.txt", DevPluginReference("input"))),
          layoutIndex = 1,
        )
      ),
      patchOutputs = listOf(DevPluginLayoutPatchOutput("test.output", "patched-output", listOf("value.txt"))),
      idPrefix = "indexed",
    )

    assertThat(adapter.facts.effects.keys).containsExactly("layout-patcher:1")
    assertThat(adapter.preparations().single().inputs).containsExactly("input")
  }

  @Test
  fun `known patch entries need no manifest metadata after policy resolution`() {
    val layout = PluginLayout.plugin("test.main")
    layout.withModule("test.output")
    val callback: LayoutPatcher = { _, _, _ -> }
    layout.withPatch(callback)
    val output = "patched-output"
    val adapter = DevPluginLayoutPreparationAdapter(
      layout = layout,
      filters = emptyList(),
      patchers = listOf(DevPluginLayoutPatcherInputs(
        callback = callback,
        sources = listOf(DevPluginLayoutSourceInput("test.input", "value.txt", DevPluginReference("input"))),
      )),
      patchOutputs = listOf(DevPluginLayoutPatchOutput("test.output", output, listOf("value.txt"))),
      idPrefix = "known-patches",
    )
    val asset = resolvePluginSymbolicManifest(
      asset = PluginPackingAsset(
        destination = "lib/test.jar",
        inputs = listOf("raw", output),
        recipe = CanonicalJarRecipe(
          sources = listOf(
            JarSourceRecipe("raw", "file", "none", entry = "raw.txt"),
            JarSourceRecipe(output, "prepared", "prepared"),
          ),
          writer = JarWriterRecipe(),
        ),
      ),
      preparedSourceManifests = adapter.facts.preparedSourceManifests,
      reportGap = { error(it.detail) },
    )
    val recipe = asset.recipe!!
    assertThat(recipe.writer.manifest).isEqualTo("drop")
    assertThat(recipe.sources.last().preparedManifest).isNull()

    val plan = planPluginPacking(
      plugin = layout.mainModule,
      variant = "test",
      assets = listOf(asset),
      preparations = adapter.preparations(),
      preparationRoots = emptyList(),
      artifacts = emptyList(),
    )
    assertThat(captureDevPluginLibraryLayoutConsumers(plan, adapter)).hasSize(1)
  }

  @Test
  fun `client application info uses the product values`(@TempDir root: Path) {
    val client = write(
      root.resolve("client.xml"), """
      <component xmlns="http://jetbrains.org/intellij/schema/application-info">
        <version major="2026" minor="3" eap="true"/>
        <company name="JetBrains s.r.o." url="https://www.jetbrains.com/"/>
        <build number="JBC-__BUILD__" date="__BUILD_DATE__"/>
        <names product="JetBrainsClient" fullname="JetBrains Client" script="jetbrains_client" motto="Client"/>
      </component>
    """.trimIndent()
    )
    val product = write(
      root.resolve("product.xml"), """
      <component xmlns="http://jetbrains.org/intellij/schema/application-info">
        <version major="2026" minor="3" micro="2" patch="1" full="{0}.{1}.{2}" suffix="EAP" eap="true"/>
        <build number="IU-__BUILD__" majorReleaseDate="20260909"/>
        <names product="IDEA" fullname="IntelliJ IDEA Ultimate" edition="ultimate" motto="Code"/>
      </component>
    """.trimIndent()
    )
    val buildNumber = write(root.resolve("build.txt"), "263.SNAPSHOT\n")

    val result = JDOMUtil.load(
      prepareCwmClientApplicationInfo(
        client,
        product,
        buildNumber,
        DevPluginCwmFrontendCallbackOptions(
          isEapOverride = null,
          versionSuffixOverride = null,
          nightlyBuild = false,
          branchName = "feature/client",
        ),
      )
    )
    val namespace = Namespace.getNamespace("http://jetbrains.org/intellij/schema/application-info")
    val version = result.getChild("version", namespace)
    val build = result.getChild("build", namespace)
    val names = result.getChild("names", namespace)
    assertThat(build.getAttributeValue("number")).isEqualTo("JBC-263.SNAPSHOT")
    assertThat(build.getAttributeValue("date")).isEqualTo("__BUILD_DATE__")
    assertThat(build.getAttributeValue("majorReleaseDate")).isEqualTo("20260909")
    assertThat(build.getAttributeValue("branchName")).isEqualTo("feature/client")
    assertThat(names.getAttributeValue("fullname")).isEqualTo("IntelliJ IDEA Ultimate")
    assertThat(names.getAttributeValue("edition")).isNull()
    assertThat(names.getAttributeValue("motto")).isEqualTo("Code")
    assertThat(version.getAttributeValue("micro")).isEqualTo("2")
    assertThat(version.getAttributeValue("patch")).isEqualTo("1")
  }

  private fun write(file: Path, text: String): Path {
    Files.writeString(file, text)
    return file
  }
}
