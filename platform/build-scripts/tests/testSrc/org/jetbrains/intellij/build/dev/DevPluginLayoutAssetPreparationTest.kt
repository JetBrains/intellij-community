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
  fun `the removed gzip-xml-archive transform is refused`() {
    val gzip = DevPluginLayoutAsset(destination = "resources", sources = listOf(0), transform = DevPluginLayoutAssetTransform(kind = "gzip-xml-archive"))
    val operation = DevPluginPreparationOperation(
      id = "layout-assets:gzip", kind = "layout-assets", inputs = listOf(DevPluginReference("archive")), output = "layout-assets:gzip:output",
      manifest = "keep", layoutAssets = DevPluginLayoutAssetPreparation(format = "entries", assets = listOf(gzip)),
    )
    assertThat(isPackerExecutedOperation(operation)).isFalse()
    assertThatThrownBy { devPluginPreparationOperationSignature(operation, version = 2) }
      .hasMessageContaining("No packer operation executes 'layout-assets:gzip'")
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
        manifest = "keep", layoutAssets = treeMapEntriesPreparation(sources = listOf(0)),
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
      layoutAssets = treeMapEntriesPreparation(sources = listOf(0)),
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
  fun `tree exclusions change the preparation signature`() {
    val transform = DevPluginLayoutAssetTransform.treeMap(listOf(DevPluginLayoutAssetMapping()))
    fun signature(value: DevPluginLayoutAssetTransform): String {
      return devPluginPreparationOperationSignature(DevPluginPreparationOperation(
        id = "layout-assets:tree", kind = "layout-assets", inputs = listOf(DevPluginReference("tree")), output = "tree:output", manifest = "keep",
        layoutAssets = DevPluginLayoutAssetPreparation(
          format = "tree", root = "helpers",
          assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = value)),
        ),
      ), version = 2)
    }

    assertThat(signature(transform.copy(excludes = listOf("setup.py")))).isNotEqualTo(signature(transform))
    assertThat(signature(transform.copy(directoryExcludes = listOf("tests")))).isNotEqualTo(signature(transform))
  }

  @Test
  fun `only tree mappings accept valid exclusion patterns`() {
    val tree = DevPluginLayoutAssetTransform.treeMap(listOf(DevPluginLayoutAssetMapping()))
    val transforms = listOf(
      tree.copy(excludes = listOf("")),
      tree.copy(excludes = listOf("[")),
      tree.copy(directoryExcludes = listOf("")),
      tree.copy(directoryExcludes = listOf("[")),
      DevPluginLayoutAssetTransform.archiveTree().copy(excludes = listOf("setup.py")),
      DevPluginLayoutAssetTransform.archiveTree().copy(directoryExcludes = listOf("tests")),
    )
    for (transform in transforms) {
      assertThatThrownBy {
        validateLayoutAssets(DevPluginLayoutAssetPreparation(
          format = "tree", root = "helpers",
          assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = transform)),
        ), listOf(DevPluginReference("tree")))
      }.isInstanceOf(IllegalArgumentException::class.java)
    }
  }

  @Test
  fun `archive includes and executable patterns change the preparation signature`() {
    val archive = DevPluginLayoutAssetTransform.archiveTree()
    val tree = DevPluginLayoutAssetTransform.treeMap(listOf(DevPluginLayoutAssetMapping()))
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
    assertThat(signature(tree.copy(executables = listOf("DotFiles/*.sh")), "tree")).isNotEqualTo(signature(tree, "tree"))
    assertThat(signature(DevPluginLayoutAssetTransform.archiveTree(includes = listOf("!x"), executables = listOf("y")), "archive"))
      .isEqualTo(signature(archive.copy(includes = listOf("!x"), executables = listOf("y")), "archive"))
  }

  @Test
  fun `only archive-tree accepts includes and only the tree transforms accept executable patterns`() {
    val archive = DevPluginLayoutAssetTransform.archiveTree()
    val tree = DevPluginLayoutAssetTransform.treeMap(listOf(DevPluginLayoutAssetMapping()))
    val invalid = listOf(
      tree.copy(includes = listOf("bin/**")) to "tree",
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

  private fun treeMapEntriesPreparation(sources: List<Int>): DevPluginLayoutAssetPreparation {
    val transform = DevPluginLayoutAssetTransform.treeMap(listOf(DevPluginLayoutAssetMapping()))
    return DevPluginLayoutAssetPreparation(
      format = "entries",
      assets = listOf(DevPluginLayoutAsset(destination = "resources", sources = sources, transform = transform)),
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
