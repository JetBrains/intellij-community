// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout.generator

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationMode
import org.jetbrains.intellij.build.productLayout.plugin
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.intellij.build.productLayout.util.GeneratedArtifactWritePolicy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val PROJECT_DIR_MACRO: String = "$" + "PROJECT_DIR" + "$"
private const val COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML: String = "community/module-set-plugins/generated"
private const val GENERATED_ROOT_IN_COMMUNITY_MODULES_XML: String = "module-set-plugins/generated"
private const val ULTIMATE_GENERATED_ROOT_IN_ROOT_MODULES_XML: String = "module-set-plugins/generated"
private const val MAIN_MODULE_NAME: String = "intellij.moduleSet.plugin.main"
private const val LEGACY_GENERATED_ROOT: String = "community/platform/platform-resources/generated/module-set-plugins"
private const val LEGACY_GENERATED_ROOT_IN_COMMUNITY_MODULES_XML: String = "platform/platform-resources/generated/module-set-plugins"

@ExtendWith(TestFailureLogger::class)
class ModuleSetPluginGeneratorTest {
  @TempDir
  lateinit var projectRoot: Path

  @Test
  fun `syncs both modules xml files with module-name sort and no trailing newline`() {
    val rootGeneratedWrapperPath =
      "$PROJECT_DIR_MACRO/$COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML/intellij.moduleSet.plugin.recentFiles/intellij.moduleSet.plugin.recentFiles.iml"
    val rootGeneratedMainPath =
      "$PROJECT_DIR_MACRO/$COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML/$MAIN_MODULE_NAME/$MAIN_MODULE_NAME.iml"
    val communityGeneratedWrapperPath =
      "$PROJECT_DIR_MACRO/$GENERATED_ROOT_IN_COMMUNITY_MODULES_XML/intellij.moduleSet.plugin.recentFiles/intellij.moduleSet.plugin.recentFiles.iml"
    val communityGeneratedMainPath =
      "$PROJECT_DIR_MACRO/$GENERATED_ROOT_IN_COMMUNITY_MODULES_XML/$MAIN_MODULE_NAME/$MAIN_MODULE_NAME.iml"
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

    generateAndCommit(communityModuleSets = listOf(recentFilesModuleSet()))

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
    generateAndCommit(communityModuleSets = listOf(recentFilesModuleSet()))

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
  fun `skips opted-out wrapper from shared main runtime dependencies`() {
    generateAndCommit(
      communityModuleSets = listOf(recentFilesModuleSet(), optedOutFrontendModuleSet()),
    )

    val optedOutWrapperImlText = optedOutWrapperPath()
      .resolve("intellij.moduleSet.plugin.opted.out.frontend.iml")
      .readText()
    assertThat(optedOutWrapperImlText)
      .doesNotContain("module-name=\"intellij.platform.opted.out.frontend\"")

    val mainImlText = mainPath().resolve("$MAIN_MODULE_NAME.iml").readText()
    assertThat(mainImlText)
      .contains("<orderEntry type=\"module\" module-name=\"intellij.moduleSet.plugin.recentFiles\" scope=\"RUNTIME\" />")
      .doesNotContain("module-name=\"intellij.moduleSet.plugin.opted.out.frontend\"")
      .doesNotContain("module-name=\"intellij.platform.opted.out.frontend\"")
  }

  @Test
  fun `removes stale main module when all wrappers opt out`() {
    val staleMainModuleDir = mainPath()
    staleMainModuleDir.createDirectories()
    staleMainModuleDir.resolve("$MAIN_MODULE_NAME.iml").writeText("stale")

    generateAndCommit(communityModuleSets = listOf(optedOutFrontendModuleSet()))

    assertThat(staleMainModuleDir).doesNotExist()
  }

  @Test
  fun `syncs ultimate wrappers into root generated dir only`() {
    val rootGeneratedUltimateWrapperPath =
      "$PROJECT_DIR_MACRO/$ULTIMATE_GENERATED_ROOT_IN_ROOT_MODULES_XML/intellij.moduleSet.plugin.vcs.frontend.split/intellij.moduleSet.plugin.vcs.frontend.split.iml"
    val staleCommunityUltimateWrapperPath =
      "$PROJECT_DIR_MACRO/$COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML/intellij.moduleSet.plugin.vcs.frontend.split/intellij.moduleSet.plugin.vcs.frontend.split.iml"
    val staleCommunityUltimateWrapperDir = projectRoot
      .resolve(COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML)
      .resolve("intellij.moduleSet.plugin.vcs.frontend.split")
    staleCommunityUltimateWrapperDir.createDirectories()
    staleCommunityUltimateWrapperDir.resolve("stale.marker").writeText("legacy")

    val rootModulesXml = projectRoot.resolve(".idea/modules.xml")
    writeModulesXml(
      rootModulesXml,
      moduleFilepaths = listOf(
        staleCommunityUltimateWrapperPath,
        "$PROJECT_DIR_MACRO/aaa/intellij.zulu.iml",
      ),
    )

    val communityModulesXml = projectRoot.resolve("community/.idea/modules.xml")
    writeModulesXml(
      communityModulesXml,
      moduleFilepaths = listOf(
        "$PROJECT_DIR_MACRO/${GENERATED_ROOT_IN_COMMUNITY_MODULES_XML}/intellij.moduleSet.plugin.vcs.frontend.split/intellij.moduleSet.plugin.vcs.frontend.split.iml",
        "$PROJECT_DIR_MACRO/aaa/intellij.zulu.iml",
      ),
    )

    generateAndCommit(
      communityModuleSets = listOf(recentFilesModuleSet()),
      ultimateModuleSets = listOf(vcsFrontendSplitModuleSet()),
    )

    assertThat(ultimateWrapperPath()).exists()
    assertThat(staleCommunityUltimateWrapperDir).doesNotExist()

    val rootPaths = moduleFilepaths(rootModulesXml.readText())
    assertThat(rootPaths)
      .contains(rootGeneratedUltimateWrapperPath)
      .doesNotContain(staleCommunityUltimateWrapperPath)

    val communityPaths = moduleFilepaths(communityModulesXml.readText())
    assertThat(communityPaths)
      .doesNotContain("$PROJECT_DIR_MACRO/${GENERATED_ROOT_IN_COMMUNITY_MODULES_XML}/intellij.moduleSet.plugin.vcs.frontend.split/intellij.moduleSet.plugin.vcs.frontend.split.iml")
  }

  @Test
  fun `generates wrapper plugin with literal name and without resource bundle`() {
    generateAndCommit(communityModuleSets = listOf(recentFilesModuleSet()))

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
  fun `generates plugin content yaml for dotted module set names`() {
    generateAndCommit(communityModuleSets = listOf(vcsFrontendModuleSet()))

    val pluginContentYaml = projectRoot
      .resolve(COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML)
      .resolve("intellij.moduleSet.plugin.vcs.frontend")
      .resolve("plugin-content.yaml")
      .readText()

    assertThat(pluginContentYaml)
      .isEqualTo(
        """
        - name: lib/moduleSet-plugin-vcs-frontend.jar
          modules:
          - name: intellij.moduleSet.plugin.vcs.frontend
        - name: lib/modules/intellij.platform.vcs.impl.frontend.jar
          contentModules:
          - name: intellij.platform.vcs.impl.frontend
        """.trimIndent()
      )
  }

  @Test
  fun `cleans up legacy bundle resources`() {
    val messagesDir = wrapperPath().resolve("resources/messages")
    messagesDir.createDirectories()
    val legacyBundleFile = messagesDir.resolve("ModuleSetPluginsBundle.properties")
    legacyBundleFile.writeText("module.set.plugin.recentFiles.name=Recent Files\n")

    generateAndCommit(communityModuleSets = listOf(recentFilesModuleSet()))

    assertThat(legacyBundleFile).doesNotExist()
    assertThat(messagesDir).doesNotExist()
  }

  @Test
  fun `removes legacy wrapper root after migration`() {
    val moduleName = "intellij.moduleSet.plugin.recentFiles"
    val legacyWrapperDir = projectRoot.resolve(LEGACY_GENERATED_ROOT).resolve(moduleName)
    legacyWrapperDir.createDirectories()
    legacyWrapperDir.resolve("legacy.marker").writeText("legacy")

    generateAndCommit(communityModuleSets = listOf(recentFilesModuleSet()))

    assertThat(legacyWrapperDir).doesNotExist()
    assertThat(wrapperPath()).exists()
  }

  @Test
  fun `validate only mode records wrapper diffs without touching disk`() {
    val execution = generate(
      communityModuleSets = listOf(recentFilesModuleSet()),
      generationMode = GenerationMode.VALIDATE_ONLY,
    )

    assertThat(wrapperPath()).doesNotExist()
    assertThat(mainPath()).doesNotExist()
    assertThat(execution.fileUpdater.getDiffs().map { projectRoot.relativize(it.path).toString() })
      .contains(
        "$COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML/intellij.moduleSet.plugin.recentFiles/intellij.moduleSet.plugin.recentFiles.iml",
        "$COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML/$MAIN_MODULE_NAME/$MAIN_MODULE_NAME.iml",
      )
  }

  @Test
  fun `validate only mode records orphan cleanup diffs without touching disk`() {
    val staleWrapperDir = projectRoot
      .resolve(COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML)
      .resolve("intellij.moduleSet.plugin.stale")
    staleWrapperDir.createDirectories()
    val staleMarker = staleWrapperDir.resolve("stale.marker")
    staleMarker.writeText("stale")

    val execution = generate(
      communityModuleSets = listOf(recentFilesModuleSet()),
      generationMode = GenerationMode.VALIDATE_ONLY,
    )
    cleanupOrphanedModuleSetPluginFiles(projectRoot, execution.output, execution.strategy)

    assertThat(staleMarker).exists()
    assertThat(execution.fileUpdater.getDiffs().map { projectRoot.relativize(it.path).toString() })
      .contains("$COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML/intellij.moduleSet.plugin.stale/stale.marker")
  }

  @Test
  fun `update suppressions mode skips wrapper generation entirely`() {
    val execution = generate(
      communityModuleSets = listOf(recentFilesModuleSet()),
      generationMode = GenerationMode.UPDATE_SUPPRESSIONS,
    )

    assertThat(execution.output.files).isEmpty()
    assertThat(execution.fileUpdater.getDiffs()).isEmpty()
    assertThat(wrapperPath()).doesNotExist()
  }

  private fun recentFilesModuleSet(): ModuleSet {
    return plugin("recentFiles") {
      module("intellij.platform.recentFiles")
      module("intellij.platform.recentFiles.frontend")
      module("intellij.platform.recentFiles.backend")
    }
  }

  private fun vcsFrontendModuleSet(): ModuleSet {
    return plugin("vcs.frontend") {
      module("intellij.platform.vcs.impl.frontend")
    }
  }

  private fun optedOutFrontendModuleSet(): ModuleSet {
    return plugin("opted.out.frontend", addToMainModule = false) {
      module("intellij.platform.opted.out.frontend")
    }
  }

  private fun vcsFrontendSplitModuleSet(): ModuleSet {
    return plugin("vcs.frontend.split", addToMainModule = false) {
      module("intellij.platform.vcs.protocol.split.generated")
      module("intellij.platform.vcs.common.split")
      module("intellij.platform.vcs.frontend.split")
    }
  }

  private fun wrapperPath(): Path {
    val moduleName = "intellij.moduleSet.plugin.recentFiles"
    return projectRoot.resolve(COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML).resolve(moduleName)
  }

  private fun mainPath(): Path {
    return projectRoot.resolve(COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML).resolve(MAIN_MODULE_NAME)
  }

  private fun optedOutWrapperPath(): Path {
    val moduleName = "intellij.moduleSet.plugin.opted.out.frontend"
    return projectRoot.resolve(COMMUNITY_GENERATED_ROOT_IN_ROOT_MODULES_XML).resolve(moduleName)
  }

  private fun ultimateWrapperPath(): Path {
    val moduleName = "intellij.moduleSet.plugin.vcs.frontend.split"
    return projectRoot.resolve(ULTIMATE_GENERATED_ROOT_IN_ROOT_MODULES_XML).resolve(moduleName)
  }

  private fun generateAndCommit(
    communityModuleSets: List<ModuleSet> = emptyList(),
    ultimateModuleSets: List<ModuleSet> = emptyList(),
  ): GeneratorExecution {
    val execution = generate(communityModuleSets = communityModuleSets, ultimateModuleSets = ultimateModuleSets)
    val cleanup = cleanupOrphanedModuleSetPluginFiles(projectRoot, execution.output, execution.strategy)
    execution.fileUpdater.commit()
    cleanupGeneratedArtifactDirectories(cleanup.emptyDirectoryCandidates)
    return execution
  }

  private fun generate(
    communityModuleSets: List<ModuleSet> = emptyList(),
    ultimateModuleSets: List<ModuleSet> = emptyList(),
    generationMode: GenerationMode = GenerationMode.NORMAL,
  ): GeneratorExecution {
    val fileUpdater = DeferredFileUpdater(projectRoot)
    val strategy = GeneratedArtifactWritePolicy(generationMode, fileUpdater)
    val output = generateModuleSetPlugins(
      projectRoot = projectRoot,
      strategy = strategy,
      communityModuleSets = communityModuleSets,
      ultimateModuleSets = ultimateModuleSets,
      generationMode = generationMode,
    )
    return GeneratorExecution(output = output, fileUpdater = fileUpdater, strategy = strategy)
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

  private data class GeneratorExecution(
    val output: org.jetbrains.intellij.build.productLayout.pipeline.ModuleSetPluginsOutput,
    val fileUpdater: DeferredFileUpdater,
    val strategy: GeneratedArtifactWritePolicy,
  )
}
