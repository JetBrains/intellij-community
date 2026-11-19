// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.plugins

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.writeText

class IjPluginFileAttributesTest {
  @Test
  fun `the reader returns the mode of each line`(@TempDir dir: Path) {
    val file = dir.resolve(IJ_PLUGIN_FILE_ATTRIBUTES_FILE_NAME)
    file.writeText("bin/launcher.sh:0755\nlib/a:b.txt:0600\n")
    assertThat(readIjPluginFileModes(file)).containsExactlyEntriesOf(mapOf("bin/launcher.sh" to 0b111_101_101, "lib/a:b.txt" to 0b110_000_000))
  }

  @Test
  fun `the reader returns no modes for an empty file`(@TempDir dir: Path) {
    val file = dir.resolve(IJ_PLUGIN_FILE_ATTRIBUTES_FILE_NAME)
    file.writeText("")
    assertThat(readIjPluginFileModes(file)).isEmpty()
  }

  @Test
  fun `the reader refuses a bad line`(@TempDir dir: Path) {
    for (content in listOf("bin/tool", "bin/tool:rwx", "bin/tool:4755", "bin/tool:0755\nbin/tool:0755")) {
      val file = dir.resolve(IJ_PLUGIN_FILE_ATTRIBUTES_FILE_NAME)
      file.writeText(content)
      assertThatThrownBy { readIjPluginFileModes(file) }.describedAs(content).isInstanceOf(IllegalArgumentException::class.java)
    }
  }

  @Test
  fun `the copy of a plugin gets the stated modes and 0644 for each other file`(@TempDir dir: Path) {
    assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews())
    val pluginDirectory = dir.resolve("plugin")
    val tool = pluginDirectory.resolve("bin/tool")
    val jar = pluginDirectory.resolve("lib/plugin.jar")
    for (file in listOf(tool, jar)) {
      file.parent.createDirectories()
      file.writeText(file.fileName.toString())
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r-xr-xr-x"))
    }
    val link = pluginDirectory.resolve("bin/link").createSymbolicLinkPointingTo(Path.of("tool"))

    applyIjPluginFileModes(pluginDirectory, mapOf("bin/tool" to 0b111_101_101))

    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(tool))).isEqualTo("rwxr-xr-x")
    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(jar))).isEqualTo("rw-r--r--")
    assertThat(Files.isSymbolicLink(link)).isTrue()
  }

  @Test
  fun `a mode for a file that the copy does not have is refused`(@TempDir dir: Path) {
    assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews())
    val pluginDirectory = dir.resolve("plugin")
    pluginDirectory.resolve("lib").createDirectories()
    assertThatThrownBy { applyIjPluginFileModes(pluginDirectory, mapOf("bin/tool" to 0b111_101_101)) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("bin/tool")
  }

}
