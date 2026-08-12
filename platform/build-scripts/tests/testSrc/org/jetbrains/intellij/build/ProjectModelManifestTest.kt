// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.dev.materializeProjectModelTree
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

class ProjectModelManifestTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun copiesAndCreatesAtTheDeclaredDestinations() {
    val source = writeSource("community/platform/build-scripts/intellij.platform.buildScripts.iml", "<module/>")

    val target = materialize(
      "copy\t$source\tcommunity/platform/build-scripts/intellij.platform.buildScripts.iml",
      "create\t\t.ultimate.root.marker",
      "create\t\tcommunity/.community.root.marker",
    )

    assertThat(target.resolve("community/platform/build-scripts/intellij.platform.buildScripts.iml").readText()).isEqualTo("<module/>")
    assertThat(target.resolve(".ultimate.root.marker")).exists()
    assertThat(target.resolve("community/.community.root.marker")).exists()
  }

  @Test
  fun blankLinesAreNotRows() {
    val target = materialize("", "create\t\t.ultimate.root.marker", "   ", "")

    assertThat(target.resolve(".ultimate.root.marker")).exists()
  }

  @Test
  fun aMissingSourceFails() {
    val missing = tempDir.resolve("sources/gone.iml")

    assertThatThrownBy { materialize("copy\t$missing\tgone.iml") }
      .isInstanceOf(NoSuchFileException::class.java)
  }

  @Test
  fun aDestinationOutsideTheTreeIsRejected() {
    val source = writeSource("build.txt", "252.1")

    assertThatThrownBy { materialize("copy\t$source\t../escaped/build.txt") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("escapes")
  }

  @Test
  fun aMalformedRowIsRejected() {
    assertThatThrownBy { materialize("create\t.ultimate.root.marker") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("action<TAB>source<TAB>destination")
  }

  @Test
  fun anUnknownActionIsRejected() {
    assertThatThrownBy { materialize("link\t\tsomewhere") }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("link")
  }

  /** The tree is an output of the manifest alone, so a previous run's leftovers must not survive into the next one. */
  @Test
  fun aStaleTreeIsReplacedRatherThanMergedInto() {
    val source = writeSource("build.txt", "252.1")
    val target = tempDir.resolve("tree")
    target.resolve("community").createDirectories()
    Files.writeString(target.resolve("community/stale.iml"), "<module/>")

    materializeProjectModelTree(manifest = writeManifest("copy\t$source\tbuild.txt"), target = target)

    assertThat(target.resolve("build.txt")).exists()
    assertThat(target.resolve("community/stale.iml").exists()).isFalse()
  }

  private fun materialize(vararg lines: String): Path =
    materializeProjectModelTree(manifest = writeManifest(*lines), target = tempDir.resolve("tree"))

  private fun writeManifest(vararg lines: String): Path {
    val manifest = tempDir.resolve("model.manifest")
    Files.writeString(manifest, lines.joinToString(separator = "\n", postfix = "\n"))
    return manifest
  }

  private fun writeSource(relativePath: String, content: String): Path {
    val file = tempDir.resolve("sources").resolve(relativePath)
    file.parent.createDirectories()
    Files.writeString(file, content)
    return file
  }
}
