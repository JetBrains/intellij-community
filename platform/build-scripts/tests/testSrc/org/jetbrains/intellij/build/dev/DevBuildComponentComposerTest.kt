// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

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
  fun `platform resources solely own IJent DistFiles`() {
    val platformLib = DevBuildFragment(
      name = "platform_lib",
      platform = PlatformJarSelector(jars = setOf("intellij.charts.jar"), mode = PlatformJarSelector.Mode.EXCLUDE),
      platformResources = false,
      plugins = null,
    )
    val platformResources = DevBuildFragment(
      name = "platform_resources",
      platform = null,
      platformResources = true,
      plugins = null,
    )

    assertThat(shouldCopyDevBuildDistFile(platformLib, "lib/ijent/ijent-x86_64-unknown-linux-musl-release")).isFalse()
    assertThat(shouldCopyDevBuildDistFile(platformResources, "lib/ijent/ijent-x86_64-unknown-linux-musl-release")).isTrue()
    assertThat(shouldCopyDevBuildDistFile(platformLib, "bin/another-dist-file")).isTrue()
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
  fun `composition spec decodes its versioned contract`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("composition.json")
    Files.writeString(
      file,
      """
        {
          "version": 1,
          "expectedFragments": ["platform_core", "plugins_rest"],
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

    assertThat(spec.expectedFragments).containsExactly("platform_core", "plugins_rest")
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
  fun `sourced component manifest names where each file's bytes are and hashes a shared source once`(@TempDir tempDir: Path) {
    val shared = tempDir.resolve("shared.jar")
    Files.writeString(shared, "packed bytes")
    val manifestFile = tempDir.resolve("component.json")

    writeSourcedDevBuildComponentManifest(
      file = manifestFile,
      kind = "plugins_packed_content_modules",
      platformPrefix = "idea",
      os = OsFamily.LINUX,
      arch = JvmArchitecture.x64,
      files = listOf(
        DevBuildComponentSourcedFile("plugins/two/lib/modules/shared.jar", shared.toString()),
        DevBuildComponentSourcedFile("plugins/one/lib/modules/shared.jar", shared.toString()),
      ),
    )

    val manifest = readDevBuildComponentManifest(manifestFile)
    // Such a component knows its product and target platform and nothing that needs a product layout.
    assertThat(manifest.mainClass).isNull()
    assertThat(manifest.coreClassPath).isEmpty()
    assertThat(manifest.entries.map { it.relativePath }).containsExactly(
      "plugins/one/lib/modules/shared.jar",
      "plugins/two/lib/modules/shared.jar",
    )
    assertThat(manifest.entries).allSatisfy { entry ->
      assertThat(entry.type).isEqualTo("component-file")
      assertThat(entry.source).isEqualTo(shared.toString())
      assertThat(entry.symlinkTarget).isNull()
      // Not read off the source: the composer chmods what it writes, so this is false by construction.
      assertThat(entry.executable).isFalse()
    }
    // One jar under two destinations is one set of bytes, so both entries carry the same hash.
    assertThat(manifest.entries.map { it.hash }.distinct()).hasSize(1)
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

  /** What replaces `linksNotSeen`: with no tree to check a declared link against, a link may not be declared at all. */
  @Test
  fun `composer rejects a symbolic link declared by a tree-less component`(@TempDir tempDir: Path) {
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
      .hasMessageContaining("cannot declare the symbolic link 'lib/packed.jar'")
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
  ): DevBuildComponentManifest {
    return DevBuildComponentManifest(
      kind = kind,
      platformPrefix = platformPrefix,
      os = "linux",
      arch = "x64",
      additionalModules = additionalModules,
      mainClass = mainClass,
      coreClassPath = coreClassPath,
      pluginCount = pluginCount,
      entries = entries,
    )
  }
}
