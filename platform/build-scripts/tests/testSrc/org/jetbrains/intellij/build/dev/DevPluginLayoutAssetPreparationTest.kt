package org.jetbrains.intellij.build.dev

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.DISTRIBUTION_ASSET_SCOPE
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.planPluginPacking
import org.junit.jupiter.api.Test

/**
 * The generation-time rules of a layout-assets operation and of its consumers in the plan. The Go packer executes every
 * operation; the executor cases of every transform live in `layout_test.go`.
 */
internal class DevPluginLayoutAssetPreparationTest {
  @Test
  fun `a gzip-xml-archive asset requires archive inputs and an entries output`() {
    val gzip = DevPluginLayoutAsset(destination = "resources", sources = listOf(0), transform = DevPluginLayoutAssetTransform.gzipXmlArchive())
    val inputs = listOf(DevPluginReference("archive"), DevPluginReference("tree"))

    validateLayoutAssets(DevPluginLayoutAssetPreparation(
      format = "entries",
      assets = listOf(
        gzip,
        DevPluginLayoutAsset(destination = "", sources = listOf(1), transform = DevPluginLayoutAssetTransform.treeMap(listOf(DevPluginLayoutAssetMapping()))),
      ),
    ), inputs)
    assertThatThrownBy {
      validateLayoutAssets(DevPluginLayoutAssetPreparation(format = "tree", root = "payload", assets = listOf(gzip)), inputs)
    }.hasMessageContaining("an entries output")
    assertThatThrownBy {
      validateLayoutAssets(DevPluginLayoutAssetPreparation(format = "entries", assets = listOf(gzip.copy(sources = emptyList()))), inputs)
    }.hasMessageContaining("an entries output")
  }

  @Test
  fun `a file layout asset holds one plain copy or one inline text at its root`() {
    val inputs = listOf(DevPluginReference("build"))
    val inline = DevPluginLayoutAsset(destination = "jre-build.txt", transform = DevPluginLayoutAssetTransform.inlineText("21.0.7"))
    val copy = DevPluginLayoutAsset(destination = "jre-build.txt", sources = listOf(0))

    validateLayoutAssets(DevPluginLayoutAssetPreparation(format = "file", root = "jre-build.txt", assets = listOf(inline)), inputs)
    validateLayoutAssets(DevPluginLayoutAssetPreparation(format = "file", root = "jre-build.txt", assets = listOf(copy)), inputs)
    assertThatThrownBy {
      validateLayoutAssets(DevPluginLayoutAssetPreparation(format = "file", root = "jre-build.txt", assets = listOf(inline, copy)), inputs)
    }.hasMessageContaining("requires one asset")
    assertThatThrownBy {
      validateLayoutAssets(DevPluginLayoutAssetPreparation(format = "file", root = "other.txt", assets = listOf(inline)), inputs)
    }.hasMessageContaining("one asset at its root")
    assertThatThrownBy {
      validateLayoutAssets(DevPluginLayoutAssetPreparation(
        format = "file", root = "jre-build.txt",
        assets = listOf(copy.copy(transform = DevPluginLayoutAssetTransform.archiveTree())),
      ), inputs)
    }.hasMessageContaining("plain copy or inline text only")
  }

  @Test
  fun `every Go-executed operation binds its consumers in the plan`() {
    val treeAssets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = DevPluginLayoutAssetTransform.archiveTree(stripComponents = 1)))
    val fileAssets = listOf(DevPluginLayoutAsset(destination = "jre-build.txt", transform = DevPluginLayoutAssetTransform.inlineText("21.0.7")))
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
        id = "layout-assets:gzip", kind = "layout-assets", inputs = listOf(DevPluginReference("dialects")), output = "layout-assets:gzip:output",
        manifest = "keep", layoutAssets = gzipXmlArchivePreparation(sources = listOf(0)),
      ),
      DevPluginPreparationOperation(
        id = "layout-assets:file", kind = "layout-assets", output = "layout-assets:file:output",
        manifest = "keep", layoutAssets = DevPluginLayoutAssetPreparation(format = "file", root = "jre-build.txt", assets = fileAssets),
      ),
      DevPluginPreparationOperation(
        id = "native-select:library", kind = "native-select", input = DevPluginReference("library"), output = "native-select:library:output",
        manifest = "keep", filter = "library",
      ),
    )
    assertThat(operations).allMatch(::isGoExecutedOperation)
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
          destination = "lib/dialects.jar", inputs = listOf("layout-assets:gzip:output"),
          recipe = CanonicalJarRecipe(listOf(JarSourceRecipe("layout-assets:gzip:output", "prepared", "prepared")), JarWriterRecipe(manifest = "drop")),
        ),
        PluginPackingAsset(destination = "jre-build.txt", inputs = listOf("layout-assets:file:output"), classPath = false),
        PluginPackingAsset(
          destination = "lib/library.jar", inputs = listOf("native-select:library:output"),
          recipe = CanonicalJarRecipe(listOf(JarSourceRecipe("native-select:library:output", "prepared", "prepared")), JarWriterRecipe(manifest = "keep")),
        ),
        PluginPackingAsset(
          destination = "lib/native", inputs = listOf("native-select:library:output"), kind = "tree", classPath = false, scope = DISTRIBUTION_ASSET_SCOPE,
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
    validateDevPluginNativeSelectConsumers(operations.last(), plan)
    val misplacedTree = operations[1].copy(layoutAssets = DevPluginLayoutAssetPreparation(format = "tree", root = "other", assets = treeAssets))
    assertThatThrownBy { validateDevPluginLayoutAssetConsumers(misplacedTree, plan) }.hasMessageContaining("one tree asset at 'other'")
    val misplacedFile = operations[3].copy(layoutAssets = DevPluginLayoutAssetPreparation(format = "file", root = "other.txt", assets = fileAssets))
    assertThatThrownBy { validateDevPluginLayoutAssetConsumers(misplacedFile, plan) }.hasMessageContaining("one file asset at 'other.txt'")
    assertThatThrownBy { validateDevPluginNativeSelectConsumers(operations.last().copy(output = "module-filter:module:output"), plan) }
      .hasMessageContaining("one distribution tree consumer and one prepared jar source")
  }

  @Test
  fun `every operation of a plan file is Go-executed`() {
    val moduleFilter = DevPluginPreparationOperation(id = "filter", input = DevPluginReference("module"), output = "filtered", manifest = "keep")
    val gzip = DevPluginPreparationOperation(
      id = "gzip", kind = "layout-assets", inputs = listOf(DevPluginReference("jar")), output = "gzip:output", manifest = "keep",
      layoutAssets = gzipXmlArchivePreparation(sources = listOf(0)),
    )
    val inlineFile = DevPluginPreparationOperation(
      id = "file", kind = "layout-assets", output = "file:output", manifest = "keep",
      layoutAssets = DevPluginLayoutAssetPreparation(
        format = "file", root = "jre-build.txt",
        assets = listOf(DevPluginLayoutAsset(destination = "jre-build.txt", transform = DevPluginLayoutAssetTransform.inlineText("21"))),
      ),
    )
    val nativeSelect = DevPluginPreparationOperation(
      id = "native", kind = "native-select", input = DevPluginReference("library"), output = "native:output", manifest = "keep", filter = "library",
    )
    val presigned = DevPluginPreparationOperation(
      id = "presigned", kind = "native-presigned", input = DevPluginReference("library"), output = "presigned:output", manifest = "keep", filter = "library",
    )

    assertThat(isGoExecutedOperation(moduleFilter)).isTrue()
    assertThat(isGoExecutedOperation(gzip)).isTrue()
    assertThat(isGoExecutedOperation(inlineFile)).isTrue()
    assertThat(isGoExecutedOperation(nativeSelect)).isTrue()
    assertThat(readsPlatform(nativeSelect)).isTrue()
    assertThat(listOf(moduleFilter, gzip, inlineFile).none(::readsPlatform)).isTrue()
    assertThat(isGoExecutedOperation(presigned)).isFalse()
    assertThatThrownBy { devPluginPreparationOperationSignature(presigned, version = 2) }
      .hasMessageContaining("No Go operation executes 'presigned' of kind 'native-presigned'")
  }

  @Test
  fun `a native-select operation requires the library policy and recipe version 2`() {
    val nativeSelect = DevPluginPreparationOperation(
      id = "native", kind = "native-select", input = DevPluginReference("library"), output = "native:output", manifest = "keep", filter = "library",
    )

    assertThat(devPluginPreparationOperationSignature(nativeSelect, version = 2)).isNotEmpty()
    assertThatThrownBy { devPluginPreparationOperationSignature(nativeSelect, version = 1) }.hasMessageContaining("recipe version 2")
    for (invalid in listOf(nativeSelect.copy(filter = "module"), nativeSelect.copy(filter = ""), nativeSelect.copy(manifest = "drop"), nativeSelect.copy(excludes = listOf("a/**")))) {
      assertThatThrownBy { devPluginPreparationOperationSignature(invalid, version = 2) }.hasMessageContaining("original library policy")
    }
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
      DevPluginLayoutAssetTransform.inlineText("x").copy(executables = listOf("*")) to "archive",
    )
    for ((transform, input) in invalid) {
      assertThatThrownBy {
        validateLayoutAssets(DevPluginLayoutAssetPreparation(
          format = "tree", root = "bin",
          assets = listOf(DevPluginLayoutAsset(destination = if (transform.kind == "inline-text") "bin/x" else "", sources = if (transform.kind == "inline-text") emptyList() else listOf(0), transform = transform)),
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

  private fun gzipXmlArchivePreparation(sources: List<Int>): DevPluginLayoutAssetPreparation {
    return DevPluginLayoutAssetPreparation(
      format = "entries",
      assets = listOf(DevPluginLayoutAsset(destination = "resources", sources = sources, transform = DevPluginLayoutAssetTransform.gzipXmlArchive())),
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
