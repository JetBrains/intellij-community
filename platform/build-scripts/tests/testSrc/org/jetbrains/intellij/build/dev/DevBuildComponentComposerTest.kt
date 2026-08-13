// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

internal class DevBuildComponentComposerTest {
  @Test
  fun `copy fallback preserves file attributes`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("source")
    val sourceFile = source.resolve("bin/tool")
    Files.createDirectories(sourceFile.parent)
    Files.writeString(sourceFile, "tool")
    Files.setLastModifiedTime(sourceFile, FileTime.fromMillis(1_234_000))
    val expectedLastModifiedTime = Files.getLastModifiedTime(sourceFile)
    val supportsPosix = Files.getFileStore(sourceFile).supportsFileAttributeView(PosixFileAttributeView::class.java)
    val expectedPermissions = if (supportsPosix) {
      setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
        .also { Files.setPosixFilePermissions(sourceFile, it) }
    }
    else null
    val target = tempDir.resolve("target")

    mergeDevBuildComponent(source = source, target = target) { _, _ -> throw IOException("force copy fallback") }

    val copiedFile = target.resolve("bin/tool")
    assertThat(Files.readString(copiedFile)).isEqualTo("tool")
    assertThat(Files.getLastModifiedTime(copiedFile)).isEqualTo(expectedLastModifiedTime)
    expectedPermissions?.let { assertThat(Files.getPosixFilePermissions(copiedFile)).isEqualTo(it) }
  }

  @Test
  fun `component merge rejects duplicate files`(@TempDir tempDir: Path) {
    val platform = tempDir.resolve("platform")
    val plugins = tempDir.resolve("plugins")
    Files.createDirectories(platform)
    Files.createDirectories(plugins)
    Files.writeString(platform.resolve("shared.txt"), "platform")
    Files.writeString(plugins.resolve("shared.txt"), "plugins")
    val target = tempDir.resolve("target")

    mergeDevBuildComponent(platform, target)

    assertThatThrownBy { mergeDevBuildComponent(plugins, target) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("both provide 'shared.txt'")
  }
}
