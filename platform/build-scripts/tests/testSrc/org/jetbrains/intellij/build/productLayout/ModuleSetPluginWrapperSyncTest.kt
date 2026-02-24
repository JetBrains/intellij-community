// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val PROJECT_DIR_MACRO: String = "$" + "PROJECT_DIR" + "$"
private const val NEW_GENERATED_ROOT: String = "community/module-set-plugins/generated"
private const val MAIN_MODULE_NAME: String = "intellij.moduleSet.plugin.main"
private const val LEGACY_GENERATED_ROOT: String = "community/platform/platform-resources/generated/module-set-plugins"
private const val LEGACY_GENERATED_ROOT_IN_COMMUNITY_MODULES_XML: String = "platform/platform-resources/generated/module-set-plugins"

class ModuleSetPluginWrapperSyncTest {
  @TempDir
  lateinit var projectRoot: Path

  @Test
  fun `syncs both modules xml files with module-name sort and no trailing newline`() {
    val rootGeneratedWrapperPath =
      "$PROJECT_DIR_MACRO/$NEW_GENERATED_ROOT/intellij.moduleSet.plugin.recentFiles/intellij.moduleSet.plugin.recentFiles.iml"
    val rootGeneratedMainPath =
      "$PROJECT_DIR_MACRO/$NEW_GENERATED_ROOT/$MAIN_MODULE_NAME/$MAIN_MODULE_NAME.iml"
    val communityGeneratedWrapperPath =
      "$PROJECT_DIR_MACRO/module-set-plugins/generated/intellij.moduleSet.plugin.recentFiles/intellij.moduleSet.plugin.recentFiles.iml"
    val communityGeneratedMainPath =
      "$PROJECT_DIR_MACRO/module-set-plugins/generated/$MAIN_MODULE_NAME/$MAIN_MODULE_NAME.iml"
    val legacyRootStalePath =
      "$PROJECT_DIR_MACRO/$LEGACY_GENERATED_ROOT/intellij.moduleSet.plugin.recentFiles/stale.iml"
    val legacyCommunityStalePath =
      "$PROJECT_DIR_MACRO/$LEGACY_GENERATED_ROOT_IN_COMMUNITY_MODULES_XML/intellij.moduleSet.plugin.recentFiles/stale.iml"

    val rootModulesXml = projectRoot.resolve(".idea/modules.xml")
    writeModulesXml(
      rootModulesXml,
      moduleFilepaths = listOf(
        "$PROJECT_DIR_MACRO/zzz/intellij.alpha.iml",
        legacyRootStalePath,
        "$PROJECT_DIR_MACRO/aaa/intellij.zulu.iml",
      ),
    )

    val communityModulesXml = projectRoot.resolve("community/.idea/modules.xml")
    writeModulesXml(
      communityModulesXml,
      moduleFilepaths = listOf(
        "$PROJECT_DIR_MACRO/zzz/intellij.alpha.iml",
        legacyCommunityStalePath,
        "$PROJECT_DIR_MACRO/aaa/intellij.zulu.iml",
      ),
    )

    syncModuleSetPluginsOnDisk(projectRoot = projectRoot, moduleSets = listOf(recentFilesModuleSet()))

    val wrapperImlText = wrapperPath()
      .resolve("intellij.moduleSet.plugin.recentFiles.iml")
      .readText()
    assertThat(wrapperImlText).doesNotEndWith("\n")

    val rootXmlText = rootModulesXml.readText()
    val rootPaths = moduleFilepaths(rootXmlText)
    assertThat(rootPaths)
      .contains(rootGeneratedWrapperPath)
      .contains(rootGeneratedMainPath)
    assertThat(rootPaths)
      .containsExactly(
        "$PROJECT_DIR_MACRO/zzz/intellij.alpha.iml",
        rootGeneratedMainPath,
        rootGeneratedWrapperPath,
        "$PROJECT_DIR_MACRO/aaa/intellij.zulu.iml",
      )
    assertThat(rootPaths).doesNotContain(legacyRootStalePath)
    assertThat(rootXmlText).doesNotEndWith("\n")

    val communityXmlText = communityModulesXml.readText()
    val communityPaths = moduleFilepaths(communityXmlText)
    assertThat(communityPaths)
      .contains(communityGeneratedWrapperPath)
      .contains(communityGeneratedMainPath)
    assertThat(communityPaths)
      .containsExactly(
        "$PROJECT_DIR_MACRO/zzz/intellij.alpha.iml",
        communityGeneratedMainPath,
        communityGeneratedWrapperPath,
        "$PROJECT_DIR_MACRO/aaa/intellij.zulu.iml",
      )
    assertThat(communityPaths).doesNotContain(legacyCommunityStalePath)
    assertThat(communityXmlText).doesNotEndWith("\n")
  }

  @Test
  fun `generates wrapper without content dependencies and shared main runtime dependencies`() {
    syncModuleSetPluginsOnDisk(projectRoot = projectRoot, moduleSets = listOf(recentFilesModuleSet()))

    val wrapperImlText = wrapperPath().resolve("intellij.moduleSet.plugin.recentFiles.iml").readText()
    assertThat(wrapperImlText)
      .doesNotContain("module-name=\"intellij.platform.recentFiles\"")
      .doesNotContain("module-name=\"intellij.platform.recentFiles.frontend\"")
      .doesNotContain("module-name=\"intellij.platform.recentFiles.backend\"")

    val mainImlText = mainPath().resolve("$MAIN_MODULE_NAME.iml").readText()
    assertThat(mainImlText)
      .contains("<orderEntry type=\"module\" module-name=\"intellij.moduleSet.plugin.recentFiles\" scope=\"RUNTIME\" />")
      .contains("<orderEntry type=\"module\" module-name=\"intellij.platform.recentFiles\" scope=\"RUNTIME\" />")
      .contains("<orderEntry type=\"module\" module-name=\"intellij.platform.recentFiles.frontend\" scope=\"RUNTIME\" />")
      .contains("<orderEntry type=\"module\" module-name=\"intellij.platform.recentFiles.backend\" scope=\"RUNTIME\" />")
      .doesNotEndWith("\n")
  }

  @Test
  fun `generates wrapper plugin with literal name and without resource bundle`() {
    syncModuleSetPluginsOnDisk(projectRoot = projectRoot, moduleSets = listOf(recentFilesModuleSet()))

    val pluginXml = wrapperPath()
      .resolve("resources/META-INF/plugin.xml")
      .readText()

    assertThat(pluginXml)
      .contains("<idea-plugin implementation-detail=\"true\">")
      .contains("<name>Recent Files</name>")
      .contains("<description>Generated plugin wrapper for module set recentFiles.</description>")
      .doesNotContain("<resource-bundle>")
      .doesNotContain("%module.set.plugin.recentFiles.name%")

    val legacyBundleFile = wrapperPath()
      .resolve("resources/messages/ModuleSetPluginsBundle.properties")
    assertThat(legacyBundleFile).doesNotExist()
  }

  @Test
  fun `cleans up legacy bundle resources`() {
    val messagesDir = wrapperPath().resolve("resources/messages")
    messagesDir.createDirectories()
    val legacyBundleFile = messagesDir.resolve("ModuleSetPluginsBundle.properties")
    legacyBundleFile.writeText("module.set.plugin.recentFiles.name=Recent Files\n")

    syncModuleSetPluginsOnDisk(projectRoot = projectRoot, moduleSets = listOf(recentFilesModuleSet()))

    assertThat(legacyBundleFile).doesNotExist()
    assertThat(messagesDir).doesNotExist()
  }

  @Test
  fun `removes legacy wrapper root after migration`() {
    val moduleName = "intellij.moduleSet.plugin.recentFiles"
    val legacyWrapperDir = projectRoot.resolve(LEGACY_GENERATED_ROOT).resolve(moduleName)
    legacyWrapperDir.createDirectories()
    legacyWrapperDir.resolve("legacy.marker").writeText("legacy")

    syncModuleSetPluginsOnDisk(projectRoot = projectRoot, moduleSets = listOf(recentFilesModuleSet()))

    assertThat(legacyWrapperDir).doesNotExist()
    assertThat(wrapperPath()).exists()
  }

  private fun recentFilesModuleSet(): ModuleSet {
    return plugin("recentFiles") {
      module("intellij.platform.recentFiles")
      module("intellij.platform.recentFiles.frontend")
      module("intellij.platform.recentFiles.backend")
    }
  }

  private fun wrapperPath(): Path {
    val moduleName = "intellij.moduleSet.plugin.recentFiles"
    return projectRoot.resolve(NEW_GENERATED_ROOT).resolve(moduleName)
  }

  private fun mainPath(): Path {
    return projectRoot.resolve(NEW_GENERATED_ROOT).resolve(MAIN_MODULE_NAME)
  }

  private fun writeModulesXml(path: Path, moduleFilepaths: List<String>) {
    path.parent.createDirectories()
    val moduleLines = moduleFilepaths.joinToString(separator = "\n") {
      "      <module fileurl=\"file://$it\" filepath=\"$it\" />"
    }
    val xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="ProjectModuleManager">
          <modules>
      $moduleLines
          </modules>
        </component>
      </project>
      """.trimIndent() + "\n"
    path.writeText(xml)
  }

  private fun moduleFilepaths(xmlText: String): List<String> {
    val regex = Regex("<module\\s+[^>]*filepath=\"([^\"]+)\"")
    return regex.findAll(xmlText).map { it.groupValues[1] }.toList()
  }
}
