package org.jetbrains.intellij.build.dev

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.planPluginPacking
import org.junit.jupiter.api.Test

/**
 * The generation-time rules of a layout-assets operation and of its consumers in the plan. The packer executes every
 * operation; the executor cases of every transform live in `tests/layout.rs` of the `pluginpack` crate.
 */
internal class DevPluginLayoutAssetPreparationTest {
  @Test
  fun `a removed transform is refused`() {
    for (kind in listOf("gzip-xml-archive", "tree-map")) {
      val asset = DevPluginLayoutAsset(destination = "resources", sources = listOf(0), transform = DevPluginLayoutAssetTransform(kind = kind))
      val operation = DevPluginPreparationOperation(
        id = "layout-assets:$kind", kind = "layout-assets", inputs = listOf(DevPluginReference("archive")), output = "layout-assets:$kind:output",
        manifest = "keep", layoutAssets = DevPluginLayoutAssetPreparation(format = "entries", assets = listOf(asset)),
      )
      assertThat(isPackerExecutedOperation(operation)).isFalse()
      assertThatThrownBy { validatePreparationOperation(operation) }
        .hasMessageContaining("No packer operation executes 'layout-assets:$kind'")
    }
  }

  @Test
  fun `every packer-executed operation binds its consumers in the plan`() {
    val treeAssets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = DevPluginLayoutAssetTransform.archiveTree(stripComponents = 1)))
    val operations = listOf(
      DevPluginPreparationOperation(
        id = "layout-assets:tree", kind = "layout-assets", inputs = listOf(DevPluginReference("archive")), output = "layout-assets:tree:output",
        manifest = "keep", layoutAssets = DevPluginLayoutAssetPreparation(format = "tree", root = "payload", assets = treeAssets),
      ),
      DevPluginPreparationOperation(
        id = "layout-assets:entries", kind = "layout-assets", inputs = listOf(DevPluginReference("resources")), output = "layout-assets:entries:output",
        manifest = "keep", layoutAssets = plainCopyEntriesPreparation(sources = listOf(0)),
      ),
    )
    assertThat(operations).allMatch(::isPackerExecutedOperation)
    val plan = planPluginPacking(
      plugin = "test.plugin",
      variant = "linux_x64",
      assets = listOf(
        PluginPackingAsset(destination = "payload", inputs = listOf("layout-assets:tree:output"), kind = "tree", classPath = false),
        PluginPackingAsset(
          destination = "lib/resources.jar", inputs = listOf("layout-assets:entries:output"),
          recipe = CanonicalJarRecipe(listOf(JarSourceRecipe("layout-assets:entries:output", "prepared", "prepared")), JarWriterRecipe(manifest = "drop")),
        ),
      ),
      operations = operations,
      preparationRoots = emptyList(),
      artifacts = emptyList(),
    )

    for (operation in operations) {
      validateDevPluginLayoutAssetConsumers(operation, plan)
    }
    val misplacedTree = operations[0].copy(layoutAssets = DevPluginLayoutAssetPreparation(format = "tree", root = "other", assets = treeAssets))
    assertThatThrownBy { validateDevPluginLayoutAssetConsumers(misplacedTree, plan) }.hasMessageContaining("one tree asset at 'other'")
  }

  @Test
  fun `every operation of a plan file is packer-executed`() {
    val moduleFilter = DevPluginPreparationOperation(
      id = "filter", kind = "module-filter", inputs = listOf(DevPluginReference("module")), output = "filtered", manifest = "keep",
    )
    val entries = DevPluginPreparationOperation(
      id = "entries", kind = "layout-assets", inputs = listOf(DevPluginReference("resources")), output = "entries:output", manifest = "keep",
      layoutAssets = plainCopyEntriesPreparation(sources = listOf(0)),
    )
    val file = DevPluginPreparationOperation(
      id = "file", kind = "layout-assets", inputs = listOf(DevPluginReference("build")), output = "file:output", manifest = "keep",
      layoutAssets = DevPluginLayoutAssetPreparation(
        format = "file", root = "jre-build.txt",
        assets = listOf(DevPluginLayoutAsset(destination = "jre-build.txt", sources = listOf(0))),
      ),
    )
    val presigned = DevPluginPreparationOperation(
      id = "presigned", kind = "native-presigned", inputs = listOf(DevPluginReference("library")), output = "presigned:output", manifest = "keep", filter = "library",
    )

    assertThat(isPackerExecutedOperation(moduleFilter)).isFalse()
    assertThat(isPackerExecutedOperation(entries)).isTrue()
    assertThat(isPackerExecutedOperation(file)).isFalse()
    assertThat(isPackerExecutedOperation(presigned)).isFalse()
    assertThatThrownBy { validatePreparationOperation(moduleFilter) }
      .hasMessageContaining("No packer operation executes 'filter' of kind 'module-filter'")
    assertThatThrownBy { validatePreparationOperation(presigned) }
      .hasMessageContaining("No packer operation executes 'presigned' of kind 'native-presigned'")
    assertThatThrownBy { validatePreparationOperation(file) }
      .hasMessageContaining("No packer operation executes 'file' of kind 'layout-assets'")
  }

  @Test
  fun `a plain copy of a directory writes the output root and takes one source`() {
    validateLayoutAssets(DevPluginLayoutAssetPreparation(
      format = "entries",
      assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0)), DevPluginLayoutAsset(destination = "", sources = listOf(1))),
    ), listOf(DevPluginReference("properties"), DevPluginReference("descriptions")))
    assertThatThrownBy {
      validateLayoutAssets(DevPluginLayoutAssetPreparation(
        format = "entries",
        assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0, 1))),
      ), listOf(DevPluginReference("properties"), DevPluginReference("descriptions")))
    }.hasMessageContaining("A direct layout asset requires one source")
  }

  @Test
  fun `archive includes and executable patterns change the operation text`() {
    val archive = DevPluginLayoutAssetTransform.archiveTree()
    fun text(value: DevPluginLayoutAssetTransform, input: String): String {
      val operation = DevPluginPreparationOperation(
        id = "layout-assets:native", kind = "layout-assets", inputs = listOf(DevPluginReference(input)), output = "native:output", manifest = "keep",
        layoutAssets = DevPluginLayoutAssetPreparation(
          format = "tree", root = "bin",
          assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = value)),
        ),
      )
      validatePreparationOperation(operation)
      return Json.encodeToString(DevPluginPreparationOperation.serializer(), operation)
    }

    assertThat(text(archive.copy(includes = listOf("bin/**", "!bin/LLDBFrontend")), "archive")).isNotEqualTo(text(archive, "archive"))
    assertThat(text(archive.copy(executables = listOf("bin/*")), "archive")).isNotEqualTo(text(archive, "archive"))
    assertThat(text(DevPluginLayoutAssetTransform.archiveTree(includes = listOf("!x"), executables = listOf("y")), "archive"))
      .isEqualTo(text(archive.copy(includes = listOf("!x"), executables = listOf("y")), "archive"))
  }

  @Test
  fun `archive-tree accepts valid includes and executable patterns`() {
    val archive = DevPluginLayoutAssetTransform.archiveTree()
    val invalid = listOf(
      archive.copy(includes = listOf("")) to "archive",
      archive.copy(includes = listOf("!")) to "archive",
      archive.copy(includes = listOf("[")) to "archive",
      archive.copy(executables = listOf("")) to "archive",
      archive.copy(executables = listOf("{a")) to "archive",
    )
    for ((transform, input) in invalid) {
      assertThatThrownBy {
        validateLayoutAssets(DevPluginLayoutAssetPreparation(
          format = "tree", root = "bin",
          assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = transform)),
        ), listOf(DevPluginReference(input)))
      }.isInstanceOf(IllegalArgumentException::class.java)
    }
    validateLayoutAssets(DevPluginLayoutAssetPreparation(
      format = "tree", root = "bin",
      assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = archive.copy(includes = listOf("!bin/LLDBFrontend"), executables = listOf("bin/*")))),
    ), listOf(DevPluginReference("archive")))
  }

  @Test
  fun `a payload asset carries no host platforms and the encoding omits them`() {
    val asset = DevPluginLayoutAsset(destination = "bin/tool", sources = listOf(0), hostPlatforms = listOf("darwin_aarch64"))
    assertThat(Json.encodeToString(DevPluginLayoutAsset.serializer(), asset)).isEqualTo("""{"destination":"bin/tool","sources":[0]}""")
    assertThatThrownBy {
      validateLayoutAssets(DevPluginLayoutAssetPreparation(format = "tree", root = "bin", assets = listOf(asset.copy(destination = ""))), listOf(DevPluginReference("archive")))
    }.hasMessageContaining("host platforms")
  }

  private fun plainCopyEntriesPreparation(sources: List<Int>): DevPluginLayoutAssetPreparation {
    return DevPluginLayoutAssetPreparation(
      format = "entries",
      assets = listOf(DevPluginLayoutAsset(destination = "resources", sources = sources)),
    )
  }

  /** Runs the generation-time validation of one layout-assets operation. */
  private fun validateLayoutAssets(preparation: DevPluginLayoutAssetPreparation, inputs: List<DevPluginReference>) {
    val operation = DevPluginPreparationOperation(
      id = "layout-assets:test", kind = "layout-assets", inputs = inputs, output = "layout-assets:test:output", manifest = "keep", layoutAssets = preparation,
    )
    validatePreparationOperation(operation)
  }
}
