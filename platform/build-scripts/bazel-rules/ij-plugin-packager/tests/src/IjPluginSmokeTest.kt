package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.platform.bazel.runfiles.BazelRunfiles
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import com.intellij.util.io.directoryContentOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal class IjPluginSmokeTest {
  @Test
  fun smokePluginContainsKeyFiles() {
    val propertyName = "ij.plugin.packager.test.smoke"
    val runfilePath = requireNotNull(System.getProperty(propertyName)) { "System property $propertyName is not set" }
    val pluginDirectory = BazelRunfiles.resolveRunfilePath(runfilePath)
    val expectedFiles = listOf(
      "lib/ijPluginPackagerSmoke.jar",
      "lib/intellij.ijPluginPackagerSmoke.embedded.jar",
      "lib/modules/intellij.ijPluginPackagerSmoke.library.jar",
      "lib/modules/intellij.ijPluginPackagerSmoke.optional.jar",
    )
    assertFilesExist(pluginDirectory, expectedFiles)

    val libraryJar = pluginDirectory.resolve("lib/modules/intellij.ijPluginPackagerSmoke.library.jar")
    FileSystems.newFileSystem(libraryJar).use { zipFileSystem ->
      assertFilesExist(
        zipFileSystem.getPath("/"),
        listOf(
          "org/objectweb/asm/ClassReader.class",
          "org/objectweb/asm/tree/ClassNode.class",
          "org/objectweb/asm/tree/analysis/Analyzer.class",
          "org/assertj/core/api/Assertions.class",
        ),
      )
    }

    pluginDirectory.resolve("descriptor-data-dir").assertMatches(directoryContent {
      dir("subdir") {
        file("second.txt", "second")
      }
      file("first.txt", "first")
      file("third.bin", "third")
    })
    pluginDirectory.resolve("descriptor-data-txt-files").assertMatches(directoryContent {
      dir("subdir") {
        file("second.txt", "second")
      }
      file("first.txt", "first")
    })
    assertEquals("third", pluginDirectory.resolve("third-renamed.txt").readText())
    assertEquals("first", pluginDirectory.resolve("new-parent/first.txt").readText())
    assertEquals(
      "library data",
      pluginDirectory.resolve("modules/intellij.ijPluginPackagerSmoke.library/library-data.txt").readText(),
    )
  }

  private fun assertFilesExist(directory: Path, relativePaths: List<String>) {
    for (relativePath in relativePaths) {
      val file = directory.resolve(relativePath)
      assertTrue(Files.isRegularFile(file), "$relativePath is missing from $directory")
    }
  }
}
