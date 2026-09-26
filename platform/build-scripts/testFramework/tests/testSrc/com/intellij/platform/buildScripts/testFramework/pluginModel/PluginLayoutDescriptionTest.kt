package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class PluginLayoutDescriptionTest {
  @Test
  fun `standalone module library files retain their owner without adding module output`() {
    val requests = ArrayList<Pair<String, String?>>()
    val layout = toPluginLayoutDescription(
      entries = listOf(
        FileEntry(name = "lib/plugin.jar", modules = listOf(ModuleEntry(name = "demo.plugin"))),
        FileEntry(name = "lib/project.jar", library = "shared"),
        FileEntry(name = "lib/module.jar", library = "shared", module = "demo.owner"),
        FileEntry(name = "lib/other.jar", library = "shared", module = "demo.owner"),
        FileEntry(name = "lib/anonymous.jar", library = "anonymous.jar", module = "demo.owner"),
      ),
      mainModuleName = "demo.plugin",
      pluginDescriptorPath = "META-INF/plugin.xml",
      mainLibDir = "lib",
      jarsToIgnore = emptySet(),
      libraryRootResolver = { library, module ->
        requests.add(library to module)
        listOf(Path.of("${module ?: "project"}.jar"))
      },
    )

    assertThat(requests).containsExactly("shared" to null, "shared" to "demo.owner")
    assertThat(layout.libraryRootsInClasspath).containsExactly(Path.of("project.jar"), Path.of("demo.owner.jar"))
    assertThat(layout.jpsModulesInClasspath).containsExactly("demo.plugin")
  }
}
