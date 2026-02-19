// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.xml

import com.intellij.platform.pluginGraph.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class XmlDependencyUpdaterTest {
  @Test
  fun `inserts dependencies after metadata`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>
        <name>Test Plugin</name>
        <id>test.plugin</id>
        <description>Test description</description>

        <extensions defaultExtensionNs="com.intellij">
        </extensions>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("META-INF/plugin.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = emptyList(),
      pluginDependencies = listOf("dep.plugin"),
      strategy = updater,
    )

    val diffs = updater.getDiffs()
    assertThat(diffs).hasSize(1)
    val xml = diffs.single().expectedContent
    assertThat(xml.indexOf("<dependencies>")).isGreaterThan(xml.indexOf("</description>"))
  }

  @Test
  fun `inserting generated dependencies preserves blank line count before next section`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>

        <extensions defaultExtensionNs="com.intellij">
        </extensions>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("META-INF/plugin.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = listOf("intellij.platform.backend"),
      pluginDependencies = emptyList(),
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).contains("<!-- endregion -->\n\n  <extensions")
    assertThat(xml).doesNotContain("<!-- endregion -->\n\n\n  <extensions")
  }

  @Test
  fun `non plugin descriptor with inside region rewrites full dependencies section`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>
        <dependencies>
          <module name="manual.dep"/>
          <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
          <module name="old.auto"/>
          <!-- endregion -->
        </dependencies>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("resources/intellij.sample.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = listOf("intellij.new.auto"),
      pluginDependencies = emptyList(),
      allowInsideSectionRegion = false,
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).contains("<!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->\n  <dependencies>")
    assertThat(xml).contains("<module name=\"intellij.new.auto\"/>")
    assertThat(xml).doesNotContain("<module name=\"manual.dep\"/>")
  }

  @Test
  fun `suppressed module is preserved for non plugin descriptor even with inside region`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>
        <dependencies>
          <module name="manual.dep"/>
          <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
          <module name="old.auto"/>
          <!-- endregion -->
        </dependencies>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("resources/intellij.sample.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = listOf("intellij.new.auto"),
      preserveExistingModule = { it == "manual.dep" },
      allowInsideSectionRegion = false,
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).contains("<module name=\"manual.dep\"/>")
    assertThat(xml).contains("<module name=\"intellij.new.auto\"/>")
  }

  @Test
  fun `removes duplicate legacy depends for modern plugin deps`() {
    val content = """
      <idea-plugin>
        <name>Test</name>
        <id>test</id>
        <depends>XPathView</depends>
        <depends optional="true">XPathView</depends>
        <depends>com.intellij.modules.xml</depends>
      </idea-plugin>
    """.trimIndent()

    val migrated = removeDuplicateLegacyDepends(content, setOf(PluginId("XPathView")))
    assertThat(migrated.content).doesNotContain("<depends>XPathView</depends>")
    assertThat(migrated.content).contains("<depends optional=\"true\">XPathView</depends>")
    assertThat(migrated.content).contains("<depends>com.intellij.modules.xml</depends>")
  }
}
