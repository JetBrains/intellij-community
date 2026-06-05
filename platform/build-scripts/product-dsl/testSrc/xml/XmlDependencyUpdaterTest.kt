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
  fun `inside section generated dependency wins over duplicate outside region`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>
        <dependencies>
          <plugin id="dup.plugin"/>
          <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
          <plugin id="dup.plugin"/>
          <!-- endregion -->
        </dependencies>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("META-INF/plugin.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = emptyList(),
      pluginDependencies = listOf("dup.plugin"),
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).containsOnlyOnce("<plugin id=\"dup.plugin\"/>")
    assertThat(xml.indexOf("<plugin id=\"dup.plugin\"/>")).isGreaterThan(xml.indexOf("<!-- region Generated dependencies"))
  }

  @Test
  fun `inside section keeps first duplicate outside region`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>
        <dependencies>
          <module name="manual.dep"/>
          <module name="manual.dep"/>
          <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
          <module name="generated.dep"/>
          <!-- endregion -->
        </dependencies>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("META-INF/plugin.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = listOf("generated.dep"),
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).containsOnlyOnce("<module name=\"manual.dep\"/>")
    assertThat(xml).containsOnlyOnce("<module name=\"generated.dep\"/>")
    assertThat(xml.indexOf("<module name=\"manual.dep\"/>")).isLessThan(xml.indexOf("<!-- region Generated dependencies"))
  }

  @Test
  fun `wrapped section keeps single preserved module dependency when also generated`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>
        <dependencies>
          <module name="manual.dep"/>
          <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
          <module name="manual.dep"/>
          <!-- endregion -->
        </dependencies>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("resources/intellij.sample.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = listOf("manual.dep", "intellij.new.auto"),
      preserveExistingModule = { it == "manual.dep" },
      allowInsideSectionRegion = false,
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).containsOnlyOnce("<module name=\"manual.dep\"/>")
    assertThat(xml).contains("<module name=\"intellij.new.auto\"/>")
  }

  @Test
  fun `legacy section keeps single preserved plugin dependency when also generated`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>
        <dependencies>
          <plugin id="dup.plugin"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("META-INF/plugin.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = emptyList(),
      pluginDependencies = listOf("dup.plugin", "new.plugin"),
      preserveExistingPlugin = { it == "dup.plugin" },
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).containsOnlyOnce("<plugin id=\"dup.plugin\"/>")
    assertThat(xml).contains("<plugin id=\"new.plugin\"/>")
  }

  @Test
  fun `preserves placeholder comment when transitioning from no region to inside region`(@TempDir tempDir: Path) {
    // Repro of IJI-2993-style regression: a plugin.xml with hand-authored placeholder comments
    // and an explicit <dependencies> section but no generated region was rewritten by the
    // generator, which dropped every comment along with the rebuilt block.
    val content = """
      <idea-plugin>
        <id>org.example</id>
        <name>Example</name>
        <dependencies>
          <!-- IJ/AS-DEPENDENCY-PLACEHOLDER -->
          <plugin id="com.intellij.java"/>
          <module name="intellij.platform.collaborationTools"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("META-INF/plugin.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = listOf("intellij.java.backend"),
      pluginDependencies = emptyList(),
      preserveExistingPlugin = { it == "com.intellij.java" },
      preserveExistingModule = { it == "intellij.platform.collaborationTools" },
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).contains("<!-- IJ/AS-DEPENDENCY-PLACEHOLDER -->")
    assertThat(xml).contains("<plugin id=\"com.intellij.java\"/>")
    assertThat(xml).contains("<module name=\"intellij.platform.collaborationTools\"/>")
    assertThat(xml).contains("<module name=\"intellij.java.backend\"/>")
    assertThat(xml.indexOf("<!-- IJ/AS-DEPENDENCY-PLACEHOLDER -->"))
      .isLessThan(xml.indexOf("<!-- region Generated dependencies"))
  }

  @Test
  fun `preserves placeholder comment inside wrapped generated dependencies region`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>
        <id>org.example</id>
        <name>Example</name>

        <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
        <dependencies>
          <!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->
          <module name="intellij.rider"/>
        </dependencies>
        <!-- endregion -->
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("META-INF/plugin.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = listOf("intellij.java.ui", "intellij.rider"),
      pluginDependencies = emptyList(),
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).contains("<!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->")
    assertThat(xml).contains("<module name=\"intellij.java.ui\"/>")
    assertThat(xml).contains("<module name=\"intellij.rider\"/>")
    assertThat(xml.indexOf("<!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->"))
      .isLessThan(xml.indexOf("<module name=\"intellij.java.ui\"/>"))
  }

  @Test
  fun `does not duplicate or re-emit fold region markers as comments`(@TempDir tempDir: Path) {
    val content = """
      <idea-plugin>
        <id>org.example</id>
        <name>Example</name>
        <dependencies>
          <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
          <module name="intellij.platform.debugger.impl.ui"/>
          <!-- endregion -->
        </dependencies>
      </idea-plugin>
    """.trimIndent()

    val updater = DeferredFileUpdater(tempDir)
    val path = tempDir.resolve("META-INF/plugin.xml")
    updateXmlDependencies(
      path = path,
      content = content,
      moduleDependencies = listOf("intellij.platform.debugger.impl.ui", "intellij.platform.collaborationTools"),
      pluginDependencies = emptyList(),
      strategy = updater,
    )

    val xml = updater.getDiffs().single().expectedContent
    assertThat(xml).containsOnlyOnce("<!-- region Generated dependencies")
    assertThat(xml).containsOnlyOnce("<!-- endregion -->")
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
