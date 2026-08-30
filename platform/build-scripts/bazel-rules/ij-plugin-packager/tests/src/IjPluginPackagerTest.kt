package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.runBlocking
import org.jetbrains.bazel.jvm.WorkRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

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

    // paths are relative to the base directory of the request, like they are when the packager runs as a worker
    val outputDirectory = tempDirectory.resolve("output")
    IjPluginPackager.packPlugin(
      args = listOf(
        "output",
        "--packed_modules",
        "output/packed-modules.yaml",
        "--descriptor_module",
        "descriptor:input/descriptor.jar",
        "--content_module",
        "embedded.module:input/embedded-module.jar",
        "--content_module",
        "optional.module:input/optional-module.jar",
      ),
      baseDir = tempDirectory,
    )

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
      file("packed-modules.yaml", """
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
  fun doesNotGeneratePackedModulesIfOptionIsNotSpecified(@TempDir tempDirectory: Path) {
    val inputDirectory = tempDirectory.resolve("input")
    directoryContent {
      zip("descriptor.jar") {
        dir("META-INF") {
          file("plugin.xml", "<idea-plugin><id>my.plugin</id></idea-plugin>")
        }
      }
    }.generate(inputDirectory)

    val outputDirectory = tempDirectory.resolve("output")
    IjPluginPackager.packPlugin(
      args = listOf(
        "output",
        "--descriptor_module",
        "descriptor:input/descriptor.jar",
      ),
      baseDir = tempDirectory,
    )

    assertTrue(Files.exists(outputDirectory.resolve("lib/descriptor.jar")))
    assertFalse(Files.exists(outputDirectory.resolve("packed-modules.yaml")))
  }

  @Test
  fun readsArgumentsFromParamsFile(@TempDir tempDirectory: Path) {
    val inputDirectory = tempDirectory.resolve("input")
    directoryContent {
      zip("descriptor.jar") {
        dir("META-INF") {
          file("plugin.xml", "<idea-plugin><id>my.plugin</id></idea-plugin>")
        }
      }
    }.generate(inputDirectory)

    tempDirectory.resolve("packager.params").writeText("""
      output
      --descriptor_module
      descriptor:input/descriptor.jar
    """.trimIndent())

    val writer = StringWriter()
    val exitCode = runBlocking {
      IjPluginPackagerExecutor.execute(
        request = WorkRequest(
          arguments = arrayOf("--flagfile=packager.params"),
          inputs = emptyArray(),
          requestId = 0,
          cancel = false,
          verbosity = 0,
          sandboxDir = null,
        ),
        writer = writer,
        baseDir = tempDirectory,
        tracer = OpenTelemetry.noop().getTracer("test"),
      )
    }

    assertEquals(0, exitCode, writer.toString())
    assertTrue(Files.exists(tempDirectory.resolve("output/lib/descriptor.jar")))
  }

  @Test
  fun reportsErrorIfArgumentsAreNotPassedInParamsFile(@TempDir tempDirectory: Path) {
    val writer = StringWriter()
    val exitCode = runBlocking {
      IjPluginPackagerExecutor.execute(
        request = WorkRequest(
          arguments = arrayOf("output", "--descriptor_module", "descriptor:input/descriptor.jar"),
          inputs = emptyArray(),
          requestId = 0,
          cancel = false,
          verbosity = 0,
          sandboxDir = null,
        ),
        writer = writer,
        baseDir = tempDirectory,
        tracer = OpenTelemetry.noop().getTracer("test"),
      )
    }

    assertEquals(3, exitCode)
    assertTrue(writer.toString().contains("--flagfile="), writer.toString())
  }
}
