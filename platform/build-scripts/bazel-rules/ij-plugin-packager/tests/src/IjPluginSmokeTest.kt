package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.platform.bazel.runfiles.BazelRunfiles
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
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
    pluginDirectory.assertMatches(directoryContent {
      dir("lib") {
        file("ijPluginPackagerSmoke.jar")
        file("intellij.ijPluginPackagerSmoke.embedded.jar")
        dir("modules") {
          file("intellij.ijPluginPackagerSmoke.library.jar")
          file("intellij.ijPluginPackagerSmoke.optional.jar")
        }
      }
      dir("descriptor-data-dir") {
        dir("subdir") {
          file("second.txt", "second")
        }
        file("first.txt", "first")
        file("third.bin", "third")
      }
      dir("grouped-data") {
        dir("descriptor-data-txt-files") {
          dir("subdir") {
            file("second.txt", "second")
          }
          file("first.txt", "first")
        }
        dir("files") {
          dir("new-parent") {
            file("first.txt", "first")
          }
          file("third-renamed.txt", "third")
        }
      }
      dir("modules") {
        dir("intellij.ijPluginPackagerSmoke.library") {
          file("library-data.txt", "library data")
        }
      }
      dir("subdir") {
        file("second.txt", "second")
      }
      file("first.txt", "first")
      file("third.bin", "third")
    })

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
