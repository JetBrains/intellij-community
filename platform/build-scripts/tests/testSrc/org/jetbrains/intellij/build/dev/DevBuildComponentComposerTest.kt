// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
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
  fun `component manifest fingerprints packaged bytes rather than stale entry metadata`(@TempDir tempDir: Path) {
    val componentRoot = tempDir.resolve("component")
    val packagedFile = componentRoot.resolve("plugins/air/lib/air.jar")
    Files.createDirectories(packagedFile.parent)
    Files.writeString(packagedFile, "before")
    val entry = CustomAssetEntry(path = packagedFile, hash = 1)
    val beforeManifest = tempDir.resolve("before.json")
    writeManifest(beforeManifest, componentRoot, entry)

    Files.writeString(packagedFile, "after!")
    val afterManifest = tempDir.resolve("after.json")
    writeManifest(afterManifest, componentRoot, entry)

    val before = readDevBuildComponentManifest(beforeManifest)
    val after = readDevBuildComponentManifest(afterManifest)
    assertThat(before.entries.single().hash).isNotEqualTo(1)
    assertThat(after.entries.single().hash).isNotEqualTo(before.entries.single().hash)
    assertThat(computeIdeFingerprintFromComponents(listOf(after)))
      .isNotEqualTo(computeIdeFingerprintFromComponents(listOf(before)))
  }

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
  fun `component merge rejects a file two components disagree about`(@TempDir tempDir: Path) {
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
      .hasMessageContaining("provide different content for 'shared.txt'")
  }

  @Test
  fun `component merge rejects same-size files with different content`(@TempDir tempDir: Path) {
    val first = tempDir.resolve("first")
    val second = tempDir.resolve("second")
    Files.createDirectories(first)
    Files.createDirectories(second)
    Files.writeString(first.resolve("native.so"), "aaaa")
    Files.writeString(second.resolve("native.so"), "bbbb")
    val target = tempDir.resolve("target")

    mergeDevBuildComponent(first, target)

    assertThatThrownBy { mergeDevBuildComponent(second, target) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("the same size, 4 bytes, but different content")
  }

  @Test
  fun `component merge accepts a file two components produced identically`(@TempDir tempDir: Path) {
    // What every fragment that builds the platform layout registers - `lib/ijent/…` and the presigned natives beside it.
    // None of them owns it, and rejecting it is how those files went missing from a split distribution.
    val first = tempDir.resolve("first")
    val second = tempDir.resolve("second")
    Files.createDirectories(first.resolve("lib/ijent"))
    Files.createDirectories(second.resolve("lib/ijent"))
    Files.writeString(first.resolve("lib/ijent/binary"), "same bytes")
    Files.writeString(second.resolve("lib/ijent/binary"), "same bytes")
    val target = tempDir.resolve("target")

    mergeDevBuildComponent(first, target)
    mergeDevBuildComponent(second, target)

    assertThat(Files.readString(target.resolve("lib/ijent/binary"))).isEqualTo("same bytes")
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

    // Ordered here rather than left in component order: each component sorted only the share it packed.
    assertThat(result.coreClassPath).containsExactly(
      "lib/platform.jar",
      "plugins/extra/lib/extra.jar",
      "plugins/sample/lib/sample.jar",
    )
    assertThat(result.additionalModules).containsExactly("intellij.sample", "intellij.shared", "intellij.extra")
    assertThat(Files.exists(tempDir.resolve("target/bin/idea.properties"))).isTrue()
    assertThat(result.fingerprint).isEqualTo(computeIdeFingerprintFromComponents(components.map { it.manifest }))
  }

  @Test
  fun `composer puts the leading core classpath jars first`(@TempDir tempDir: Path) {
    val component = DevBuildComponent(
      root = component(tempDir, "platform", "lib/util.jar"),
      manifest = manifest(
        kind = "platform_core",
        coreClassPath = listOf("lib/app-backend.jar", "lib/util.jar", "lib/platform-loader.jar", "lib/util-8.jar"),
      ),
    )

    val result = composeDevBuildComponents(listOf(component), tempDir.resolve("target"))

    assertThat(result.coreClassPath).containsExactly(
      "lib/platform-loader.jar",
      "lib/util-8.jar",
      "lib/util.jar",
      "lib/app-backend.jar",
    )
  }

  @Test
  fun `composer builds plugin-classpath from the prefix and every component's records`(@TempDir tempDir: Path) {
    val prefix = tempDir.resolve("prefix.bin")
    Files.write(prefix, byteArrayOf(2, 1, 0, 0, 0, 0))
    val air = DevBuildComponent(
      root = component(tempDir, "air", "plugins/air-plugin/lib/air.jar"),
      manifest = manifest(kind = "plugins_air", pluginCount = 1),
      pluginClasspathPart = tempDir.resolve("air.part").also { Files.write(it, byteArrayOf(10)) },
    )
    val remaining = DevBuildComponent(
      root = component(tempDir, "remaining", "plugins/git/lib/git.jar"),
      manifest = manifest(kind = "plugins_remaining", pluginCount = 2),
      pluginClasspathPart = tempDir.resolve("remaining.part").also { Files.write(it, byteArrayOf(20, 21)) },
    )
    val target = tempDir.resolve("target")

    composeDevBuildComponents(listOf(air, remaining), target, pluginClasspathPrefix = prefix)

    // prefix, then the summed plugin count as a big-endian short, then the records in component order
    assertThat(Files.readAllBytes(target.resolve("plugins/plugin-classpath.txt")))
      .containsExactly(2, 1, 0, 0, 0, 0, 0, 3, 10, 20, 21)
  }

  @Test
  fun `composer rejects plugin records without a prefix`(@TempDir tempDir: Path) {
    val air = DevBuildComponent(
      root = component(tempDir, "air", "plugins/air-plugin/lib/air.jar"),
      manifest = manifest(kind = "plugins_air", pluginCount = 1),
      pluginClasspathPart = tempDir.resolve("air.part").also { Files.write(it, byteArrayOf(10)) },
    )

    assertThatThrownBy { composeDevBuildComponents(listOf(air), tempDir.resolve("target")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("plugin-classpath prefix is required")
  }

  @Test
  fun `composer rejects a composition that is missing a wired fragment`(@TempDir tempDir: Path) {
    val platform = DevBuildComponent(component(tempDir, "platform", "lib/platform.jar"), manifest(kind = "platform_core"))

    assertThatThrownBy {
      composeDevBuildComponents(
        components = listOf(platform),
        target = tempDir.resolve("target"),
        expectedFragments = listOf("platform_core", "platform_resources"),
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("platform_resources")
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

  private fun writeManifest(file: Path, componentRoot: Path, entry: CustomAssetEntry) {
    writeDevBuildComponentManifest(
      file = file,
      kind = "plugins_air",
      platformPrefix = "idea",
      os = OsFamily.MACOS,
      arch = JvmArchitecture.aarch64,
      additionalModules = emptyList(),
      mainClass = "com.intellij.idea.Main",
      coreClassPath = emptyList(),
      pluginCount = 1,
      entries = sequenceOf(entry),
      componentRoot = componentRoot,
      projectDir = componentRoot,
    )
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
    pluginCount: Int = 0,
  ): DevBuildComponentManifest {
    return DevBuildComponentManifest(
      kind = kind,
      platformPrefix = platformPrefix,
      os = "linux",
      arch = "x64",
      additionalModules = additionalModules,
      mainClass = "com.intellij.idea.Main",
      coreClassPath = coreClassPath,
      pluginCount = pluginCount,
      entries = emptyList(),
    )
  }
}
