package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class DevPluginLibraryLayoutRecipeRuntimeTest {
  @Test
  fun `raw binding stays stable when Bazel transports a tree file`(@TempDir tempDir: Path) {
    val backingRoot = Files.createDirectories(tempDir.resolve("backing"))
    val source = Files.writeString(backingRoot.resolve("product_16_frontend.svg"), "icon")
    val transportRoot = Files.createDirectories(tempDir.resolve("transport"))
    Files.createSymbolicLink(transportRoot.resolve(source.fileName), source)
    val reference = DevPluginReference("branding", source.fileName.toString())
    val context = context(reference, transportRoot, tempDir.resolve("prepared"))

    val resolved = requireDevPluginLibraryLayoutInput(context, reference, transportRoot.resolve(reference.path))
    assertThat(Files.isSameFile(resolved, source)).isTrue()
  }

  @Test
  fun `raw binding rejects a different file`(@TempDir tempDir: Path) {
    val declaredRoot = Files.createDirectories(tempDir.resolve("declared"))
    val otherRoot = Files.createDirectories(tempDir.resolve("other"))
    val reference = DevPluginReference("branding", "product_16_frontend.svg")
    Files.writeString(declaredRoot.resolve(reference.path), "declared")
    val other = Files.writeString(otherRoot.resolve(reference.path), "other")
    val context = context(reference, declaredRoot, tempDir.resolve("prepared"))

    assertThatThrownBy { requireDevPluginLibraryLayoutInput(context, reference, other) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("raw library layout binding changed")
  }

  @Test
  fun `raw binding reports a missing file before it compares identity`(@TempDir tempDir: Path) {
    val missing = tempDir.resolve("missing.svg")
    val reference = DevPluginReference("branding")
    val context = context(reference, missing, tempDir.resolve("prepared"), kind = "file")

    assertThatThrownBy { requireDevPluginLibraryLayoutInput(context, reference, missing) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Missing library layout input file")
  }

  private fun context(reference: DevPluginReference, root: Path, output: Path, kind: String = "directory"): DevPluginPreparationContext {
    val definition = PluginPackingPreparation(
      id = "library-layout:patches",
      inputs = listOf(reference.artifact),
      outputs = emptyList(),
      modelSignature = "test",
    )
    val catalogue = DevPluginArtifactCatalogue(
      artifacts = listOf(DevPluginArtifact(reference.artifact, kind, root.toString())),
    )
    return DevPluginPreparationContext(definition, PreparationCatalogue(catalogue), output)
  }
}
