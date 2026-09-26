package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class DevBuildLocalLayoutTest {
  @Test
  fun `directory components preserve modes in local and full layouts without payload trees`(@TempDir tempDir: Path) {
    val entries = listOf(
      DevBuildComponentEntry("resources/empty", "directory", mode = 456),
      DevBuildComponentEntry("resources", "directory", mode = 448),
    )
    val component = component(null, "plugin", entries)
    val metadata = tempDir.resolve("metadata")
    composeDevBuildComponents(listOf(component), metadata, sourceRunfiles = emptyMap())
    val layout = Files.readString(metadata.resolve("local-layout.json"))
    assertThat(layout).contains("\"kind\":\"directory\"", "\"mode\":456", "\"mode\":448")
    assertThat(Files.exists(metadata.resolve("resources"))).isFalse()
    val target = tempDir.resolve("home")
    composeDevBuildComponents(listOf(component), target)
    for (entry in entries) {
      assertThat(Files.isDirectory(target.resolve(entry.relativePath))).isTrue()
      assertThat((Files.getAttribute(target.resolve(entry.relativePath), "unix:mode") as Int) and 511).isEqualTo(entry.mode)
    }
    assertThat(computeIdeFingerprintFromComponents(listOf(component.manifest)))
      .isNotEqualTo(computeIdeFingerprintFromComponents(listOf(component.manifest.copy(entries = entries.map { it.copy(mode = 493) }))))
    for (invalid in listOf(entries.first().copy(hash = 0), entries.first().copy(source = "tree"),
                           entries.first().copy(executable = true), entries.first().copy(symlinkTarget = "other"))) {
      assertThatThrownBy { composeDevBuildComponents(listOf(component(null, "invalid", listOf(invalid))), tempDir.resolve("invalid")) }
        .hasMessageContaining("Invalid directory")
    }
  }

  @Test
  fun `a manifest-only link reaches the local layout without a payload`(@TempDir tempDir: Path) {
    val link = DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = "lib/payload")
    val target = tempDir.resolve("metadata")
    composeDevBuildComponents(listOf(component(null, "plugin", listOf(link))), target, sourceRunfiles = emptyMap())
    assertThat(Files.readString(target.resolve("local-layout.json"))).contains("\"symlinkTarget\":\"lib/payload\"")
  }

  @Test
  fun `exact modes remain in launch metadata without reading payloads`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("absent-tool")
    val file = entry("plugins/demo/bin/tool").copy(source = source.toString(), executable = true, mode = 488)
    val target = tempDir.resolve("metadata")
    composeDevBuildComponents(
      listOf(component(null, "plugin", listOf(file))), target, sourceRunfiles = mapOf(source to "_main/absent-tool"),
    )
    assertThat(Files.readString(target.resolve("local-layout.json"))).contains("\"mode\":488")
    assertThat(Files.exists(source)).isFalse()
  }

  @Test
  fun `canonical modes retain the existing local linking policy`(@TempDir tempDir: Path) {
    val jar = tempDir.resolve("absent.jar")
    val executable = tempDir.resolve("absent-tool")
    val files = listOf(
      entry("plugins/demo/lib/main.jar").copy(source = jar.toString(), mode = 420),
      entry("plugins/demo/bin/tool").copy(source = executable.toString(), mode = 493, executable = true),
    )
    val target = tempDir.resolve("metadata")
    composeDevBuildComponents(
      listOf(component(null, "plugin", files)), target,
      sourceRunfiles = mapOf(jar to "_main/absent.jar", executable to "_main/absent-tool"),
    )
    val metadata = Files.readString(target.resolve("local-layout.json"))
    assertThat(metadata).doesNotContain("\"mode\":420", "\"mode\":493")
    assertThat(metadata).contains("\"executable\":false", "\"executable\":true")
    assertThat(Files.exists(jar)).isFalse()
    assertThat(Files.exists(executable)).isFalse()
  }

  @Test
  fun `invalid or conflicting modes fail before creating launch metadata`(@TempDir tempDir: Path) {
    val cases = listOf(
      entry("bin/tool").copy(mode = -1),
      entry("bin/tool").copy(mode = 512),
      entry("bin/tool").copy(mode = 493),
      entry("bin/tool").copy(mode = 420, executable = true),
      DevBuildComponentEntry("bin/link", "symlink", 1, symlinkTarget = "tool", mode = 0),
    )
    for ((index, file) in cases.withIndex()) {
      val target = tempDir.resolve("metadata-$index")
      assertThatThrownBy {
        composeDevBuildComponents(listOf(component(null, "plugin", listOf(file))), target, sourceRunfiles = emptyMap())
      }.hasMessageContaining("file mode")
      assertThat(Files.exists(target)).isFalse()
    }
  }

  @Test
  fun `local composition reads metadata without staging payload`(@TempDir tempDir: Path) {
    val root = tempDir.resolve("absent-tree")
    val packed = tempDir.resolve("absent.jar")
    val components = listOf(
      component(root, "platform", listOf(entry("lib/app.jar"))),
      component(null, "packed", listOf(entry("plugins/demo/lib/demo.jar").copy(source = packed.toString()))),
    )
    val target = tempDir.resolve("metadata")
    composeDevBuildComponents(
      components, target, expectedFragments = listOf("platform", "packed"),
      sourceRunfiles = mapOf(root to "_main/tree", packed to "community+/packed.jar"),
    )
    val layout = Files.readString(target.resolve("local-layout.json"))
    assertThat(layout).contains("\"runfile\":\"_main/tree/lib/app.jar\"", "\"runfile\":\"community+/packed.jar\"")
    assertThat(Files.exists(target.resolve("lib"))).isFalse()
    assertThat(Files.exists(target.resolve("plugins"))).isFalse()
    assertThat(Files.exists(root)).isFalse()
    assertThat(Files.exists(packed)).isFalse()
  }

  @Test
  fun `local and exported compositions have the same metadata`(@TempDir tempDir: Path) {
    val root = tempDir.resolve("tree")
    Files.createDirectories(root.resolve("lib"))
    Files.writeString(root.resolve("lib/app.jar"), "bytes")
    val prefix = tempDir.resolve("prefix")
    val part = tempDir.resolve("part")
    Files.write(prefix, byteArrayOf(1, 2, 3))
    Files.write(part, byteArrayOf(4, 5, 6))
    val component = component(root, "platform", listOf(entry("lib/app.jar"))).let {
      it.copy(manifest = it.manifest.copy(pluginCount = 1), pluginClasspathPart = part)
    }
    val exported = composeDevBuildComponents(listOf(component), tempDir.resolve("dist"), prefix)
    Files.delete(root.resolve("lib/app.jar"))
    val local = composeDevBuildComponents(listOf(component), tempDir.resolve("metadata"), prefix, sourceRunfiles = mapOf(root to "_main/tree"))
    assertThat(local).isEqualTo(exported)
    assertThat(Files.readAllBytes(tempDir.resolve("metadata/plugins/plugin-classpath.txt")))
      .isEqualTo(Files.readAllBytes(tempDir.resolve("dist/plugins/plugin-classpath.txt")))
  }

  @Test
  fun `local composition preserves genuine relative links`(@TempDir tempDir: Path) {
    val root = tempDir.resolve("tree")
    val components = listOf(component(root, "platform", listOf(entry("lib/current").copy(symlinkTarget = "versions/A"))))
    composeDevBuildComponents(components, tempDir.resolve("metadata"), sourceRunfiles = mapOf(root to "_main/tree"))
    val layout = Files.readString(tempDir.resolve("metadata/local-layout.json"))
    assertThat(layout).contains("\"symlinkTarget\":\"versions/A\"", "\"runfile\":null")
  }

  @Test
  fun `local composition rejects undeclared inputs`(@TempDir tempDir: Path) {
    val component = component(tempDir.resolve("tree"), "platform", listOf(entry("lib/app.jar")))
    assertThatThrownBy { composeDevBuildComponents(listOf(component), tempDir.resolve("metadata"), sourceRunfiles = emptyMap()) }
      .hasMessageContaining("undeclared source")
  }

  @Test
  fun `local composition resolves files inside declared directories without reading them`(@TempDir tempDir: Path) {
    val directory = tempDir.resolve("absent-plugin")
    val packed = directory.resolve("lib/nested/plugin.jar")
    val component = component(null, "plugin", listOf(entry("plugins/demo/lib/plugin.jar").copy(source = packed.toString())))
    val target = tempDir.resolve("metadata")
    composeDevBuildComponents(
      listOf(component), target, sourceRunfiles = emptyMap(), sourceDirectoryRunfiles = mapOf(directory to "_main/plugin"),
    )

    assertThat(Files.readString(target.resolve("local-layout.json"))).contains("\"runfile\":\"_main/plugin/lib/nested/plugin.jar\"")
    assertThat(Files.exists(directory)).isFalse()
    assertThat(Files.exists(target.resolve("plugins"))).isFalse()
  }

  @Test
  fun `local composition does not treat a file declaration as a directory`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("file")
    val component = component(null, "plugin", listOf(entry("lib/plugin.jar").copy(source = file.resolve("nested.jar").toString())))
    assertThatThrownBy {
      composeDevBuildComponents(listOf(component), tempDir.resolve("metadata"), sourceRunfiles = mapOf(file to "_main/file"))
    }.hasMessageContaining("undeclared source")
  }

  @Test
  fun `directory references reject sibling prefixes and escaping paths`(@TempDir tempDir: Path) {
    val directory = tempDir.resolve("plugin")
    for (source in listOf(tempDir.resolve("plugin-other/file.jar"), directory.resolve("../other.jar"), directory.resolve("lib/../plugin.jar"))) {
      val component = component(null, "plugin", listOf(entry("lib/plugin.jar").copy(source = source.toString())))
      assertThatThrownBy {
        composeDevBuildComponents(
          listOf(component), tempDir.resolve("metadata"), sourceRunfiles = emptyMap(), sourceDirectoryRunfiles = mapOf(directory to "_main/plugin"),
        )
      }.isInstanceOf(IllegalStateException::class.java)
    }
  }

  @Test
  fun `manifest-only links preserve their spelling without a payload tree`(@TempDir tempDir: Path) {
    val link = DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = "lib/../lib/plugin.jar")
    val component = component(null, "plugin", listOf(link))
    composeDevBuildComponents(listOf(component), tempDir.resolve("metadata"), sourceRunfiles = emptyMap())
    assertThat(Files.readString(tempDir.resolve("metadata/local-layout.json"))).contains("\"symlinkTarget\":\"lib/../lib/plugin.jar\"")
  }

  @Test
  fun `manifest-only links reject file sources and escapes`(@TempDir tempDir: Path) {
    val link = DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = "lib/plugin.jar")
    for (entry in listOf(link.copy(source = "ambiguous"), link.copy(symlinkTarget = "../../../outside"))) {
      assertThatThrownBy {
        composeDevBuildComponents(listOf(component(null, "plugin", listOf(entry))), tempDir.resolve("metadata"), sourceRunfiles = emptyMap())
      }.isInstanceOf(IllegalStateException::class.java)
    }
  }

  @Test
  fun `link chains cannot escape through a link to the distribution root`(@TempDir tempDir: Path) {
    val links = listOf(
      DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = "../.."),
      DevBuildComponentEntry("plugins/demo/escape", "symlink", 1, symlinkTarget = "current/../outside"),
    )
    assertThatThrownBy {
      composeDevBuildComponents(listOf(component(null, "plugin", links)), tempDir.resolve("metadata"), sourceRunfiles = emptyMap())
    }.hasMessageContaining("link chain escapes")
  }

  @Test
  fun `link cycles fail without reading payload files`(@TempDir tempDir: Path) {
    val links = listOf(
      DevBuildComponentEntry("plugins/demo/first", "symlink", 1, symlinkTarget = "second"),
      DevBuildComponentEntry("plugins/demo/second", "symlink", 1, symlinkTarget = "first"),
    )
    assertThatThrownBy {
      composeDevBuildComponents(listOf(component(null, "plugin", links)), tempDir.resolve("metadata"), sourceRunfiles = emptyMap())
    }.hasMessageContaining("link cycle")
  }

  @Test
  fun `case and Unicode aliases cannot bypass link containment`(@TempDir tempDir: Path) {
    for ((name, alias) in listOf("current" to "CURRENT", "currént" to "curre\u0301nt", "σ" to "ς", "straẞe" to "STRASSE")) {
      val links = listOf(
        DevBuildComponentEntry("plugins/demo/$name", "symlink", 1, symlinkTarget = "../.."),
        DevBuildComponentEntry("plugins/demo/escape", "symlink", 1, symlinkTarget = "$alias/../outside"),
      )
      assertThatThrownBy {
        composeDevBuildComponents(listOf(component(null, "plugin", links)), tempDir.resolve("metadata"), sourceRunfiles = emptyMap())
      }.hasMessageContaining("link chain escapes")
    }
  }

  @Test
  fun `repeated acyclic links use cached resolutions`() {
    val links = ArrayList<DevBuildComponentEntry>()
    links.add(DevBuildComponentEntry("link0", "symlink", 1, symlinkTarget = "."))
    for (index in 1..100) {
      links.add(DevBuildComponentEntry("link$index", "symlink", 1, symlinkTarget = "link${index - 1}/link${index - 1}"))
    }
    validateDevBuildLinkGraph(links)
  }

  @Test
  fun `link graph validation keeps noncanonical target spelling in launch metadata`(@TempDir tempDir: Path) {
    val link = DevBuildComponentEntry("plugins/demo/current", "symlink", 1, symlinkTarget = "lib//payload/")
    composeDevBuildComponents(listOf(component(null, "plugin", listOf(link))), tempDir.resolve("metadata"), sourceRunfiles = emptyMap())
    assertThat(Files.readString(tempDir.resolve("metadata/local-layout.json"))).contains("\"symlinkTarget\":\"lib//payload/\"")
  }

  @Test
  fun `local composition rejects unsafe and conflicting paths`(@TempDir tempDir: Path) {
    val root = tempDir.resolve("tree")
    val cases = listOf(
      listOf(entry("../outside")),
      listOf(entry("/absolute")),
      listOf(entry("dir/../outside")),
      listOf(entry("lib/app.jar"), entry("lib/app.jar")),
      listOf(entry("lib/café.jar"), entry("lib/cafe\u0301.jar")),
      listOf(entry("lib/first.jar"), entry("Lib/second.jar")),
      listOf(entry("café/first.jar"), entry("cafe\u0301/second.jar")),
      listOf(entry("lib"), entry("lib/app.jar")),
      listOf(entry("fingerprint.txt")),
      listOf(entry("local-layout.json")),
      listOf(entry("lib/link").copy(symlinkTarget = "")),
      listOf(entry("lib/link").copy(symlinkTarget = "../../outside")),
    )
    for (entries in cases) {
      assertThatThrownBy {
        composeDevBuildComponents(
          listOf(component(root, "platform", entries)), tempDir.resolve("metadata"), sourceRunfiles = mapOf(root to "_main/tree"),
        )
      }.isInstanceOf(IllegalStateException::class.java)
    }
  }

  private fun entry(path: String): DevBuildComponentEntry {
    return DevBuildComponentEntry(relativePath = path, type = "component-file", hash = 1)
  }

  private fun component(root: Path?, kind: String, entries: List<DevBuildComponentEntry>): DevBuildComponent {
    return DevBuildComponent(
      root = root,
      manifest = DevBuildComponentManifest(
        kind = kind, platformPrefix = "idea", os = "linux", arch = "x64", additionalModules = emptyList(),
        mainClass = "com.intellij.idea.Main", coreClassPath = listOf("lib/app.jar"), entries = entries,
      ),
    )
  }
}
