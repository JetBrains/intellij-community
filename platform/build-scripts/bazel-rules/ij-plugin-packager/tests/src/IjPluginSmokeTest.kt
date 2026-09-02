package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.platform.bazel.runfiles.BazelRunfiles
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

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
  }

  private fun assertFilesExist(directory: Path, relativePaths: List<String>) {
    for (relativePath in relativePaths) {
      val file = directory.resolve(relativePath)
      assertTrue(Files.isRegularFile(file), "$relativePath is missing from $directory")
    }
  }
}
