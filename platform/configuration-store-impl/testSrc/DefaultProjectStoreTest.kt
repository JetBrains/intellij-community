// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.checkDefaultProjectAsTemplate
import com.intellij.testFramework.useProject
import com.intellij.util.io.getDirectoryTree
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

internal class DefaultProjectStoreTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test
  fun `new project from default - file-based storage`() {
    checkDefaultProjectAsTemplate { checkTask ->
      val project = openAsNewProjectAndUseDefaultSettings(fsRule.fs.getPath("/test${ProjectFileType.DOT_DEFAULT_EXTENSION}"))
      project.useProject {
        checkTask(project, true)
      }
    }
  }

  @Test
  fun `new project from default - directory-based storage`() {
    checkDefaultProjectAsTemplate { checkTask ->
      // obviously, project must be directory-based also
      val project = openAsNewProjectAndUseDefaultSettings(fsRule.fs.getPath("/test"))
      project.useProject {
        checkTask(project, true)
      }
    }
  }

  @Test
  fun `save default project configuration changes`() {
    runBlocking {
      val defaultTestComponent = TestComponentCustom()
      val defaultProject = ProjectManager.getInstance().defaultProject
      val defaultStateStore = defaultProject.service<IComponentStore>()
      defaultStateStore.initComponent(defaultTestComponent, null, null)
      saveSettings(ApplicationManager.getApplication())
      assertThat(defaultTestComponent.saved).isTrue
    }
  }

  @Suppress("DEPRECATION")
  private class TestComponentCustom : com.intellij.openapi.components.SettingsSavingComponent {
    var saved = false
    override fun save() {
      saved = true
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
    assertThat(JDOMUtil.isEmpty(element)).isTrue()

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