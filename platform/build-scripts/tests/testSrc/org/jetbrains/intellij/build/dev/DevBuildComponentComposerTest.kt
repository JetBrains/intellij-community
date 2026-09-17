// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
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
  fun `component manifest fingerprints packaged bytes`(@TempDir tempDir: Path) {
    val componentRoot = tempDir.resolve("component")
    val packagedFile = componentRoot.resolve("plugins/air/lib/air.jar")
    Files.createDirectories(packagedFile.parent)
    Files.writeString(packagedFile, "before")
    val beforeManifest = tempDir.resolve("before.json")
    writeManifest(beforeManifest, componentRoot)

    Files.writeString(packagedFile, "after!")
    val afterManifest = tempDir.resolve("after.json")
    writeManifest(afterManifest, componentRoot)

    val before = readDevBuildComponentManifest(beforeManifest)
    val after = readDevBuildComponentManifest(afterManifest)
    assertThat(after.entries.single().hash).isNotEqualTo(before.entries.single().hash)
    assertThat(computeIdeFingerprintFromComponents(listOf(after)))
      .isNotEqualTo(computeIdeFingerprintFromComponents(listOf(before)))
  }

  @Test
  fun `component manifest fingerprints the executable bit`(@TempDir tempDir: Path) {
    val componentRoot = tempDir.resolve("component")
    val executable = componentRoot.resolve("bin/tool")
    Files.createDirectories(executable.parent)
    Files.writeString(executable, "same bytes")
    if (!Files.getFileStore(executable).supportsFileAttributeView(PosixFileAttributeView::class.java)) return

    Files.setPosixFilePermissions(executable, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    val regularManifestFile = tempDir.resolve("regular.json")
    writeManifest(regularManifestFile, componentRoot)
    val regularManifest = readDevBuildComponentManifest(regularManifestFile)

    Files.setPosixFilePermissions(
      executable,
      setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
    )
    val executableManifestFile = tempDir.resolve("executable.json")
    writeManifest(executableManifestFile, componentRoot)
    val executableManifest = readDevBuildComponentManifest(executableManifestFile)

    assertThat(regularManifest.entries.single().executable).isFalse()
    assertThat(executableManifest.entries.single().executable).isTrue()
    assertThat(computeIdeFingerprintFromComponents(listOf(executableManifest)))
      .isNotEqualTo(computeIdeFingerprintFromComponents(listOf(regularManifest)))
  }

  @Test
  fun `component manifest inventories late DistFiles and genuine relative symlinks`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val componentRoot = tempDir.resolve("component")
    val ijent = componentRoot.resolve("lib/ijent/ijent-x86_64-unknown-linux-musl-release")
    Files.createDirectories(ijent.parent)
    Files.writeString(ijent, "ijent")
    val frameworkVersions = componentRoot.resolve("plugins/jcef/Framework.framework/Versions")
    Files.createDirectories(frameworkVersions.resolve("A"))
    val current = frameworkVersions.resolve("Current")
    Files.createSymbolicLink(current, Path.of("A"))
    val manifestFile = tempDir.resolve("component.json")

    writeDevBuildComponentManifest(
      file = manifestFile,
      kind = "platform_resources",
      platformPrefix = "idea",
      os = OsFamily.LINUX,
      arch = JvmArchitecture.x64,
      additionalModules = emptyList(),
      mainClass = "com.intellij.idea.Main",
      coreClassPath = emptyList(),
      pluginCount = 0,
      componentRoot = componentRoot,
    )

    val manifest = readDevBuildComponentManifest(manifestFile)
    assertThat(manifest.version).isEqualTo(9)
    assertThat(manifest.entries).anySatisfy { entry ->
      assertThat(entry.relativePath).isEqualTo("lib/ijent/ijent-x86_64-unknown-linux-musl-release")
      assertThat(entry.type).isEqualTo("component-file")
    }
    assertThat(manifest.entries).anySatisfy { entry ->
      assertThat(entry.relativePath).isEqualTo("plugins/jcef/Framework.framework/Versions/Current")
      assertThat(entry.type).isEqualTo("symlink")
      assertThat(entry.symlinkTarget).isEqualTo("A")
    }
  }

  @Test
  fun `component manifest inventories an external staging symlink as owned bytes`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val componentRoot = tempDir.resolve("component")
    Files.createDirectories(componentRoot)
    val outside = tempDir.resolve("outside")
    Files.writeString(outside, "staged bytes")
    Files.createSymbolicLink(componentRoot.resolve("staged"), Path.of("../outside"))
    val manifestFile = tempDir.resolve("component.json")

    writeDevBuildComponentManifest(
      file = manifestFile,
      kind = "platform_resources",
      platformPrefix = "idea",
      os = OsFamily.MACOS,
      arch = JvmArchitecture.aarch64,
      additionalModules = emptyList(),
      mainClass = "com.intellij.idea.Main",
      coreClassPath = emptyList(),
      pluginCount = 0,
      componentRoot = componentRoot,
    )

    val entry = readDevBuildComponentManifest(manifestFile).entries.single()
    assertThat(entry.relativePath).isEqualTo("staged")
    assertThat(entry.type).isEqualTo("component-file")
    assertThat(entry.symlinkTarget).isNull()
    assertThat(Files.isSymbolicLink(componentRoot.resolve("staged"))).isFalse()
    assertThat(Files.readString(outside)).isEqualTo("staged bytes")
    Files.writeString(outside, "changed source")
    assertThat(Files.readString(componentRoot.resolve("staged"))).isEqualTo("staged bytes")
  }

  @Test
  fun `component materializes absolute transport links without changing source modes`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val componentRoot = tempDir.resolve("component")
    Files.createDirectories(componentRoot)
    val outside = tempDir.resolve("script.py")
    Files.writeString(outside, "script bytes")
    val permissions = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ)
    val posix = Files.getFileStore(outside).supportsFileAttributeView(PosixFileAttributeView::class.java)
    if (posix) Files.setPosixFilePermissions(outside, permissions)
    val staged = componentRoot.resolve("script.py")
    Files.createSymbolicLink(staged, outside.toAbsolutePath())
    val genuineLink = componentRoot.resolve("current.py")
    Files.createSymbolicLink(genuineLink, Path.of("script.py"))

    val manifestFile = tempDir.resolve("component.json")
    writeManifest(manifestFile, componentRoot)

    assertThat(Files.isSymbolicLink(staged)).isFalse()
    assertThat(Files.readString(staged)).isEqualTo("script bytes")
    assertThat(Files.readSymbolicLink(genuineLink).toString()).isEqualTo("script.py")
    if (posix) {
      assertThat(Files.getPosixFilePermissions(outside)).isEqualTo(permissions)
      assertThat(Files.getPosixFilePermissions(staged)).isEqualTo(permissions)
    }
    Files.delete(outside)
    val manifest = readDevBuildComponentManifest(manifestFile)
    val repeatedManifestFile = tempDir.resolve("repeated.json")
    writeManifest(repeatedManifestFile, componentRoot)
    assertThat(readDevBuildComponentManifest(repeatedManifestFile).entries).isEqualTo(manifest.entries)
    assertThat(manifest.entries.single { it.relativePath == "current.py" }.symlinkTarget).isEqualTo("script.py")
  }

  @Test
  fun `component merge owns bytes independently of its source`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("source")
    Files.createDirectories(source.resolve("lib"))
    val sourceFile = source.resolve("lib/file.txt")
    Files.writeString(sourceFile, "before")
    val target = tempDir.resolve("target")

    mergeDevBuildComponent(source, target)
    Files.writeString(sourceFile, "after")

    val copied = target.resolve("lib/file.txt")
    assertThat(Files.readString(copied)).isEqualTo("before")
    assertThat(Files.isSymbolicLink(copied)).isFalse()
  }

  private fun supportsSymbolicLinks(tempDir: Path): Boolean {
    val probe = tempDir.resolve("symlink-probe")
    return try {
      Files.createSymbolicLink(probe, tempDir)
      Files.delete(probe)
      true
    }
    catch (_: IOException) {
      false
    }
    catch (_: UnsupportedOperationException) {
      false
    }
  }

  @Test
  fun `component merge preserves file attributes`(@TempDir tempDir: Path) {
    val sourceFile = tempDir.resolve("source/bin/tool")
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

    mergeDevBuildComponent(source = tempDir.resolve("source"), target = target)

    val copiedFile = target.resolve("bin/tool")
    assertThat(Files.readString(copiedFile)).isEqualTo("tool")
    assertThat(Files.getLastModifiedTime(copiedFile)).isEqualTo(expectedLastModifiedTime)
    expectedPermissions?.let { assertThat(Files.getPosixFilePermissions(copiedFile)).isEqualTo(it) }
  }

  @Test
  fun `component merge rejects duplicate paths even when bytes match`(@TempDir tempDir: Path) {
    val first = tempDir.resolve("first")
    val second = tempDir.resolve("second")
    Files.createDirectories(first.resolve("lib/ijent"))
    Files.createDirectories(second.resolve("lib/ijent"))
    Files.writeString(first.resolve("lib/ijent/binary"), "same bytes")
    Files.writeString(second.resolve("lib/ijent/binary"), "same bytes")
    val target = tempDir.resolve("target")

    mergeDevBuildComponent(first, target)

    assertThatThrownBy { mergeDevBuildComponent(second, target) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("both provide 'lib/ijent/binary'")
  }

  @Test
  fun `component merge follows an undeclared sandbox staging symlink`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val stagedBytes = tempDir.resolve("bazel-out/fragment/file.jar")
    Files.createDirectories(stagedBytes.parent)
    Files.writeString(stagedBytes, "jar bytes")
    val source = tempDir.resolve("sandbox/component")
    Files.createDirectories(source.resolve("lib"))
    Files.createSymbolicLink(source.resolve("lib/file.jar"), stagedBytes)
    val target = tempDir.resolve("target")

    mergeDevBuildComponent(source, target)
    Files.delete(stagedBytes)

    assertThat(Files.isSymbolicLink(target.resolve("lib/file.jar"))).isFalse()
    assertThat(Files.readString(target.resolve("lib/file.jar"))).isEqualTo("jar bytes")
  }

  @Test
  fun `component merge accepts an empty tree staged as a symbolic link`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val tree = tempDir.resolve("bazel-out/fragment.home")
    Files.createDirectories(tree)
    val source = tempDir.resolve("sandbox/fragment.home")
    Files.createDirectories(source.parent)
    Files.createSymbolicLink(source, tree)
    val target = tempDir.resolve("target")
    Files.createDirectories(target)

    mergeDevBuildComponent(source, target)

    assertThat(Files.isDirectory(target)).isTrue()
    assertThat(Files.list(target).use { it.count() }).isEqualTo(0)
  }

  @Test
  fun `composer recreates only a manifest-declared JCEF framework symlink`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val source = tempDir.resolve("jcef-component")
    val versions = source.resolve("plugins/jcef/jcef/Frameworks/Chromium Embedded Framework.framework/Versions")
    Files.createDirectories(versions.resolve("A"))
    Files.writeString(versions.resolve("A/Chromium Embedded Framework"), "framework")
    Files.createSymbolicLink(versions.resolve("Current"), Path.of("A"))
    val relativeLink = "plugins/jcef/jcef/Frameworks/Chromium Embedded Framework.framework/Versions/Current"
    val manifest = manifest(
      kind = "plugins_jcef",
      entries = listOf(
        DevBuildComponentEntry(relativePath = relativeLink, type = "symlink", hash = 1, symlinkTarget = "A")
      ),
    )
    val target = tempDir.resolve("target")

    composeDevBuildComponents(listOf(DevBuildComponent(source, manifest)), target)

    assertThat(Files.readSymbolicLink(target.resolve(relativeLink))).isEqualTo(Path.of("A"))
    assertThat(Files.readString(target.resolve("$relativeLink/Chromium Embedded Framework"))).isEqualTo("framework")
  }

  @Test
  fun `composer recreates a manifest directory symlink materialized by Bazel`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val source = tempDir.resolve("jcef-component")
    val frameworks = source.resolve("plugins/jcef/jcef/Frameworks")
    val versionedFramework = frameworks.resolve("Chromium Embedded Framework.framework/Versions/A")
    Files.createDirectories(versionedFramework)
    Files.writeString(versionedFramework.resolve("Chromium Embedded Framework"), "framework")
    val stagedLinkDirectory = frameworks.resolve("Chromium Embedded Framework.framework")
    val relativeLink = "plugins/jcef/jcef/Frameworks/Chromium Embedded Framework.framework"
    val manifest = manifest(
      kind = "plugins_jcef",
      entries = listOf(
        DevBuildComponentEntry(relativePath = relativeLink, type = "symlink", hash = 1, symlinkTarget = "jcef.framework")
      ),
    )
    val target = tempDir.resolve("target")

    composeDevBuildComponents(listOf(DevBuildComponent(source, manifest)), target)

    assertThat(Files.readSymbolicLink(target.resolve(relativeLink))).isEqualTo(Path.of("jcef.framework"))
    assertThat(Files.exists(target.resolve(relativeLink).resolve("Versions"))).isFalse()
    assertThat(Files.exists(stagedLinkDirectory.resolve("Versions/A/Chromium Embedded Framework"))).isTrue()
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

    val result = composeDevBuildComponents(
      components = components,
      target = tempDir.resolve("target"),
      additionalModules = listOf("intellij.sample", "intellij.shared", "intellij.extra"),
    )

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
    Files.write(prefix, byteArrayOf(3, 0, 0, 0, 0))
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
      .containsExactly(3, 0, 0, 0, 0, 0, 3, 10, 20, 21)
  }

  @Test
  fun `component fingerprint covers generated launch and classpath data`(@TempDir tempDir: Path) {
    val base = manifest(kind = "platform_core", coreClassPath = listOf("lib/platform.jar"))
    val changedCoreClasspath = base.copy(coreClassPath = listOf("lib/renamed-platform.jar"))
    val changedMainClass = base.copy(mainClass = "com.intellij.idea.OtherMain")
    val pluginClasspath = tempDir.resolve("plugin-classpath.txt")
    Files.write(pluginClasspath, byteArrayOf(1, 2, 3))

    val fingerprint = computeIdeFingerprintFromComponents(listOf(base), pluginClasspathFile = pluginClasspath)

    assertThat(computeIdeFingerprintFromComponents(listOf(changedCoreClasspath), pluginClasspathFile = pluginClasspath))
      .isNotEqualTo(fingerprint)
    assertThat(computeIdeFingerprintFromComponents(listOf(changedMainClass), pluginClasspathFile = pluginClasspath))
      .isNotEqualTo(fingerprint)
    Files.write(pluginClasspath, byteArrayOf(1, 2, 4))
    assertThat(computeIdeFingerprintFromComponents(listOf(base), pluginClasspathFile = pluginClasspath))
      .isNotEqualTo(fingerprint)
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
  fun `composer rejects a positive plugin count without records before writing output`(@TempDir tempDir: Path) {
    val plugins = DevBuildComponent(
      root = component(tempDir, "air", "plugins/air-plugin/lib/air.jar"),
      manifest = manifest(kind = "plugins_air", pluginCount = 1),
    )
    val target = tempDir.resolve("target")

    assertThatThrownBy { composeDevBuildComponents(listOf(plugins), target) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("plugins_air (1)")
    assertThat(Files.exists(target)).isFalse()
  }

  @Test
  fun `composer takes the main class from a component that declares one`(@TempDir tempDir: Path) {
    // A component that only carries packed jars declares no launch metadata: it knows its product and target platform,
    // but the main class follows from `ProductProperties`, which such a producer deliberately never evaluates.
    val jars = DevBuildComponent(component(tempDir, "jars", "lib/packed.jar"), manifest(kind = "platform_jars", mainClass = null))
    val core = DevBuildComponent(component(tempDir, "core", "lib/platform.jar"), manifest(kind = "platform_core"))

    val composed = composeDevBuildComponents(listOf(jars, core), tempDir.resolve("target"))

    assertThat(composed.mainClass).isEqualTo("com.intellij.idea.Main")
  }

  @Test
  fun `composer rejects a composition where no component declares a main class`(@TempDir tempDir: Path) {
    val jars = DevBuildComponent(component(tempDir, "jars", "lib/packed.jar"), manifest(kind = "platform_jars", mainClass = null))
    val target = tempDir.resolve("target")

    assertThatThrownBy { composeDevBuildComponents(listOf(jars), target) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("platform_jars")
    assertThat(Files.exists(target)).isFalse()
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
  fun `composer rejects an unexpected wired fragment`(@TempDir tempDir: Path) {
    val platform = DevBuildComponent(component(tempDir, "platform", "lib/platform.jar"), manifest(kind = "platform_core"))
    val stale = DevBuildComponent(component(tempDir, "stale", "plugins/stale/plugin.jar"), manifest(kind = "plugins_stale"))

    assertThatThrownBy {
      composeDevBuildComponents(
        components = listOf(platform, stale),
        target = tempDir.resolve("target"),
        expectedFragments = listOf("platform_core"),
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("unexpected: plugins_stale")
  }

  @Test
  fun `composer rejects duplicate fragment kinds before writing output`(@TempDir tempDir: Path) {
    val first = DevBuildComponent(component(tempDir, "first", "lib/first.jar"), manifest(kind = "platform_core"))
    val second = DevBuildComponent(component(tempDir, "second", "lib/second.jar"), manifest(kind = "platform_core"))
    val target = tempDir.resolve("target")

    assertThatThrownBy { composeDevBuildComponents(listOf(first, second), target) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("platform_core")
    assertThat(Files.exists(target)).isFalse()
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

  @Test
  fun `composer takes the platform from the first component that names one`(@TempDir tempDir: Path) {
    // A component Bazel packs from plain jars knows no target platform, so its manifest has an empty os and arch.
    val neutral = DevBuildComponent(
      root = component(tempDir, "json", "plugins/json/lib/json.jar"),
      manifest = manifest(kind = "plugins_json", os = "", arch = "", mainClass = null),
    )
    val linux = DevBuildComponent(component(tempDir, "platform", "lib/platform.jar"), manifest(kind = "platform_core"))
    val mac = DevBuildComponent(component(tempDir, "platform-mac", "lib/platform.jar"), manifest(kind = "platform_core", os = "mac", arch = "aarch64"))

    composeDevBuildComponents(listOf(neutral, linux), tempDir.resolve("target"))

    assertThat(Files.exists(tempDir.resolve("target/plugins/json/lib/json.jar"))).isTrue()
    // the launch metadata hashes the distribution's platform, not the neutral component's empty one
    assertThat(computeIdeFingerprintFromComponents(listOf(neutral.manifest, linux.manifest)))
      .isNotEqualTo(computeIdeFingerprintFromComponents(listOf(neutral.manifest, mac.manifest)))
  }

  @Test
  fun `composer accepts a composition of neutral components only`(@TempDir tempDir: Path) {
    val neutral = DevBuildComponent(
      root = component(tempDir, "json", "plugins/json/lib/json.jar"),
      manifest = manifest(kind = "plugins_json", os = "", arch = ""),
    )

    val composed = composeDevBuildComponents(listOf(neutral), tempDir.resolve("target"))

    assertThat(composed.platformPrefix).isEqualTo("idea")
    assertThat(Files.exists(tempDir.resolve("target/plugins/json/lib/json.jar"))).isTrue()
  }

  @Test
  fun `composer rejects two components for different platforms around a neutral one`(@TempDir tempDir: Path) {
    val neutral = DevBuildComponent(
      root = component(tempDir, "json", "plugins/json/lib/json.jar"),
      manifest = manifest(kind = "plugins_json", os = "", arch = "", mainClass = null),
    )
    val linux = DevBuildComponent(component(tempDir, "platform", "lib/platform.jar"), manifest(kind = "platform_core"))
    val mac = DevBuildComponent(component(tempDir, "resources", "bin/idea.properties"), manifest(kind = "platform_resources", os = "mac", arch = "aarch64"))
    val target = tempDir.resolve("target")

    assertThatThrownBy { composeDevBuildComponents(listOf(neutral, linux, mac), target) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("different target platforms: 'linux/x64' and 'mac/aarch64'")
    assertThat(Files.exists(target)).isFalse()
  }

  @Test
  fun `composition spec decodes its versioned contract`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("composition.json")
    Files.writeString(
      file,
      """
        {
          "version": 1,
          "expectedFragments": ["platform_core", "intellij.java.plugin"],
          "additionalModules": ["intellij.air.plugin"],
          "components": [
            {"root": "core", "manifest": "core.json"},
            {"root": "plugins", "manifest": "plugins.json", "pluginClasspathPart": "plugins.part"}
          ],
          "pluginClasspathPrefix": "prefix.bin"
        }
      """.trimIndent(),
    )

    val spec = readDevBuildCompositionSpec(file)

    assertThat(spec.expectedFragments).containsExactly("platform_core", "intellij.java.plugin")
    assertThat(spec.additionalModules).containsExactly("intellij.air.plugin")
    assertThat(spec.components).containsExactly(
      DevBuildCompositionComponent(root = "core", manifest = "core.json"),
      DevBuildCompositionComponent(root = "plugins", manifest = "plugins.json", pluginClasspathPart = "plugins.part"),
    )
    assertThat(spec.pluginClasspathPrefix).isEqualTo("prefix.bin")
  }

  @Test
  fun `composition spec rejects an unsupported version`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("composition.json")
    Files.writeString(
      file,
      """
        {
          "version": 2,
          "expectedFragments": ["platform_core"],
          "components": [{"root": "core", "manifest": "core.json"}]
        }
      """.trimIndent(),
    )

    assertThatThrownBy { readDevBuildCompositionSpec(file) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Unsupported dev-build composition spec version 2")
  }

  private fun writeManifest(file: Path, componentRoot: Path) {
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
      componentRoot = componentRoot,
    )
  }

  /**
   * The regression that turned every AIR UI lane red: a plugin the product bundles is packed by a fragment several
   * distributions share, so no component manifest names it, and summing the manifests made the distribution deny
   * having a plugin that was sitting in it.
   */
  @Test
  fun `composer declares a bundled module that no component assembled`(@TempDir tempDir: Path) {
    val components = listOf(
      DevBuildComponent(
        root = component(tempDir, "plugins-air", "plugins/air/lib/air.jar"),
        manifest = manifest(kind = "plugins_air"),
      ),
      DevBuildComponent(
        root = component(tempDir, "plugins-additional", "plugins/bridge/lib/bridge.jar"),
        manifest = manifest(kind = "plugins_additional", additionalModules = listOf("intellij.bridge.plugin")),
      ),
    )

    val result = composeDevBuildComponents(
      components = components,
      target = tempDir.resolve("target"),
      additionalModules = listOf("intellij.air.plugin", "intellij.bridge.plugin"),
    )

    assertThat(result.additionalModules).containsExactly("intellij.air.plugin", "intellij.bridge.plugin")
    // The declaration is part of the launch metadata, so a distribution that only re-declared is not reused.
    assertThat(result.fingerprint).isNotEqualTo(computeIdeFingerprintFromComponents(components.map { it.manifest }))
  }

  @Test
  fun `composer rejects a component that assembled an undeclared module`(@TempDir tempDir: Path) {
    val component = DevBuildComponent(
      root = component(tempDir, "plugins-additional", "plugins/devkit/lib/devkit.jar"),
      manifest = manifest(kind = "plugins_additional", additionalModules = listOf("intellij.devkit")),
    )

    assertThatThrownBy {
      composeDevBuildComponents(
        components = listOf(component),
        target = tempDir.resolve("target"),
        additionalModules = listOf("intellij.air.plugin"),
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("does not declare: [intellij.devkit]")
  }

  @Test
  fun `Go manifests preserve version 9 hashes and tree fingerprints`(@TempDir tempDir: Path) {
    val vectors = listOf(
      0 to 3244421341483603138L,
      1 to -2399747073602280719L,
      3 to -737883702129266468L,
      240 to 2788469911834355041L,
      241 to -4155630063455057979L,
      262143 to 9078738661776034622L,
      262144 to -1692254647099917537L,
      262145 to -2541306581069977202L,
      524288 to 3157545227256347297L,
      524301 to 8144707773225287728L,
    )
    val componentRoot = tempDir.resolve("component")
    val contentFile = componentRoot.resolve("lib/content.jar")
    Files.createDirectories(contentFile.parent)
    val launch = manifest(kind = "launch")
    for ((size, hash) in vectors) {
      Files.write(contentFile, ByteArray(size) { index -> (index * 31 + 7).toByte() })
      val treeFile = tempDir.resolve("tree.json")
      writeDevBuildComponentManifest(
        file = treeFile,
        kind = "files",
        platformPrefix = "idea",
        os = OsFamily.LINUX,
        arch = JvmArchitecture.x64,
        additionalModules = emptyList(),
        mainClass = null,
        coreClassPath = emptyList(),
        pluginCount = 0,
        componentRoot = componentRoot,
      )
      val sourcedFile = tempDir.resolve("sourced.json")
      Files.writeString(sourcedFile, """
        {
          "kind": "files", "platformPrefix": "idea", "os": "linux", "arch": "x64",
          "additionalModules": [], "mainClass": null, "coreClassPath": [],
          "entries": [{"relativePath": "lib/content.jar", "type": "component-file", "hash": $hash, "source": "inputs/content.jar"}]
        }
      """.trimIndent())
      val sourced = readDevBuildComponentManifest(sourcedFile)
      val tree = readDevBuildComponentManifest(treeFile)
      assertThat(sourced.version).isEqualTo(9)
      assertThat(tree.entries.single().hash).describedAs("hash for %s bytes", size).isEqualTo(hash)
      assertThat(sourced.entries.single().executable).isFalse()
      assertThat(computeIdeFingerprintFromComponents(listOf(launch, sourced)))
        .isEqualTo(computeIdeFingerprintFromComponents(listOf(launch, tree)))
      val relocated = sourced.copy(entries = sourced.entries.map { entry -> entry.copy(source = "other/content.jar") })
      assertThat(computeIdeFingerprintFromComponents(listOf(launch, relocated)))
        .isEqualTo(computeIdeFingerprintFromComponents(listOf(launch, sourced)))
    }
  }

  /**
   * The mode assertion the collector used to own. It was true there because the collector chmodded its own copy of the
   * jar before inventorying it; the copy is gone, so the composer normalizes instead and the manifest's
   * `executable = false` has to keep meaning the same thing.
   */
  @Test
  fun `composer copies a tree-less component's sourced files as non-executable distribution files`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("staged/shared.jar")
    Files.createDirectories(source.parent)
    Files.writeString(source, "packed bytes")
    val supportsPosix = Files.getFileStore(source).supportsFileAttributeView(PosixFileAttributeView::class.java)
    if (supportsPosix) {
      Files.setPosixFilePermissions(
        source,
        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
      )
    }
    val component = DevBuildComponent(
      root = null,
      manifest = manifest(
        kind = "plugins_packed_content_modules",
        entries = listOf(
          sourcedEntry("plugins/one/lib/modules/shared.jar", source),
          sourcedEntry("plugins/two/lib/modules/shared.jar", source),
        ),
      ),
    )
    val target = tempDir.resolve("target")

    composeDevBuildComponents(listOf(component), target)

    for (relativePath in listOf("plugins/one/lib/modules/shared.jar", "plugins/two/lib/modules/shared.jar")) {
      val copied = target.resolve(relativePath)
      assertThat(Files.readString(copied)).isEqualTo("packed bytes")
      assertThat(Files.isSymbolicLink(copied)).isFalse()
      if (supportsPosix) {
        assertThat(Files.getPosixFilePermissions(copied)).isEqualTo(setOf(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.OTHERS_READ,
        ))
      }
    }
  }

  @Test
  fun `composer honors the declared executable flag without changing the source`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("ijent")
    Files.writeString(source, "binary bytes")
    val supportsPosix = Files.getFileStore(source).supportsFileAttributeView(PosixFileAttributeView::class.java)
    val sourcePermissions = setOf(PosixFilePermission.OWNER_READ)
    if (supportsPosix) {
      Files.setPosixFilePermissions(source, sourcePermissions)
    }
    val entry = sourcedEntry("bin/ijent", source).copy(executable = true)
    val componentManifest = manifest(kind = "ijent", entries = listOf(entry))
    val target = tempDir.resolve("target")

    val composed = composeDevBuildComponents(listOf(DevBuildComponent(root = null, manifest = componentManifest)), target)

    val copied = target.resolve("bin/ijent")
    assertThat(Files.readString(copied)).isEqualTo("binary bytes")
    assertThat(Files.isSymbolicLink(copied)).isFalse()
    if (supportsPosix) {
      assertThat(Files.getPosixFilePermissions(copied)).isEqualTo(setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_EXECUTE,
      ))
      assertThat(Files.getPosixFilePermissions(source)).isEqualTo(sourcePermissions)
    }
    val nonExecutable = componentManifest.copy(entries = listOf(entry.copy(executable = false)))
    assertThat(composed.fingerprint).isEqualTo(computeIdeFingerprintFromComponents(listOf(componentManifest)))
    assertThat(composed.fingerprint).isNotEqualTo(computeIdeFingerprintFromComponents(listOf(nonExecutable)))
    Files.writeString(copied, "changed bytes")
    assertThat(Files.readString(source)).isEqualTo("binary bytes")
  }

  @Test
  fun `composer follows a staging symlink of a tree-less component`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val bytes = tempDir.resolve("bazel-out/packed.jar")
    Files.createDirectories(bytes.parent)
    Files.writeString(bytes, "jar bytes")
    val staged = tempDir.resolve("sandbox/packed.jar")
    Files.createDirectories(staged.parent)
    Files.createSymbolicLink(staged, bytes)
    val component = DevBuildComponent(
      root = null,
      manifest = manifest(kind = "platform_packed_content_modules", entries = listOf(sourcedEntry("lib/packed.jar", staged))),
    )
    val target = tempDir.resolve("target")

    composeDevBuildComponents(listOf(component), target)
    Files.delete(bytes)

    assertThat(Files.isSymbolicLink(target.resolve("lib/packed.jar"))).isFalse()
    assertThat(Files.readString(target.resolve("lib/packed.jar"))).isEqualTo("jar bytes")
  }

  @Test
  fun `composer preserves exact modes without modifying shared sources`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("shared-tool")
    Files.writeString(source, "tool")
    if (!Files.getFileStore(source).supportsFileAttributeView(PosixFileAttributeView::class.java)) return
    val sourcePermissions = setOf(PosixFilePermission.OWNER_READ)
    Files.setPosixFilePermissions(source, sourcePermissions)
    val file = sourcedEntry("plugins/demo/bin/tool", source).copy(executable = true, mode = 488)
    val componentManifest = manifest(kind = "plugin", entries = listOf(file))
    val target = tempDir.resolve("target")

    composeDevBuildComponents(listOf(DevBuildComponent(null, componentManifest)), target)

    assertThat(Files.getPosixFilePermissions(target.resolve(file.relativePath))).isEqualTo(setOf(
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
    ))
    assertThat(Files.getPosixFilePermissions(source)).isEqualTo(sourcePermissions)
    assertThat(computeIdeFingerprintFromComponents(listOf(componentManifest))).isNotEqualTo(
      computeIdeFingerprintFromComponents(listOf(componentManifest.copy(entries = listOf(file.copy(mode = 493))))),
    )
    assertThat(computeIdeFingerprintFromComponents(listOf(componentManifest.copy(entries = listOf(file.copy(mode = 493)))))).isEqualTo(
      computeIdeFingerprintFromComponents(listOf(componentManifest.copy(entries = listOf(file.copy(mode = null))))),
    )
  }

  @Test
  fun `rooted components apply declared exact modes to copied files`(@TempDir tempDir: Path) {
    val root = tempDir.resolve("component")
    val source = root.resolve("bin/tool")
    Files.createDirectories(source.parent)
    Files.writeString(source, "tool")
    if (!Files.getFileStore(source).supportsFileAttributeView(PosixFileAttributeView::class.java)) return
    val sourcePermissions = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    Files.setPosixFilePermissions(source, sourcePermissions)
    val file = DevBuildComponentEntry("bin/tool", "component-file", 1, executable = true, mode = 448)
    val target = tempDir.resolve("target")

    composeDevBuildComponents(listOf(DevBuildComponent(root, manifest(kind = "plugin", entries = listOf(file)))), target)

    assertThat(Files.getPosixFilePermissions(target.resolve("bin/tool"))).isEqualTo(setOf(
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
    ))
    assertThat(Files.getPosixFilePermissions(source)).isEqualTo(sourcePermissions)
  }

  @Test
  fun `composer copies provenanced links with exact native target spelling`(@TempDir tempDir: Path) {
    if (tempDir.fileSystem.separator != "/" || !supportsSymbolicLinks(tempDir)) return
    val sourceRoot = tempDir.resolve("plugin")
    Files.createDirectories(sourceRoot)
    val source = sourceRoot.resolve("current")
    val spelling = "lib//payload/"
    check(ProcessBuilder("ln", "-s", spelling, source.toString()).start().waitFor() == 0)
    val link = DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = spelling, symlinkSource = source.toString())
    val target = tempDir.resolve("target")

    composeDevBuildComponents(
      listOf(DevBuildComponent(null, manifest(kind = "plugin", entries = listOf(link)))), target,
      sourceDirectoryRunfiles = mapOf(sourceRoot to "_main/plugin"),
    )

    val readlink = ProcessBuilder("readlink", target.resolve(link.relativePath).toString()).start()
    val actual = readlink.inputStream.readAllBytes().toString(Charsets.UTF_8)
    assertThat(readlink.waitFor()).isZero()
    assertThat(actual).isEqualTo("$spelling\n")
    assertThat(Files.isSymbolicLink(source)).isTrue()
  }

  @Test
  fun `composer rejects link provenance through an escaping directory alias`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val sourceRoot = tempDir.resolve("plugin")
    val outside = tempDir.resolve("outside")
    Files.createDirectories(sourceRoot)
    Files.createDirectories(outside)
    Files.createSymbolicLink(sourceRoot.resolve("escape"), outside)
    Files.createSymbolicLink(outside.resolve("current"), Path.of("target"))
    val link = DevBuildComponentEntry(
      "plugins/demo/current", "symlink", 1, symlinkTarget = "target", symlinkSource = sourceRoot.resolve("escape/current").toString(),
    )
    assertThatThrownBy {
      composeDevBuildComponents(
        listOf(DevBuildComponent(null, manifest(kind = "plugin", entries = listOf(link)))), tempDir.resolve("target"),
        sourceDirectoryRunfiles = mapOf(sourceRoot to "_main/plugin"),
      )
    }.hasMessageContaining("stale or escaping symbolic link provenance")
  }

  @Test
  fun `composer rejects stale link targets instead of exporting them`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val sourceRoot = tempDir.resolve("plugin")
    Files.createDirectories(sourceRoot)
    val source = sourceRoot.resolve("current")
    Files.createSymbolicLink(source, Path.of("../../outside"))
    val link = DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = "lib/payload", symlinkSource = source.toString())
    val target = tempDir.resolve("target")
    assertThatThrownBy {
      composeDevBuildComponents(
        listOf(DevBuildComponent(null, manifest(kind = "plugin", entries = listOf(link)))), target,
        sourceDirectoryRunfiles = mapOf(sourceRoot to "_main/plugin"),
      )
    }.hasMessageContaining("stale or escaping symbolic link provenance")
    assertThat(Files.exists(target.resolve(link.relativePath), java.nio.file.LinkOption.NOFOLLOW_LINKS)).isFalse()
  }

  @Test
  fun `composer rejects regular files through escaping directory aliases`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val sourceRoot = tempDir.resolve("plugin")
    val outside = tempDir.resolve("outside")
    Files.createDirectories(sourceRoot)
    Files.createDirectories(outside)
    Files.writeString(outside.resolve("payload"), "outside bytes")
    Files.createSymbolicLink(sourceRoot.resolve("alias"), outside)
    val file = sourcedEntry("plugins/demo/payload", sourceRoot.resolve("alias/payload"))
    val target = tempDir.resolve("target")
    assertThatThrownBy {
      composeDevBuildComponents(
        listOf(DevBuildComponent(null, manifest(kind = "plugin", entries = listOf(file)))), target,
        sourceDirectoryRunfiles = mapOf(sourceRoot to "_main/plugin"),
      )
    }.hasMessageContaining("escapes its declared source directory")
    assertThat(Files.exists(target.resolve(file.relativePath))).isFalse()
  }

  @Test
  fun `composer rejects a tree-less component entry that names no source`(@TempDir tempDir: Path) {
    val component = DevBuildComponent(
      root = null,
      manifest = manifest(
        kind = "platform_packed_content_modules",
        entries = listOf(DevBuildComponentEntry(relativePath = "lib/packed.jar", type = "component-file", hash = 1)),
      ),
    )

    assertThatThrownBy { composeDevBuildComponents(listOf(component), tempDir.resolve("target")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("must name where its bytes are")
  }

  @Test
  fun `composer rejects a symbolic link with an ambiguous file source`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("packed.jar")
    Files.writeString(source, "packed bytes")
    val component = DevBuildComponent(
      root = null,
      manifest = manifest(
        kind = "platform_packed_content_modules",
        entries = listOf(sourcedEntry("lib/packed.jar", source).copy(symlinkTarget = "other.jar")),
      ),
    )

    assertThatThrownBy { composeDevBuildComponents(listOf(component), tempDir.resolve("target")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("must declare the symbolic link 'lib/packed.jar' without a file source")
  }

  @Test
  fun `composer creates explicit links for manifest-only components`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val source = tempDir.resolve("packed.jar")
    Files.writeString(source, "packed bytes")
    val entries = listOf(
      sourcedEntry("plugins/demo/lib/packed.jar", source),
      DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = "lib/packed.jar"),
    )
    val component = DevBuildComponent(root = null, manifest = manifest("plugin", entries = entries))
    val target = tempDir.resolve("target")
    composeDevBuildComponents(listOf(component), target)

    assertThat(Files.isSymbolicLink(target.resolve("plugins/demo/current"))).isTrue()
    assertThat(Files.readSymbolicLink(target.resolve("plugins/demo/current"))).isEqualTo(Path.of("lib/packed.jar"))
    assertThat(Files.readString(target.resolve("plugins/demo/current"))).isEqualTo("packed bytes")
  }

  @Test
  fun `composer orders a link after the links its target traverses`() {
    val links = linkedMapOf(
      "plugins/jcef/jcef.framework/Frameworks" to "Versions/Current/Frameworks",
      "plugins/jcef/jcef.framework/Resources" to "Versions/Current/Resources",
      "plugins/jcef/jcef.framework/Versions/Current" to "A",
      "plugins/jcef/jcef.framework/lib" to "../shared/lib",
      "plugins/jcef/shared/lib" to "lib-1",
    )
    assertThat(orderDevBuildLinks(links)).containsExactly(
      "plugins/jcef/jcef.framework/Versions/Current",
      "plugins/jcef/shared/lib",
      "plugins/jcef/jcef.framework/Frameworks",
      "plugins/jcef/jcef.framework/Resources",
      "plugins/jcef/jcef.framework/lib",
    )
    assertThatThrownBy { orderDevBuildLinks(linkedMapOf("a" to "b", "b" to "a")) }.hasMessageContaining("symbolic link cycle")
  }

  @Test
  fun `composer rejects a file inside another declared entry`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("packed.jar")
    Files.writeString(source, "bytes")
    val component = DevBuildComponent(root = null, manifest = manifest("plugin", entries = listOf(
      sourcedEntry("plugins/demo", source), sourcedEntry("plugins/demo/lib/plugin.jar", source),
    )))
    assertThatThrownBy { composeDevBuildComponents(listOf(component), tempDir.resolve("target")) }
      .hasMessageContaining("is below another entry")
    assertThat(Files.exists(tempDir.resolve("target/plugins"))).isFalse()
  }

  @Test
  fun `composer reserves generated metadata destinations`(@TempDir tempDir: Path) {
    for (path in listOf("fingerprint.txt", "Fingerprint.txt", "core-classpath.txt", "plugins/plugin-classpath.txt", "plugins", "Plugins")) {
      val link = DevBuildComponentEntry(path, "symlink", 1, symlinkTarget = "lib/app.jar")
      val component = DevBuildComponent(root = null, manifest = manifest("plugin", entries = listOf(link)))
      assertThatThrownBy { composeDevBuildComponents(listOf(component), tempDir.resolve("target")) }
        .isInstanceOf(IllegalStateException::class.java)
      assertThat(Files.exists(tempDir.resolve("target/$path"))).isFalse()
    }
  }

  @Test
  fun `composer rejects escaping link chains before creating links`(@TempDir tempDir: Path) {
    val links = listOf(
      DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = "../.."),
      DevBuildComponentEntry("plugins/demo/escape", "symlink", 1, symlinkTarget = "current/../outside"),
    )
    val component = DevBuildComponent(root = null, manifest = manifest("plugin", entries = links))
    assertThatThrownBy { composeDevBuildComponents(listOf(component), tempDir.resolve("target")) }.hasMessageContaining("link chain escapes")
    assertThat(Files.exists(tempDir.resolve("target/plugins"))).isFalse()
  }

  @Test
  fun `composer fails instead of changing a manifest-only link target`(@TempDir tempDir: Path) {
    for (target in listOf("payload/", "lib//payload")) {
      val link = DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = target)
      val component = DevBuildComponent(root = null, manifest = manifest("plugin", entries = listOf(link)))
      assertThatThrownBy { composeDevBuildComponents(listOf(component), tempDir.resolve("target")) }.hasMessageContaining("cannot preserve symbolic link")
      assertThat(Files.exists(tempDir.resolve("target/plugins"))).isFalse()
    }
  }

  /** What replaces relativizing a walked path: a manifest path is a string, so the escape is checked explicitly. */
  @Test
  fun `composer rejects a tree-less component entry that escapes the distribution`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("packed.jar")
    Files.writeString(source, "packed bytes")
    val component = DevBuildComponent(
      root = null,
      manifest = manifest(kind = "platform_packed_content_modules", entries = listOf(sourcedEntry("../outside.jar", source))),
    )

    assertThatThrownBy { composeDevBuildComponents(listOf(component), tempDir.resolve("target")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("escapes the distribution: ../outside.jar")
  }

  @Test
  fun `composer rejects a path a tree-less and a tree component both provide`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("packed.jar")
    Files.writeString(source, "packed bytes")
    val components = listOf(
      DevBuildComponent(root = component(tempDir, "platform", "lib/packed.jar"), manifest = manifest(kind = "platform_lib")),
      DevBuildComponent(
        root = null,
        manifest = manifest(kind = "platform_packed_content_modules", entries = listOf(sourcedEntry("lib/packed.jar", source))),
      ),
    )

    assertThatThrownBy { composeDevBuildComponents(components, tempDir.resolve("target")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("both provide 'lib/packed.jar'")
  }

  private fun sourcedEntry(relativePath: String, source: Path): DevBuildComponentEntry {
    return DevBuildComponentEntry(
      relativePath = relativePath,
      type = "component-file",
      hash = 1,
      source = source.toString(),
    )
  }

  @Test
  fun `composer consumes bound sandbox members and genuine links`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    if (!Files.getFileStore(tempDir).supportsFileAttributeView(PosixFileAttributeView::class.java)) return
    val fixture = boundTree(tempDir, listOf("lib/native.jar", "current"))
    Files.createSymbolicLink(fixture.physical.resolve("current"), Path.of("lib/native.jar"))
    Files.createSymbolicLink(fixture.staged.resolve("current"), fixture.physical.resolve("current"))
    val entries = listOf(
      sourcedEntry("plugins/demo/lib/native.jar", fixture.staged.resolve("lib/native.jar")).copy(mode = 489, executable = true),
      DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = "lib/native.jar", symlinkSource = fixture.staged.resolve("current").toString()),
    )
    val target = tempDir.resolve("target")
    composeDevBuildComponents(
      listOf(DevBuildComponent(null, manifest("plugin", entries = entries), sourceBindings = fixture.bindings)), target,
      sourceDirectoryRunfiles = mapOf(fixture.staged to "_main/tree"),
    )
    assertThat(Files.readString(target.resolve(entries.first().relativePath))).isEqualTo("native bytes")
    assertThat(Files.isSymbolicLink(target.resolve(entries.first().relativePath))).isFalse()
    assertThat(Files.readSymbolicLink(target.resolve("plugins/demo/current"))).isEqualTo(Path.of("lib/native.jar"))
    assertThat(Files.getPosixFilePermissions(target.resolve(entries.first().relativePath))).isEqualTo(setOf(
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE,
    ))
  }

  @Test
  fun `composer rejects sandbox members without bindings`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val fixture = boundTree(tempDir)
    assertThatThrownBy {
      composeDevBuildComponents(
        listOf(DevBuildComponent(null, manifest("plugin", entries = listOf(sourcedEntry("lib/native.jar", fixture.staged.resolve("lib/native.jar")))))),
        tempDir.resolve("target"), sourceDirectoryRunfiles = mapOf(fixture.staged to "_main/tree"),
      )
    }.hasMessageContaining("escapes its declared source directory")
  }

  @Test
  fun `source bindings reject outside sources and member tampering`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val fixture = boundTree(tempDir)
    val outside = Files.writeString(tempDir.resolve("outside"), "native bytes")
    assertThatThrownBy { fixture.bindings.resolve(outside, symlink = false) }.hasMessageContaining("Missing declared artifact binding")
    assertThatThrownBy { fixture.bindings.resolve(fixture.staged.resolve("lib/Native.jar"), symlink = false) }
      .hasMessageContaining("Missing declared artifact binding")
    assertThatThrownBy { fixture.bindings.resolve(fixture.staged, symlink = false) }.hasMessageContaining("Missing declared artifact binding")
    val other = readDevBuildSourceBindings(
      fixture.staged.parent.parent.resolve("metadata/bindings.jsonl"),
      listOf(DevBuildCompositionComponent(manifest = "plugin"), DevBuildCompositionComponent(manifest = "other")),
    ).getValue("other")
    assertThatThrownBy { other.resolve(fixture.staged.resolve("lib/native.jar"), symlink = false) }
      .hasMessageContaining("Missing declared artifact binding")
    val staged = fixture.staged.resolve("lib/native.jar")
    Files.delete(staged)
    Files.createSymbolicLink(staged, outside)
    assertThatThrownBy { fixture.bindings.resolve(staged, symlink = false) }.hasMessageContaining("Staged source differs")
  }

  @Test
  fun `source bindings reject genuine file links and directory escapes`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    for (escape in listOf("file", "directory", "root")) {
      val fixture = boundTree(Files.createDirectory(tempDir.resolve(escape)))
      val outside = Files.createDirectories(tempDir.resolve("outside-$escape/lib"))
      Files.writeString(outside.resolve("native.jar"), "native bytes")
      val member = fixture.physical.resolve("lib/native.jar")
      Files.delete(member)
      when (escape) {
        "file" -> Files.createSymbolicLink(member, outside.resolve("native.jar"))
        "directory" -> {
          Files.delete(member.parent)
          Files.createSymbolicLink(member.parent, outside)
        }
        else -> {
          Files.delete(member.parent)
          Files.delete(fixture.physical)
          Files.createSymbolicLink(fixture.physical, outside.parent)
        }
      }
      assertThatThrownBy { fixture.bindings.resolve(fixture.staged.resolve("lib/native.jar"), symlink = false) }
        .hasMessageContaining(when (escape) {
          "file" -> "not a regular file"
          "directory" -> "escaping directory alias"
          else -> "escapes its artifact binding"
        })
    }
  }

  @Test
  fun `source bindings reject escaping genuine links`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val fixture = boundTree(tempDir, listOf("lib/native.jar", "current"))
    val outside = Files.writeString(fixture.physical.parent.resolve("outside"), "outside")
    Files.createSymbolicLink(fixture.physical.resolve("current"), Path.of("../outside"))
    Files.createSymbolicLink(fixture.staged.resolve("current"), fixture.physical.resolve("current"))
    assertThat(Files.exists(outside)).isTrue()
    assertThatThrownBy { fixture.bindings.resolve(fixture.staged.resolve("current"), symlink = true) }
      .hasMessageContaining("symbolic link escapes its directory")
  }

  @Test
  fun `source bindings check every link in a chain`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val fixture = boundTree(tempDir, listOf("lib/native.jar", "current", "bridge"))
    Files.createSymbolicLink(fixture.physical.resolve("current"), Path.of("bridge"))
    Files.createSymbolicLink(fixture.physical.resolve("bridge"), Path.of("lib/native.jar"))
    Files.createSymbolicLink(fixture.staged.resolve("current"), fixture.physical.resolve("current"))
    assertThat(fixture.bindings.resolve(fixture.staged.resolve("current"), symlink = true)).isEqualTo(fixture.physical.resolve("current"))
    Files.delete(fixture.physical.resolve("bridge"))
    Files.createSymbolicLink(fixture.physical.resolve("bridge"), Path.of("../roundtrip"))
    Files.createSymbolicLink(fixture.physical.parent.resolve("roundtrip"), Path.of("plugin/lib/native.jar"))
    assertThat(fixture.physical.resolve("current").toRealPath()).isEqualTo(fixture.physical.resolve("lib/native.jar"))
    assertThatThrownBy { fixture.bindings.resolve(fixture.staged.resolve("current"), symlink = true) }
      .hasMessageContaining("symbolic link escapes its directory")
  }

  @Test
  fun `source bindings reject a case alias in a genuine link`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    rejectBoundLinkAlias(tempDir, "lib/native.jar", "LIB/native.jar", intermediate = false)
  }

  @Test
  fun `source bindings reject a Unicode alias in a genuine link`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    rejectBoundLinkAlias(tempDir, "lib/\u00e9.jar", "lib/e\u0301.jar", intermediate = false)
  }

  @Test
  fun `source bindings reject a case alias in an intermediate link`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    rejectBoundLinkAlias(tempDir, "lib/native.jar", "LIB/native.jar", intermediate = true)
  }

  @Test
  fun `source bindings reject a Unicode alias in an intermediate link`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    rejectBoundLinkAlias(tempDir, "lib/\u00e9.jar", "lib/e\u0301.jar", intermediate = true)
  }

  @Test
  fun `source bindings reject a case alias in a directory link`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val fixture = boundTree(tempDir, listOf("lib/native.jar", "current/native.jar"))
    Files.createSymbolicLink(fixture.physical.resolve("current"), Path.of("LIB"))
    Files.createDirectories(fixture.staged.resolve("current"))
    Files.createSymbolicLink(fixture.staged.resolve("current/native.jar"), fixture.physical.resolve("current/native.jar"))
    assertThatThrownBy { fixture.bindings.resolve(fixture.staged.resolve("current"), symlink = true) }
      .hasMessageContaining("unbound member spelling")
  }

  @Test
  fun `composer preserves equivalent relative link spellings`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    for ((index, target) in listOf("./lib/native.jar", "lib/../lib/native.jar").withIndex()) {
      composeBoundLink(tempDir.resolve("spelling-$index"), "lib/native.jar", target, directory = false)
    }
  }

  @Test
  fun `composer preserves an equivalent directory link spelling`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    composeBoundLink(tempDir, "lib/native.jar", "./lib/../lib/.", directory = true)
  }

  @Test
  fun `composer preserves an exact Unicode link spelling`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    composeBoundLink(tempDir, "lib/\u00e9.jar", "lib/\u00e9.jar", directory = false)
  }

  @Test
  fun `source bindings reject a changed member type`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val fixture = boundTree(tempDir, listOf("lib/native.jar", "current"))
    Files.createSymbolicLink(fixture.physical.resolve("current"), Path.of("lib"))
    Files.createSymbolicLink(fixture.staged.resolve("current"), fixture.physical.resolve("current"))
    assertThatThrownBy { fixture.bindings.resolve(fixture.staged.resolve("current"), symlink = true) }
      .hasMessageContaining("differs from its bound type")
  }

  @Test
  fun `source bindings reject missing members and aliases`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    val fixture = boundTree(tempDir.resolve("missing"), emptyList())
    assertThatThrownBy { fixture.bindings.resolve(fixture.staged.resolve("lib/native.jar"), symlink = false) }
      .hasMessageContaining("Missing declared artifact binding")
    for ((index, members) in listOf(
      listOf("lib/native.jar", "lib/native.jar"),
      listOf("lib/native.jar", "lib/Native.jar"),
      listOf("lib/native.jar", "Lib/second.jar"),
      listOf("lib/\u00e9.jar", "lib/e\u0301.jar"),
      listOf("lib/../outside"),
      listOf("lib//native.jar"),
      listOf("lib", "lib/native.jar"),
    ).withIndex()) {
      assertThatThrownBy { boundTree(tempDir.resolve("invalid-$index"), members) }.isInstanceOf(IllegalStateException::class.java)
    }
  }

  @Test
  fun `source bindings reject changed owners and anchor paths`(@TempDir tempDir: Path) {
    if (!supportsSymbolicLinks(tempDir)) return
    for ((index, mutation) in listOf<Pair<String, String>>(
      "\"component\":\"plugin\"" to "\"component\":\"other\"",
      "../trees/plugin" to "../trees/other",
      "\"type\":\"directory\"" to "\"type\":\"file\"",
    ).withIndex()) {
      val fixture = boundTree(tempDir.resolve("tamper-$index"))
      val file = fixture.staged.parent.parent.resolve("metadata/bindings.jsonl")
      readDevBuildSourceBindings(file, listOf(DevBuildCompositionComponent(manifest = "plugin")))
      Files.writeString(file, Files.readString(file).replace(mutation.first, mutation.second))
      assertThatThrownBy { readDevBuildSourceBindings(file, listOf(DevBuildCompositionComponent(manifest = "plugin"))) }
        .isInstanceOf(IllegalStateException::class.java)
    }
  }

  private data class BoundTree(
    @JvmField val physical: Path,
    @JvmField val staged: Path,
    @JvmField val bindings: DevBuildComponentSources,
  )

  private fun rejectBoundLinkAlias(tempDir: Path, filename: String, target: String, intermediate: Boolean) {
    val fixture = boundTree(tempDir, listOf(filename, "current") + if (intermediate) listOf("bridge") else emptyList())
    if (filename != "lib/native.jar") {
      Files.writeString(fixture.physical.resolve(filename), "native bytes")
      Files.createSymbolicLink(fixture.staged.resolve(filename), fixture.physical.resolve(filename))
    }
    Files.createSymbolicLink(fixture.physical.resolve("current"), Path.of(if (intermediate) "bridge" else target))
    if (intermediate) Files.createSymbolicLink(fixture.physical.resolve("bridge"), Path.of(target))
    Files.createSymbolicLink(fixture.staged.resolve("current"), fixture.physical.resolve("current"))
    assertThatThrownBy { fixture.bindings.resolve(fixture.staged.resolve("current"), symlink = true) }
      .hasMessageContaining("unbound member spelling")
  }

  private fun composeBoundLink(tempDir: Path, filename: String, linkTarget: String, directory: Boolean) {
    val fixture = boundTree(tempDir, listOf(filename, if (directory) "current/native.jar" else "current"))
    if (filename != "lib/native.jar") {
      Files.writeString(fixture.physical.resolve(filename), "native bytes")
      Files.createSymbolicLink(fixture.staged.resolve(filename), fixture.physical.resolve(filename))
    }
    Files.createSymbolicLink(fixture.physical.resolve("current"), Path.of(linkTarget))
    if (directory) {
      Files.createDirectories(fixture.staged.resolve("current"))
      Files.createSymbolicLink(fixture.staged.resolve("current/native.jar"), fixture.physical.resolve("current/native.jar"))
    }
    else {
      Files.createSymbolicLink(fixture.staged.resolve("current"), fixture.physical.resolve("current"))
    }
    val entries = listOf(
      sourcedEntry(filename, fixture.staged.resolve(filename)),
      DevBuildComponentEntry("current", "symlink", 1, symlinkTarget = linkTarget, symlinkSource = fixture.staged.resolve("current").toString()),
    )
    val target = tempDir.resolve("target")
    val result = composeDevBuildComponents(
      listOf(DevBuildComponent(null, manifest("plugin", coreClassPath = listOf(filename), entries = entries), sourceBindings = fixture.bindings)), target,
      sourceDirectoryRunfiles = mapOf(fixture.staged to "_main/tree"),
    )
    assertThat(Files.readSymbolicLink(target.resolve("current")).toString()).isEqualTo(linkTarget)
    assertThat(Files.readString(target.resolve(if (directory) "current/native.jar" else "current"))).isEqualTo("native bytes")
    assertThat(result.coreClassPath).containsExactly(filename)
  }

  private fun boundTree(tempDir: Path, members: List<String> = listOf("lib/native.jar")): BoundTree {
    Files.createDirectories(tempDir)
    val physical = Files.createDirectories(tempDir.toRealPath().resolve("physical/trees/plugin/lib")).parent
    val staged = Files.createDirectories(tempDir.toRealPath().resolve("sandbox/trees/plugin/lib")).parent
    Files.writeString(physical.resolve("lib/native.jar"), "native bytes")
    Files.createSymbolicLink(staged.resolve("lib/native.jar"), physical.resolve("lib/native.jar"))
    val physicalMetadata = Files.createDirectories(physical.parent.parent.resolve("metadata")).resolve("bindings.jsonl")
    val stagedMetadata = Files.createDirectories(staged.parent.parent.resolve("metadata")).resolve("bindings.jsonl")
    Files.writeString(physicalMetadata, buildJsonObject {
      put("component", "plugin")
      put("source", staged.toString())
      put("anchorRelativePath", "../trees/plugin")
      put("type", "directory")
      put("members", buildJsonArray { for (member in members) add(JsonPrimitive(member)) })
    }.toString())
    Files.createSymbolicLink(stagedMetadata, physicalMetadata)
    return BoundTree(physical, staged, readDevBuildSourceBindings(stagedMetadata, listOf(DevBuildCompositionComponent(manifest = "plugin"))).getValue("plugin"))
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
    entries: List<DevBuildComponentEntry> = emptyList(),
    mainClass: String? = "com.intellij.idea.Main",
    os: String = "linux",
    arch: String = "x64",
  ): DevBuildComponentManifest {
    return DevBuildComponentManifest(
      kind = kind,
      platformPrefix = platformPrefix,
      os = os,
      arch = arch,
      additionalModules = additionalModules,
      mainClass = mainClass,
      coreClassPath = coreClassPath,
      pluginCount = pluginCount,
      entries = entries,
    )
  }
}
