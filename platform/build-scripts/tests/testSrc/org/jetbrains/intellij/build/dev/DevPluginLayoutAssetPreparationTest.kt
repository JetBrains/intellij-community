package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.planPluginPacking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The Kotlin layout-assets executor after the Go packer took the tree and entries transforms: the `gzip-xml-archive`
 * entries operation and the `file` operation. The executor cases of the Go transforms live in `layout_test.go`.
 */
internal class DevPluginLayoutAssetPreparationTest {
  @Test
  fun `gzip XML archives accept JAR files and keep source order`(@TempDir tempDir: Path) {
    val first = tempDir.resolve("first.jar")
    val second = tempDir.resolve("second.zip")
    writeZip(first, listOf(
      TestZipEntry("a.xml", "a".toByteArray()),
      TestZipEntry("same.xml", "first".toByteArray()),
    ))
    writeZip(second, listOf(
      TestZipEntry("b.xml", "b".toByteArray()),
      TestZipEntry("same.xml", "second".toByteArray()),
    ))

    val result = runPreparation(
      tempDir,
      gzipXmlArchivePreparation(sources = listOf(0, 1)),
      listOf(TestInput("first", first), TestInput("second", second)),
    )
    val entries = result.sources.single().sources.single().entries

    assertThat(entries.map(DevPluginPreparedEntry::name)).containsExactly(
      "resources/a.xml.gzip",
      "resources/same.xml.gzip",
      "resources/b.xml.gzip",
    )
    assertThat(entries.map { readGzip(result.content(it)) }).containsExactly("a", "first", "b")
  }

  @Test
  fun `gzip XML archives read zip and jar archives only`(@TempDir tempDir: Path) {
    val archive = Files.writeString(tempDir.resolve("resources.tar.gz"), "not read")

    assertThatThrownBy {
      runPreparation(tempDir, gzipXmlArchivePreparation(sources = listOf(0)), listOf(TestInput("archive", archive)))
    }.hasMessageContaining("reads a zip or jar archive")
  }

  @Test
  fun `gzip XML archives reject an entry that is not XML`(@TempDir tempDir: Path) {
    val archive = tempDir.resolve("resources.jar")
    writeZip(archive, listOf(TestZipEntry("a.xml", "a".toByteArray()), TestZipEntry("notes.txt", "text".toByteArray())))

    assertThatThrownBy {
      runPreparation(tempDir, gzipXmlArchivePreparation(sources = listOf(0)), listOf(TestInput("archive", archive)))
    }.hasMessageContaining("Unexpected file 'notes.txt'")
  }

  @Test
  fun `inline text produces one exact file without inputs`(@TempDir tempDir: Path) {
    val result = runPreparation(
      tempDir,
      DevPluginLayoutAssetPreparation(
        format = "file",
        root = "jre-build.txt",
        assets = listOf(DevPluginLayoutAsset(
          destination = "jre-build.txt",
          transform = DevPluginLayoutAssetTransform.inlineText("21.0.7"),
        )),
      ),
      emptyList(),
    )
    val entries = result.sources.single().sources.single().entries

    assertThat(entries.map(DevPluginPreparedEntry::name)).containsExactly("jre-build.txt")
    assertThat(result.content(entries.single())).isEqualTo("21.0.7".toByteArray())
  }

  @Test
  fun `a file layout asset copies one regular file`(@TempDir tempDir: Path) {
    val source = Files.writeString(tempDir.resolve("build.txt"), "build")

    val result = runPreparation(
      tempDir,
      DevPluginLayoutAssetPreparation(
        format = "file",
        root = "jre-build.txt",
        assets = listOf(DevPluginLayoutAsset(destination = "jre-build.txt", sources = listOf(0))),
      ),
      listOf(TestInput("build", source)),
    )
    val entries = result.sources.single().sources.single().entries

    assertThat(entries.map(DevPluginPreparedEntry::name)).containsExactly("jre-build.txt")
    assertThat(result.content(entries.single())).isEqualTo("build".toByteArray())
  }

  @Test
  fun `a file layout asset requires a regular file`(@TempDir tempDir: Path) {
    val source = Files.createDirectories(tempDir.resolve("build"))

    assertThatThrownBy {
      runPreparation(
        tempDir,
        DevPluginLayoutAssetPreparation(
          format = "file",
          root = "jre-build.txt",
          assets = listOf(DevPluginLayoutAsset(destination = "jre-build.txt", sources = listOf(0))),
        ),
        listOf(TestInput("build", source, "directory")),
      )
    }.hasMessageContaining("requires a regular file")
  }

  @Test
  fun `a gzip-xml-archive asset shares its operation with gzip-xml-archive assets only`() {
    val gzip = DevPluginLayoutAsset(destination = "resources", sources = listOf(0), transform = DevPluginLayoutAssetTransform.gzipXmlArchive())
    val inputs = listOf(DevPluginReference("archive"), DevPluginReference("tree"))

    assertThatThrownBy {
      validateLayoutAssets(DevPluginLayoutAssetPreparation(
        format = "entries",
        assets = listOf(
          gzip,
          DevPluginLayoutAsset(destination = "", sources = listOf(1), transform = DevPluginLayoutAssetTransform.treeMap(listOf(DevPluginLayoutAssetMapping()))),
        ),
      ), inputs)
    }.hasMessageContaining("gzip-xml-archive assets only")
    assertThatThrownBy {
      validateLayoutAssets(DevPluginLayoutAssetPreparation(format = "tree", root = "payload", assets = listOf(gzip)), inputs)
    }.hasMessageContaining("an entries output")
  }

  @Test
  fun `a Go-executed operation is emitted into the recipe and reads no preparation input`(@TempDir tempDir: Path) {
    val module = Files.writeString(tempDir.resolve("module.jar"), "module")
    val archive = Files.writeString(tempDir.resolve("assets.tar.gz"), "archive")
    val tree = Files.createDirectories(tempDir.resolve("properties"))
    val treeAssets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = DevPluginLayoutAssetTransform.archiveTree(stripComponents = 1)))
    val entryAssets = listOf(DevPluginLayoutAsset(
      destination = "",
      sources = listOf(0),
      transform = DevPluginLayoutAssetTransform.treeMap(listOf(DevPluginLayoutAssetMapping(pattern = "*.properties", destination = "messages"))),
    ))
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
        id = "layout-assets:entries", kind = "layout-assets", inputs = listOf(DevPluginReference("tree")), output = "layout-assets:entries:output",
        manifest = "keep", layoutAssets = DevPluginLayoutAssetPreparation(format = "entries", assets = entryAssets),
      ),
    )
    assertThat(operations).allMatch(::isGoExecutedOperation)
    val recipe = DevPluginPreparationRecipe(version = 2, operations = operations)
    val plan = planPluginPacking(
      plugin = "test.plugin",
      variant = "test",
      assets = listOf(
        PluginPackingAsset(
          destination = "lib/main.jar", inputs = listOf("module-filter:module:output"),
          recipe = CanonicalJarRecipe(listOf(JarSourceRecipe("module-filter:module:output", "prepared", "prepared")), JarWriterRecipe(manifest = "drop")),
        ),
        PluginPackingAsset(destination = "payload", inputs = listOf("layout-assets:tree:output"), kind = "tree", classPath = false),
        PluginPackingAsset(
          destination = "lib/localization.jar", inputs = listOf("layout-assets:entries:output"),
          recipe = CanonicalJarRecipe(listOf(JarSourceRecipe("layout-assets:entries:output", "prepared", "prepared")), JarWriterRecipe(manifest = "drop")),
        ),
      ),
      preparations = operations.map { operation ->
        PluginPackingPreparation(
          id = operation.id,
          inputs = (if (operation.kind == "module-filter") listOf(operation.input) else operation.inputs).map(DevPluginReference::artifact),
          outputs = listOf(operation.output),
          modelSignature = devPluginPreparationOperationSignature(operation, version = 2),
        )
      },
      preparationRoots = emptyList(),
      artifacts = emptyList(),
    )
    val catalogue = DevPluginArtifactCatalogue(artifacts = listOf(
      DevPluginArtifact(id = "module", kind = "file", root = module.toString()),
      DevPluginArtifact(id = "archive", kind = "file", root = archive.toString()),
      DevPluginArtifact(id = "tree", kind = "directory", root = tree.toString()),
    ))

    validateDevPluginOperations(plan, catalogue.toPlanCatalogue(), recipe.operations)
    val derivation = deriveDevPluginInputs(plan, catalogue.toPlanCatalogue(), recipe.operations)
    assertThat(derivation.preparationInputs).isEmpty()
    assertThat(derivation.remainderInputs).containsExactly("module", "archive", "tree")
    assertThat(derivation.inputs).isEqualTo(derivation.remainderInputs)
    val actions = compileDevPluginPreparationActions(recipe, plan, catalogue)
    assertThat(actions).isEmpty()
    assertThat(devPluginPreparationKind(recipe.operations)).isEqualTo("none")

    val result = prepareDevPlugin(
      plan = plan,
      runtimeLayoutSignature = plan.layoutSignature,
      remainderInputIds = derivation.inputs,
      catalogue = catalogue,
      cachedDescriptorContent = "<idea-plugin/>".toByteArray(),
      pluginDirectory = Path.of("plugins/test"),
      outputDirectory = tempDir.resolve("prepared-output"),
      preparationActions = actions,
      goExecutedOperations = recipe.operations,
    )

    assertThat(result.catalogue.artifacts.map(DevPluginArtifact::id)).containsExactly("module", "archive", "tree")
    assertThat(result.recipe.operations).containsExactly(
      DevPluginExecutionOperation(
        kind = "jar", destination = "lib/main.jar", mode = 420, options = DevPluginExecutionJarOptions(),
        sources = listOf(DevPluginExecutionSource(
          kind = "archive", input = DevPluginReference("module"), filter = "module", excludes = listOf("drop/**"), manifest = "drop",
        )),
      ),
      DevPluginExecutionOperation(
        kind = "layout-tree", destination = "payload",
        layout = DevPluginExecutionLayoutAssets(inputs = listOf(DevPluginReference("archive")), assets = treeAssets),
      ),
      DevPluginExecutionOperation(
        kind = "jar", destination = "lib/localization.jar", mode = 420, options = DevPluginExecutionJarOptions(),
        sources = listOf(DevPluginExecutionSource(
          kind = "layout", manifest = "keep", layout = DevPluginExecutionLayoutAssets(inputs = listOf(DevPluginReference("tree")), assets = entryAssets),
        )),
      ),
    )
    assertThat(Files.list(result.preparedDirectory).use { it.count() }).isZero()
  }

  @Test
  fun `the preparation kind follows the operation kinds`() {
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
    val presigned = DevPluginPreparationOperation(
      id = "native", kind = "native-presigned", input = DevPluginReference("library"), output = "native:output", manifest = "keep", filter = "library",
    )

    assertThat(isGoExecutedOperation(moduleFilter)).isTrue()
    assertThat(isGoExecutedOperation(gzip)).isFalse()
    assertThat(isGoExecutedOperation(inlineFile)).isFalse()
    assertThat(readsPlatform(presigned)).isTrue()
    assertThat(listOf(moduleFilter, gzip, inlineFile).none(::readsPlatform)).isTrue()
    assertThat(devPluginPreparationKind(emptyList())).isEqualTo("none")
    assertThat(devPluginPreparationKind(listOf(moduleFilter))).isEqualTo("none")
    assertThat(devPluginPreparationKind(listOf(moduleFilter, gzip))).isEqualTo("plain")
    assertThat(devPluginPreparationKind(listOf(inlineFile))).isEqualTo("plain")
    assertThat(devPluginPreparationKind(listOf(moduleFilter, presigned))).isEqualTo("callback")
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

  /** Runs the Kotlin action of one layout-assets operation. The operation must not be Go-executed. */
  private fun runPreparation(
    tempDir: Path,
    preparation: DevPluginLayoutAssetPreparation,
    inputs: List<TestInput>,
  ): PreparedLayout {
    Files.createDirectories(tempDir)
    val output = "layout-assets:test:output"
    val references = inputs.map { DevPluginReference(it.id, it.referencePath) }
    val operation = DevPluginPreparationOperation(
      id = "layout-assets:test",
      kind = "layout-assets",
      inputs = references,
      output = output,
      manifest = "keep",
      layoutAssets = preparation,
    )
    assertThat(isGoExecutedOperation(operation)).isFalse()
    val definition = PluginPackingPreparation(
      id = operation.id,
      inputs = references.map(DevPluginReference::artifact).distinct(),
      outputs = listOf(output),
      modelSignature = devPluginPreparationOperationSignature(operation, version = 2),
    )
    val assets = when (preparation.format) {
      "entries" -> emptyList()
      "file" -> listOf(PluginPackingAsset(
        destination = preparation.root,
        inputs = listOf(output),
        mode = preparation.assets.single().mode.takeIf { it != 0 } ?: 420,
        classPath = false,
      ))
      else -> error("The format '${preparation.format}' is executed by the Go packer")
    }
    val plan = planPluginPacking(
      plugin = "test.plugin",
      variant = "test",
      assets = assets,
      preparations = listOf(definition),
      preparationRoots = if (preparation.format == "entries") listOf(output) else emptyList(),
      artifacts = emptyList(),
    )
    val catalogue = DevPluginArtifactCatalogue(artifacts = inputs.map {
      DevPluginArtifact(id = it.id, kind = it.kind, root = it.path.toString())
    })
    val recipe = DevPluginPreparationRecipe(version = 2, operations = listOf(operation))
    validateDevPluginOperations(plan, catalogue.toPlanCatalogue(), recipe.operations)
    deriveDevPluginInputs(plan, catalogue.toPlanCatalogue(), recipe.operations)
    val action = compileDevPluginPreparationActions(recipe, plan, catalogue).getValue(operation.id)
    val context = DevPluginPreparationContext(
      definition = definition,
      catalogue = PreparationCatalogue(catalogue),
      outputDirectory = tempDir.resolve("prepared"),
      plugin = plan.plugin,
      layoutSignature = plan.layoutSignature,
    )
    val sources = action.prepare(context)
    return PreparedLayout(root = tempDir.resolve("prepared/0"), sources = sources)
  }

  private fun writeZip(path: Path, entries: List<TestZipEntry>) {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { archive ->
      for (entry in entries) {
        archive.putNextEntry(ZipEntry(entry.name).apply { time = 0 })
        archive.write(entry.content)
        archive.closeEntry()
      }
    }
    Files.write(path, bytes.toByteArray())
  }

  private fun readGzip(content: ByteArray): String {
    return GZIPInputStream(ByteArrayInputStream(content)).use { it.readAllBytes().toString(Charsets.UTF_8) }
  }

  private data class TestInput(
    @JvmField val id: String,
    @JvmField val path: Path,
    @JvmField val kind: String = "file",
    @JvmField val referencePath: String = "",
  )

  private class TestZipEntry(
    @JvmField val name: String,
    @JvmField val content: ByteArray,
  )

  private data class PreparedLayout(
    @JvmField val root: Path,
    @JvmField val sources: List<DevPluginPreparedSource>,
  ) {
    fun content(entry: DevPluginPreparedEntry): ByteArray {
      return Files.readAllBytes(root.resolve(requireNotNull(entry.input).path))
    }
  }
}
