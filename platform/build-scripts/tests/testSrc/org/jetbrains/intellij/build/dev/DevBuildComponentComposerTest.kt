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
  fun `component merge copies owned bytes`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("source")
    Files.createDirectories(source)
    val sourceFile = source.resolve("file.txt")
    Files.writeString(sourceFile, "before")
    val target = tempDir.resolve("target")

    mergeDevBuildComponent(source, target)
    Files.writeString(sourceFile, "after")

    assertThat(Files.readString(target.resolve("file.txt"))).isEqualTo("before")
  }

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

  @Test
  fun `composer accepts ordered platform layers and plugins`(@TempDir tempDir: Path) {
    val platformLib = component(tempDir, "platform-lib", "lib/platform.jar")
    val platformResources = component(tempDir, "platform-resources", "bin/idea.properties")
    val plugins = component(tempDir, "plugins", "plugins/sample/lib/sample.jar")
    val components = listOf(
      DevBuildComponent(
        root = platformLib,
        manifest = manifest(kind = "platform_lib", coreClassPath = listOf("lib/platform.jar")),
      ),
      DevBuildComponent(
        root = platformResources,
        manifest = manifest(kind = "platform_resources"),
      ),
      DevBuildComponent(
        root = plugins,
        manifest = manifest(
          kind = "plugins",
          coreClassPath = listOf("plugins/sample/lib/sample.jar"),
          additionalModules = listOf("intellij.sample", "intellij.shared"),
        ),
      ),
      DevBuildComponent(
        root = component(tempDir, "extra-plugins", "plugins/extra/lib/extra.jar"),
        manifest = manifest(
          kind = "plugins_extra",
          coreClassPath = listOf("plugins/extra/lib/extra.jar"),
          additionalModules = listOf("intellij.shared", "intellij.extra"),
        ),
      ),
    )

    val result = composeDevBuildComponents(components, tempDir.resolve("target"))

    assertThat(result.coreClassPath).containsExactly(
      "lib/platform.jar",
      "plugins/sample/lib/sample.jar",
      "plugins/extra/lib/extra.jar",
    )
    assertThat(result.additionalModules).containsExactly("intellij.sample", "intellij.shared", "intellij.extra")
    assertThat(Files.exists(tempDir.resolve("target/bin/idea.properties"))).isTrue()
    assertThat(result.fingerprint).isEqualTo(computeIdeFingerprintFromComponents(components.map { it.manifest }))
  }

  @Test
  fun `composer rejects a component for another product before writing output`(@TempDir tempDir: Path) {
    val first = DevBuildComponent(component(tempDir, "first", "first.txt"), manifest(kind = "platform_lib"))
    val second = DevBuildComponent(
      component(tempDir, "second", "second.txt"),
      manifest(kind = "platform_resources", platformPrefix = "Rider"),
    )
    val target = tempDir.resolve("target")

    assertThatThrownBy { composeDevBuildComponents(listOf(first, second), target) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("different products")
    assertThat(Files.exists(target)).isFalse()
  }

  private fun component(tempDir: Path, name: String, relativeFile: String): Path {
    val root = tempDir.resolve(name)
    val file = root.resolve(relativeFile)
    Files.createDirectories(file.parent)
    Files.writeString(file, name)
    return root
  }

  private fun manifest(
    kind: String,
    platformPrefix: String = "idea",
    coreClassPath: List<String> = emptyList(),
    additionalModules: List<String> = emptyList(),
  ): DevBuildComponentManifest {
    return DevBuildComponentManifest(
      kind = kind,
      platformPrefix = platformPrefix,
      os = "linux",
      arch = "x64",
      additionalModules = additionalModules,
      mainClass = "com.intellij.idea.Main",
      coreClassPath = coreClassPath,
      entries = emptyList(),
    )
  }
}
