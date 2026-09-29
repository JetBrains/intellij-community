package org.jetbrains.intellij.build.dev

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
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
      assertThatThrownBy { devPluginPreparationOperationSignature(operation, version = 2) }
        .hasMessageContaining("No packer operation executes 'layout-assets:$kind'")
    }
  }

  @Test
  fun `every packer-executed operation binds its consumers in the plan`() {
    val treeAssets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = DevPluginLayoutAssetTransform.archiveTree(stripComponents = 1)))
    val operations = listOf(
      DevPluginPreparationOperation(
        id = "module-filter:module", input = DevPluginReference("module"), output = "module-filter:module:output", manifest = "drop",
        excludes = listOf("drop/**"),
      ),
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
        PluginPackingAsset(
          destination = "lib/main.jar", inputs = listOf("module-filter:module:output"),
          recipe = CanonicalJarRecipe(listOf(JarSourceRecipe("module-filter:module:output", "prepared", "prepared")), JarWriterRecipe(manifest = "drop")),
        ),
        PluginPackingAsset(destination = "payload", inputs = listOf("layout-assets:tree:output"), kind = "tree", classPath = false),
        PluginPackingAsset(
          destination = "lib/resources.jar", inputs = listOf("layout-assets:entries:output"),
          recipe = CanonicalJarRecipe(listOf(JarSourceRecipe("layout-assets:entries:output", "prepared", "prepared")), JarWriterRecipe(manifest = "drop")),
        ),
      ),
      preparations = operations.map { operation ->
        PluginPackingPreparation(
          id = operation.id,
          inputs = operation.sourceReferences().map(DevPluginReference::artifact),
          outputs = listOf(operation.output),
          modelSignature = devPluginPreparationOperationSignature(operation, version = 2),
        )
      },
      preparationRoots = emptyList(),
      artifacts = emptyList(),
    )

    for (operation in operations.filter { it.kind == "layout-assets" }) {
      validateDevPluginLayoutAssetConsumers(operation, plan)
    }
    val misplacedTree = operations[1].copy(layoutAssets = DevPluginLayoutAssetPreparation(format = "tree", root = "other", assets = treeAssets))
    assertThatThrownBy { validateDevPluginLayoutAssetConsumers(misplacedTree, plan) }.hasMessageContaining("one tree asset at 'other'")
  }

  @Test
  fun `every operation of a plan file is packer-executed`() {
    val moduleFilter = DevPluginPreparationOperation(id = "filter", input = DevPluginReference("module"), output = "filtered", manifest = "keep")
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
      id = "presigned", kind = "native-presigned", input = DevPluginReference("library"), output = "presigned:output", manifest = "keep", filter = "library",
    )

    assertThat(isPackerExecutedOperation(moduleFilter)).isTrue()
    assertThat(isPackerExecutedOperation(entries)).isTrue()
    assertThat(isPackerExecutedOperation(file)).isFalse()
    assertThat(isPackerExecutedOperation(presigned)).isFalse()
    assertThatThrownBy { devPluginPreparationOperationSignature(presigned, version = 2) }
      .hasMessageContaining("No packer operation executes 'presigned' of kind 'native-presigned'")
    assertThatThrownBy { devPluginPreparationOperationSignature(file, version = 2) }
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
  fun `archive includes and executable patterns change the preparation signature`() {
    val archive = DevPluginLayoutAssetTransform.archiveTree()
    fun signature(value: DevPluginLayoutAssetTransform, input: String): String {
      return devPluginPreparationOperationSignature(DevPluginPreparationOperation(
        id = "layout-assets:native", kind = "layout-assets", inputs = listOf(DevPluginReference(input)), output = "native:output", manifest = "keep",
        layoutAssets = DevPluginLayoutAssetPreparation(
          format = "tree", root = "bin",
          assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = value)),
        ),
      ), version = 2)
    }

    assertThat(signature(archive.copy(includes = listOf("bin/**", "!bin/LLDBFrontend")), "archive")).isNotEqualTo(signature(archive, "archive"))
    assertThat(signature(archive.copy(executables = listOf("bin/*")), "archive")).isNotEqualTo(signature(archive, "archive"))
    assertThat(signature(DevPluginLayoutAssetTransform.archiveTree(includes = listOf("!x"), executables = listOf("y")), "archive"))
      .isEqualTo(signature(archive.copy(includes = listOf("!x"), executables = listOf("y")), "archive"))
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

  /** Runs the generation-time validation of one layout-assets operation through its signature. */
  private fun validateLayoutAssets(preparation: DevPluginLayoutAssetPreparation, inputs: List<DevPluginReference>) {
    val operation = DevPluginPreparationOperation(
      id = "layout-assets:test", kind = "layout-assets", inputs = inputs, output = "layout-assets:test:output", manifest = "keep", layoutAssets = preparation,
    )
    devPluginPreparationOperationSignature(operation, version = 2)
  }
}
