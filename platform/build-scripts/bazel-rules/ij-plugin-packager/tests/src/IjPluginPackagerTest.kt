package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class IjPluginPackagerTest {
  @Test
  fun packagesPlugin(@TempDir tempDirectory: Path) {
    val pluginXml = """
      <idea-plugin>
        <id>my.plugin</id>
        <description><![CDATA[long < description > of the plugin]]></description>
        <content>
          <module name="embedded.module" loading="embedded"/>
          <module name="optional.module"/>
        </content>
      </idea-plugin>
    """.trimIndent()
    val inputDirectory = tempDirectory.resolve("input")
    val optionalModuleXml = """
      <idea-plugin>
        <actions>
          <action id="foo" class="Foo"/>
        </actions>
      </idea-plugin>
    """.trimIndent()
    directoryContent {
      zip("descriptor.jar") {
        file("icon-robots.txt", "")
        dir("META-INF") {
          file("plugin.xml", pluginXml)
        }
      }
      zip("embedded-module.jar") {
        dir("subdir") {
          file("icon-robots.txt", "")
        }
        file("embedded.module.xml", "<idea-plugin></idea-plugin>")
      }
      zip("optional-module.jar") {
        file("optional.module.xml", optionalModuleXml)
      }
    }.generate(inputDirectory)

    val outputDirectory = tempDirectory.resolve("output")
    IjPluginPackager.main(arrayOf(
      outputDirectory.toString(),
      "--plugin_content_yaml",
      outputDirectory.resolve("plugin-content.yaml").toString(),
      "--descriptor_module",
      "descriptor:${inputDirectory.resolve("descriptor.jar")}",
      "--content_module",
      "embedded.module:${inputDirectory.resolve("embedded-module.jar")}",
      "--content_module",
      "optional.module:${inputDirectory.resolve("optional-module.jar")}",
    ))

    val expectedPluginXml = """
      <idea-plugin>
        <id>my.plugin</id>
        <description><![CDATA[long < description > of the plugin]]></description>
        <content>
          <module name="embedded.module" loading="embedded"><![CDATA[<idea-plugin />]]></module>
          <module name="optional.module"><![CDATA[<idea-plugin>
        <actions>
          <action id="foo" class="Foo" />
        </actions>
      </idea-plugin>]]></module>
        </content>
      </idea-plugin>
    """.trimIndent()
    outputDirectory.assertMatches(directoryContent {
      file("plugin-content.yaml", """
        - name: lib/descriptor.jar
          modules:
          - name: descriptor
        - name: lib/embedded.module.jar
          contentModules:
          - name: embedded.module
        - name: lib/modules/optional.module.jar
          contentModules:
          - name: optional.module
      """.trimIndent())
      dir("lib") {
        zip("descriptor.jar") {
          file("__index__")
          dir("META-INF") {
            file("plugin.xml", expectedPluginXml)
          }
        }
        zip("embedded.module.jar") {
          file("__index__")
          file("embedded.module.xml", "<idea-plugin></idea-plugin>")
        }
        dir("modules") {
          zip("optional.module.jar") {
            file("__index__")
            file("optional.module.xml", optionalModuleXml)
          }
        }
      }
    })
  }

  @Test
  fun doesNotGeneratePluginContentYamlIfOptionIsNotSpecified(@TempDir tempDirectory: Path) {
    val inputDirectory = tempDirectory.resolve("input")
    directoryContent {
      zip("descriptor.jar") {
        dir("META-INF") {
          file("plugin.xml", "<idea-plugin><id>my.plugin</id></idea-plugin>")
        }
      }
    }.generate(inputDirectory)

    val outputDirectory = tempDirectory.resolve("output")
    IjPluginPackager.main(arrayOf(
      outputDirectory.toString(),
      "--descriptor_module",
      "descriptor:${inputDirectory.resolve("descriptor.jar")}",
    ))

    assertFalse(Files.exists(outputDirectory.resolve("plugin-content.yaml")))
  }
}
