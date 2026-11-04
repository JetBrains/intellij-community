// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.runtimeModuleRepository

import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleId.DEFAULT_NAMESPACE
import com.intellij.platform.runtime.repository.RuntimeModuleId.legacyJpsModule
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl
import com.intellij.platform.runtime.repository.impl.RuntimePluginHeaderImpl
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class RuntimeModuleRepositoryMainTest {
  @TempDir
  lateinit var tempDirectory: Path

  private val projectDir: Path
    get() = tempDirectory.resolve("project")

  private val outputDir: Path
    get() = tempDirectory.resolve("out")

  @Test
  fun `generates the repository from the layout`() {
    writeProject()
    val args = writeInputs()

    generateRuntimeModuleRepositoryFromLayout(args)

    val coreId = RuntimeModuleId.contentModule("foo.core", DEFAULT_NAMESPACE)
    val expectedDescriptors = listOf(
      RawRuntimeModuleDescriptor.create(legacyJpsModule("foo.plugin"), listOf("../plugins/foo/lib/foo.jar"),
                                        listOf(RuntimeModuleId.pluginDescriptorModule("com.intellij.modules.platform"))),
      RawRuntimeModuleDescriptor.create(legacyJpsModule("foo.util"), listOf("../plugins/foo/lib/foo.jar"),
                                        listOf(RuntimeModuleId.contentModule("bar", DEFAULT_NAMESPACE), legacyJpsModule("baz"))),
      RawRuntimeModuleDescriptor.create(coreId, listOf("../plugins/foo/lib/modules/foo.core.jar"),
                                        listOf(legacyJpsModule("foo.util"), RuntimeModuleId.pluginDescriptorModule("com.intellij.modules.lang"))),
    )
    val expectedHeader = RuntimePluginHeaderImpl("com.foo", legacyJpsModule("foo.plugin"), listOf(
      IncludedRuntimeModuleImpl(legacyJpsModule("foo.plugin"), RuntimeModuleLoadingRule.EMBEDDED, null),
      IncludedRuntimeModuleImpl(legacyJpsModule("foo.util"), RuntimeModuleLoadingRule.EMBEDDED, null),
      IncludedRuntimeModuleImpl(coreId, RuntimeModuleLoadingRule.REQUIRED, null),
    ))

    val compactData = RuntimeModuleRepositorySerialization.loadFromCompactFile(outputDir.resolve(MODULE_DESCRIPTORS_COMPACT_PATH))
    assertThat(compactData.allModuleIds).containsExactlyInAnyOrderElementsOf(expectedDescriptors.map { it.moduleId })
    for (expected in expectedDescriptors) {
      assertThat(compactData.findDescriptor(expected.moduleId)).isEqualTo(expected)
    }
    assertThat(compactData.pluginHeaders).containsExactly(expectedHeader)

    val jarData = RuntimeModuleRepositorySerialization.loadFromJar(outputDir.resolve(MODULE_DESCRIPTORS_JAR_PATH))
    assertThat(jarData.allModuleIds).containsExactlyInAnyOrderElementsOf(expectedDescriptors.map { it.moduleId })
    for (expected in expectedDescriptors) {
      assertThat(jarData.findDescriptor(expected.moduleId)).isEqualTo(expected)
    }
  }

  @Test
  fun `the facts mark a dependency that has a module descriptor`() {
    writeProject()
    writeInputs()

    val facts = readRuntimeModuleFacts(tempDirectory.resolve("module-facts.json"))
    assertThat(facts.findModule("foo.util")!!.dependencies.map { it.module to it.contentModule }).containsExactly("bar" to true, "baz" to false)
    assertThat(facts.findModule("unknown")).isNull()
  }

  @Test
  fun `the module facts are required`() {
    writeProject()
    val args = writeInputs().filterNot { it.startsWith("--module-facts=") }

    assertThatThrownBy { generateRuntimeModuleRepositoryFromLayout(args) }
      .hasMessageContaining("--module-facts is required")
  }

  @Test
  fun `a separate content module descriptor is used if the plugin descriptor holds none`() {
    writeProject()
    val separateDescriptor = tempDirectory.resolve("foo.core.xml")
    separateDescriptor.writeText("""<idea-plugin visibility="internal"/>""")
    val args = writeInputs(embedCoreDescriptor = false) + "--content-module-descriptor=foo.core=$separateDescriptor"

    generateRuntimeModuleRepositoryFromLayout(args)

    val coreId = RuntimeModuleId.contentModule("foo.core", DEFAULT_NAMESPACE)
    val compactData = RuntimeModuleRepositorySerialization.loadFromCompactFile(outputDir.resolve(MODULE_DESCRIPTORS_COMPACT_PATH))
    assertThat(compactData.findDescriptor(coreId)).isEqualTo(
      RawRuntimeModuleDescriptor.create(coreId, RuntimeModuleVisibility.INTERNAL,
                                        listOf("../plugins/foo/lib/modules/foo.core.jar"), listOf(legacyJpsModule("foo.util")))
    )
  }

  @Test
  fun `a content module without a descriptor is an error`() {
    writeProject()
    val args = writeInputs(embedCoreDescriptor = false)

    assertThatThrownBy { generateRuntimeModuleRepositoryFromLayout(args) }
      .hasMessageContaining("Cannot find foo.core.xml descriptor for plugin.xml in foo.plugin")
  }

  @Test
  fun `a descriptor for a module outside the layout is an error`() {
    writeProject()
    val args = writeInputs() + "--descriptor=unknown.plugin=${tempDirectory.resolve("foo.plugin.xml")}"

    assertThatThrownBy { generateRuntimeModuleRepositoryFromLayout(args) }
      .hasMessageContaining("[unknown.plugin]")
  }

  /**
   * Writes a project with a plugin `foo.plugin` that bundles `foo.util` and the content module `foo.core`. `foo.util` depends on `bar`, which has a
   * module descriptor, and on `baz`, which has none. The plugin `suppressed.plugin` is suppressed by the IDE properties.
   */
  private fun writeProject() {
    writeModule("foo.plugin")
    writeModule("foo.util", "bar", "baz")
    writeModule("foo.core", "foo.util")
    writeModule("bar")
    projectDir.resolve("bar/src/bar.xml").writeText("<idea-plugin/>")
    writeModule("baz")
    writeModule("suppressed.plugin")
    val modules = listOf("foo.plugin", "foo.util", "foo.core", "bar", "baz", "suppressed.plugin").joinToString("\n") {
      $$"""      <module fileurl="file://$PROJECT_DIR$/$${it}/$${it}.iml" filepath="$PROJECT_DIR$/$${it}/$${it}.iml" />"""
    }
    projectDir.resolve(".idea").createDirectories()
    projectDir.resolve(".idea/modules.xml").writeText("""
      |<?xml version="1.0" encoding="UTF-8"?>
      |<project version="4">
      |  <component name="ProjectModuleManager">
      |    <modules>
      |$modules
      |    </modules>
      |  </component>
      |</project>
    """.trimMargin())
  }

  private fun writeModule(name: String, vararg dependencies: String) {
    val moduleDir = projectDir.resolve(name)
    moduleDir.resolve("src").createDirectories()
    val dependencyEntries = dependencies.joinToString("") { "\n    <orderEntry type=\"module\" module-name=\"$it\" />" }
    moduleDir.resolve("$name.iml").writeText($$"""
      |<?xml version="1.0" encoding="UTF-8"?>
      |<module type="JAVA_MODULE" version="4">
      |  <component name="NewModuleRootManager" inherit-compiler-output="true">
      |    <exclude-output />
      |    <content url="file://$MODULE_DIR$">
      |      <sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />
      |    </content>
      |    <orderEntry type="inheritedJdk" />
      |    <orderEntry type="sourceFolder" forTests="false" />$${dependencyEntries}
      |  </component>
      |</module>
    """.trimMargin())
  }

  private fun writeInputs(embedCoreDescriptor: Boolean = true): List<String> {
    val layout = RuntimeModuleRepositoryLayout(plugins = listOf(
      RuntimeModuleRepositoryPluginLayout(
        descriptorModule = "foo.plugin",
        entries = listOf(
          moduleOutput("foo.plugin", "plugins/foo/lib/foo.jar", "foo.jar"),
          moduleOutput("foo.util", "plugins/foo/lib/foo.jar", "foo.jar"),
          moduleOutput("foo.core", "plugins/foo/lib/modules/foo.core.jar", "modules/foo.core.jar"),
        ),
      ),
      RuntimeModuleRepositoryPluginLayout(
        descriptorModule = "suppressed.plugin",
        entries = listOf(moduleOutput("suppressed.plugin", "plugins/suppressed/lib/suppressed.jar", "suppressed.jar")),
      ),
    ))
    val layoutFile = tempDirectory.resolve("layout.json")
    writeRuntimeModuleRepositoryLayout(layout, layoutFile)

    val coreDescriptor = if (embedCoreDescriptor) {
      """<![CDATA[<idea-plugin visibility="public"><dependencies><plugin id="com.intellij.modules.lang"/></dependencies></idea-plugin>]]>"""
    }
    else ""
    val fooDescriptor = tempDirectory.resolve("foo.plugin.xml")
    fooDescriptor.writeText("""
      |<idea-plugin>
      |  <id>com.foo</id>
      |  <dependencies>
      |    <plugin id="com.intellij.modules.platform"/>
      |  </dependencies>
      |  <content namespace="jetbrains">
      |    <module name="foo.core" loading="required">$coreDescriptor</module>
      |  </content>
      |</idea-plugin>
    """.trimMargin())
    val suppressedDescriptor = tempDirectory.resolve("suppressed.plugin.xml")
    suppressedDescriptor.writeText("<idea-plugin><id>com.suppressed</id></idea-plugin>")
    val ideProperties = tempDirectory.resolve("idea.properties")
    ideProperties.writeText("idea.suppressed.plugins.set.selector=test\nidea.suppressed.plugins.set.test=com.other, com.suppressed\n")

    // one option comes from an argument file, as a Bazel action passes them
    val argumentFile = tempDirectory.resolve("arguments.txt")
    Files.write(argumentFile, listOf("--descriptor=suppressed.plugin=$suppressedDescriptor", "--ide-properties=$ideProperties"))
    val factsFile = tempDirectory.resolve("module-facts.json")
    writeRuntimeModuleFacts(JpsRuntimeModuleModel(loadProject(projectDir)).toFactsTable(), factsFile)
    return listOf(
      "--module-facts=$factsFile",
      "--layout=$layoutFile",
      "--descriptor=foo.plugin=$fooDescriptor",
      "@$argumentFile",
      "--output-dir=$outputDir",
    )
  }

  private fun moduleOutput(moduleName: String, path: String, relativeOutputFile: String): PluginDistributionEntry {
    return PluginDistributionEntry(PluginDistributionEntry.Kind.MODULE_OUTPUT, moduleName, path, relativeOutputFile)
  }
}
