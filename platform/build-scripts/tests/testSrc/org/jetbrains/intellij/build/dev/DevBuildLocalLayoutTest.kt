package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class DevBuildLocalLayoutTest {
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
  fun `local composition rejects unsafe and conflicting paths`(@TempDir tempDir: Path) {
    val root = tempDir.resolve("tree")
    val cases = listOf(
      listOf(entry("../outside")),
      listOf(entry("/absolute")),
      listOf(entry("dir/../outside")),
      listOf(entry("lib/app.jar"), entry("lib/app.jar")),
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
