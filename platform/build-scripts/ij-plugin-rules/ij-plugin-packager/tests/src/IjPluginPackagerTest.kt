package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class IjPluginPackagerTest {
  @Test
  fun packagesPlugin(@TempDir tempDirectory: Path) {
    val pluginXml = """
      <idea-plugin>
        <content>
          <module name="embedded.module" loading="embedded"/>
          <module name="optional.module"/>
        </content>
      </idea-plugin>
    """.trimIndent()
    val inputDirectory = tempDirectory.resolve("input")
    directoryContent {
      zip("descriptor.jar") {
        dir("META-INF") {
          file("plugin.xml", pluginXml)
        }
      }
      zip("embedded-module.jar") {
        file("one.txt", "one")
      }
      zip("optional-module.jar") {
        file("two.txt", "two")
      }
    }.generate(inputDirectory)

    val outputDirectory = tempDirectory.resolve("output")
    IjPluginPackager.main(arrayOf(
      outputDirectory.toString(),
      "--descriptor_module",
      "descriptor:${inputDirectory.resolve("descriptor.jar")}",
      "--content_module",
      "embedded.module:${inputDirectory.resolve("embedded-module.jar")}",
      "--content_module",
      "optional.module:${inputDirectory.resolve("optional-module.jar")}",
    ))

    outputDirectory.assertMatches(directoryContent {
      dir("lib") {
        zip("descriptor.jar") {
          dir("META-INF") {
            file("plugin.xml", pluginXml)
          }
        }
        zip("embedded.module.jar") {
          file("one.txt", "one")
        }
        dir("modules") {
          zip("optional.module.jar") {
            file("two.txt", "two")
          }
        }
      }
    })
  }
}
