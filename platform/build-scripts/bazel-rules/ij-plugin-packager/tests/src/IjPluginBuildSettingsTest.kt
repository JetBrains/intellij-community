package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal class IjPluginBuildSettingsTest {
  @Test
  fun generatesPluginXmlAccordingToBuildSettings() {
    val defaultBuildNumber = resolveRunfile("ij.plugin.packager.test.build-number-file").readText().trim()
    assertPluginDescriptor(
      configuration = "default",
      expectedVersion = defaultBuildNumber.substringBefore('.') + ".99999999.0",
      expectedSinceBuild = defaultBuildNumber,
      expectedUntilBuild = defaultBuildNumber,
    )
    assertPluginDescriptor(
      configuration = "nightly",
      expectedVersion = "263.12345",
      expectedSinceBuild = "263.12345",
      expectedUntilBuild = "263.*",
    )
    assertPluginDescriptor(
      configuration = "release",
      expectedVersion = "1.2.3",
      expectedSinceBuild = "263.12345",
      expectedUntilBuild = "263.*",
    )
    assertPluginDescriptor(
      configuration = "exact",
      expectedVersion = "263.12345.67",
      expectedSinceBuild = "263.12345.67",
      expectedUntilBuild = "263.12345.67",
    )
    assertPluginDescriptor(
      configuration = "restricted.range.in.release",
      expectedVersion = "263.12345.67",
      expectedSinceBuild = "263.12345.67",
      expectedUntilBuild = "263.12345.67",
    )
  }

  private fun assertPluginDescriptor(
    configuration: String,
    expectedVersion: String,
    expectedSinceBuild: String,
    expectedUntilBuild: String,
  ) {
    val rootElement = loadPluginDescriptor(resolveRunfile("ij.plugin.packager.test.$configuration"))
    assertEquals(expectedVersion, rootElement.getChildText("version"), "$configuration plugin version")

    val ideaVersion = rootElement.getChild("idea-version")
    assertNotNull(ideaVersion, "$configuration idea-version element")
    assertEquals(expectedSinceBuild, ideaVersion!!.getAttributeValue("since-build"), "$configuration since-build")
    assertEquals(expectedUntilBuild, ideaVersion.getAttributeValue("until-build"), "$configuration until-build")
  }

  private fun loadPluginDescriptor(pluginDirectory: Path): Element {
    val descriptorJar = pluginDirectory.resolve("lib/ijPluginPackagerSmoke.jar")
    FileSystems.newFileSystem(descriptorJar).use { zipFileSystem ->
      val descriptorFile = zipFileSystem.getPath("META-INF/plugin.xml")
      require(Files.exists(descriptorFile)) {
        "META-INF/plugin.xml is missing from $descriptorJar"
      }
      return JDOMUtil.load(descriptorFile)
    }
  }

  private fun resolveRunfile(propertyName: String): Path {
    val runfilePath = requireNotNull(System.getProperty(propertyName)) { "System property ${propertyName} is not set" }
    return BazelRunfiles.resolveRunfilePath(runfilePath)
  }
}
