// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

internal class PrepackedPluginContentCollectorTest {
  @Test
  fun `copies one packed jar into each validated plugin destination`(@TempDir tempDir: Path) {
    val jar = tempDir.resolve("shared.jar")
    Files.writeString(jar, "packed bytes")
    val metadata = writeLines(
      tempDir.resolve("jars.tsv"),
      "plugin.one\tcontent.shared\tmodules/content.shared.jar\t$jar",
      "plugin.two\tcontent.shared\tmodules/content.shared.jar\t$jar",
    )
    val placements = writeLines(
      tempDir.resolve("placements.tsv"),
      "plugin.two\tcontent.shared\tplugins/two/lib/modules/content.shared.jar",
      "plugin.one\tcontent.shared\tplugins/one/lib/modules/content.shared.jar",
    )
    val output = tempDir.resolve("output")

    assertThat(collectPrepackedPluginContentJars(metadata, listOf(placements), output)).isEqualTo(2)
    assertThat(Files.readString(output.resolve("plugins/one/lib/modules/content.shared.jar"))).isEqualTo("packed bytes")
    assertThat(Files.readString(output.resolve("plugins/two/lib/modules/content.shared.jar"))).isEqualTo("packed bytes")
    if (Files.getFileStore(output).supportsFileAttributeView(PosixFileAttributeView::class.java)) {
      assertThat(Files.getPosixFilePermissions(output.resolve("plugins/one/lib/modules/content.shared.jar"))).isEqualTo(setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.OTHERS_READ,
      ))
    }
  }

  @Test
  fun `rejects missing and unknown placements`(@TempDir tempDir: Path) {
    val jar = tempDir.resolve("content.jar")
    Files.writeString(jar, "content")
    val metadata = writeLines(tempDir.resolve("jars.tsv"), "plugin.one\tcontent.one\tmodules/content.one.jar\t$jar")

    assertThatThrownBy {
      collectPrepackedPluginContentJars(metadata, emptyList(), tempDir.resolve("missing"))
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("missing placements [plugin.one/content.one]")

    val unknown = writeLines(
      tempDir.resolve("unknown.tsv"),
      "plugin.one\tcontent.one\tplugins/one/lib/modules/content.one.jar",
      "plugin.two\tcontent.two\tplugins/two/lib/modules/content.two.jar",
    )
    assertThatThrownBy {
      collectPrepackedPluginContentJars(metadata, listOf(unknown), tempDir.resolve("unknown"))
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("unknown placements [plugin.two/content.two]")
  }

  @Test
  fun `rejects duplicate relations and placements`(@TempDir tempDir: Path) {
    val jar = tempDir.resolve("content.jar")
    Files.writeString(jar, "content")
    val duplicateJars = writeLines(
      tempDir.resolve("duplicate-jars.tsv"),
      "plugin.one\tcontent.one\tmodules/content.one.jar\t$jar",
      "plugin.one\tcontent.one\tmodules/content.one.jar\t$jar",
    )
    assertThatThrownBy {
      collectPrepackedPluginContentJars(duplicateJars, emptyList(), tempDir.resolve("duplicate-jars"))
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("duplicate plugin jar relation")

    val metadata = writeLines(tempDir.resolve("jars.tsv"), "plugin.one\tcontent.one\tmodules/content.one.jar\t$jar")
    val first = writeLines(tempDir.resolve("first.tsv"), "plugin.one\tcontent.one\tplugins/one/lib/modules/content.one.jar")
    val second = writeLines(tempDir.resolve("second.tsv"), "plugin.one\tcontent.one\tplugins/one/lib/modules/content.one.jar")
    assertThatThrownBy {
      collectPrepackedPluginContentJars(metadata, listOf(first, second), tempDir.resolve("duplicate-placement"))
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("duplicate placement")
  }

  @Test
  fun `rejects path escapes and placement mismatches`(@TempDir tempDir: Path) {
    val jar = tempDir.resolve("content.jar")
    Files.writeString(jar, "content")
    val escapingMetadata = writeLines(tempDir.resolve("escaping-jars.tsv"), "plugin.one\tcontent.one\t../content.jar\t$jar")
    assertThatThrownBy {
      collectPrepackedPluginContentJars(escapingMetadata, emptyList(), tempDir.resolve("escaping-metadata"))
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("escapes plugin lib")

    val metadata = writeLines(tempDir.resolve("jars.tsv"), "plugin.one\tcontent.one\tmodules/content.one.jar\t$jar")
    val escapingPlacement = writeLines(tempDir.resolve("escaping-placement.tsv"), "plugin.one\tcontent.one\t../outside.jar")
    assertThatThrownBy {
      collectPrepackedPluginContentJars(metadata, listOf(escapingPlacement), tempDir.resolve("escaping-placement"))
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("escapes the distribution")

    val mismatched = writeLines(tempDir.resolve("mismatched.tsv"), "plugin.one\tcontent.one\tplugins/one/lib/modules/other.jar")
    assertThatThrownBy {
      collectPrepackedPluginContentJars(metadata, listOf(mismatched), tempDir.resolve("mismatched"))
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("expected plugins/<directory>/lib/modules/content.one.jar")
  }

  @Test
  fun `rejects two relations claiming the same destination`(@TempDir tempDir: Path) {
    val firstJar = tempDir.resolve("first.jar")
    val secondJar = tempDir.resolve("second.jar")
    Files.writeString(firstJar, "first")
    Files.writeString(secondJar, "second")
    val metadata = writeLines(
      tempDir.resolve("jars.tsv"),
      "plugin.one\tcontent.one\tmodules/content.jar\t$firstJar",
      "plugin.two\tcontent.two\tmodules/content.jar\t$secondJar",
    )
    val placements = writeLines(
      tempDir.resolve("placements.tsv"),
      "plugin.one\tcontent.one\tplugins/shared/lib/modules/content.jar",
      "plugin.two\tcontent.two\tplugins/shared/lib/modules/content.jar",
    )

    assertThatThrownBy {
      collectPrepackedPluginContentJars(metadata, listOf(placements), tempDir.resolve("output"))
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("both claim plugins/shared/lib/modules/content.jar")
  }

  private fun writeLines(file: Path, vararg lines: String): Path {
    Files.writeString(file, lines.joinToString(separator = "\n", postfix = "\n"))
    return file
  }
}
