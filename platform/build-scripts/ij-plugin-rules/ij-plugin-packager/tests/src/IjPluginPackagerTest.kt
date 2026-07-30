package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class IjPluginPackagerTest {
  @Test
  fun packagesPlugin(@TempDir tempDirectory: Path) {
    val inputDirectory = tempDirectory.resolve("input")
    directoryContent {
      zip("descriptor.jar") {
        dir("META-INF") {
          file("plugin.xml", "<idea-plugin/>")
        }
      }
      zip("content-one.jar") {
        file("one.txt", "one")
      }
      zip("content-two.jar") {
        file("two.txt", "two")
      }
    }.generate(inputDirectory)

    val outputDirectory = tempDirectory.resolve("output")
    IjPluginPackager.main(arrayOf(
      outputDirectory.toString(),
      "--descriptor_module",
      "descriptor:${inputDirectory.resolve("descriptor.jar")}",
      "--content_module",
      "content.one:${inputDirectory.resolve("content-one.jar")}",
      "--content_module",
      "content.two:${inputDirectory.resolve("content-two.jar")}",
    ))

    outputDirectory.assertMatches(directoryContent {
      dir("lib") {
        zip("descriptor.jar") {
          dir("META-INF") {
            file("plugin.xml", "<idea-plugin/>")
          }
        }
        dir("modules") {
          zip("content.one.jar") {
            file("one.txt", "one")
          }
          zip("content.two.jar") {
            file("two.txt", "two")
          }
        }
      }
    })
  }
}
