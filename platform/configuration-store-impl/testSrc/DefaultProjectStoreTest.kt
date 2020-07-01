// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.checkDefaultProjectAsTemplate
import com.intellij.testFramework.rules.createDeleteAppConfigRule
import com.intellij.util.io.getDirectoryTree
import com.intellij.util.isEmpty
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

@Suppress("UsePropertyAccessSyntax")
internal class DefaultProjectStoreTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  private val tempDirManager = TemporaryDirectory()

  @JvmField
  @Rule
  val ruleChain = RuleChain(tempDirManager, createDeleteAppConfigRule())

  @Test
  fun `new project from default - file-based storage`() {
    checkDefaultProjectAsTemplate { checkTask ->
      val project = openAsNewProjectAndUseDefaultSettings(fsRule.fs.getPath("/test${ProjectFileType.DOT_DEFAULT_EXTENSION}"))
      project.use {
        checkTask(project, true)
      }
    }
  }

  @Test
  fun `new project from default - directory-based storage`() {
    checkDefaultProjectAsTemplate { checkTask ->
      // obviously, project must be directory-based also
      val project = openAsNewProjectAndUseDefaultSettings(fsRule.fs.getPath("/test"))
      project.use {
        checkTask(project, true)
      }
    }
  }

  private fun openAsNewProjectAndUseDefaultSettings(file: Path): Project {
    return ProjectManagerEx.getInstanceEx().openProject(file, createTestOpenProjectOptions().copy(isNewProject = true, useDefaultProjectAsTemplate = true))!!
  }

  @Test
  fun `new project from default - remove workspace component configuration`() {
    val testData = Paths.get(PathManagerEx.getCommunityHomePath(), "platform/configuration-store-impl/testData")
    val element = JDOMUtil.load(testData.resolve("testData1.xml"))

    val tempDir = fsRule.fs.getPath("")
    normalizeDefaultProjectElement(ProjectManager.getInstance().defaultProject, element, tempDir)
    assertThat(element.isEmpty()).isTrue()

    val directoryTree = tempDir.getDirectoryTree()
    assertThat(directoryTree).toMatchSnapshot(testData.resolve("testData1.txt"))
  }

  @Test
  fun `new IPR project from default - remove workspace component configuration`() {
    val testData = Paths.get(PathManagerEx.getCommunityHomePath(), "platform/configuration-store-impl/testData")
    val element = JDOMUtil.load(testData.resolve("testData1.xml"))

    val tempDir = fsRule.fs.getPath("")
    val projectFile = tempDir.resolve("test.ipr")
    moveComponentConfiguration(ProjectManager.getInstance().defaultProject, element, { "" }) {
      if (it == "workspace.xml") tempDir.resolve("test.iws") else { projectFile }
    }
    assertThat(JDOMUtil.isEmpty(element)).isTrue()
    assertThat(tempDir.getDirectoryTree()).toMatchSnapshot(testData.resolve("testData1-ipr.txt"))
  }
}