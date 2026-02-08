// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.pipeline

import com.intellij.platform.pluginGraph.TargetName
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.createTestModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class ModelBuildingStageTest {
  @Test
  fun `discoverPluginDescriptorsFromSources finds test plugin xml and plugin-content yaml`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.test.plugin")
      module("intellij.content.plugin")
    }

    val testModuleDir = tempDir.resolve("intellij/test/plugin")
    val testResources = testModuleDir.resolve("testResources")
    Files.createDirectories(testResources.resolve("META-INF"))
    val testModule = jps.project.modules.first { it.name == "intellij.test.plugin" }
    testModule.addSourceRoot(JpsPathUtil.pathToUrl(testResources.toString()), JavaResourceRootType.TEST_RESOURCE)
    Files.writeString(testResources.resolve("META-INF/plugin.xml"), "<idea-plugin/>")

    val contentModuleDir = tempDir.resolve("intellij/content/plugin")
    Files.createDirectories(contentModuleDir)
    val contentModule = jps.project.modules.first { it.name == "intellij.content.plugin" }
    contentModule.contentRootsList.addUrl(JpsPathUtil.pathToUrl(contentModuleDir.toString()))
    Files.writeString(contentModuleDir.resolve("plugin-content.yaml"), "content: []")

    val descriptors = ModelBuildingStage.discoverPluginDescriptorsFromSources(createTestModuleOutputProvider(jps.project))

    assertThat(descriptors.testPluginModules).containsExactly(TargetName("intellij.test.plugin"))
    assertThat(descriptors.pluginModules).containsExactly(TargetName("intellij.content.plugin"))
  }
}
