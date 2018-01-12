/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.ex

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.configurationStore.StoreAwareProjectManager
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.project.ProjectManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.delete
import com.intellij.util.io.readText
import com.intellij.util.io.write
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

class ProjectInspectionManagerTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val ruleChain = RuleChain(tempDirManager, InitInspectionRule())

  @Test fun `component`() {
    loadAndUseProject(tempDirManager, {
      it.path
    }) { project ->
      val projectInspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)

      assertThat(projectInspectionProfileManager.state).isEmpty()

      projectInspectionProfileManager.currentProfile

      assertThat(projectInspectionProfileManager.state).isEmpty()

      // cause to use app profile
      projectInspectionProfileManager.setRootProfile(null)
      val doNotUseProjectProfileState = """
      <state>
        <settings>
          <option name="USE_PROJECT_PROFILE" value="false" />
          <version value="1.0" />
        </settings>
      </state>""".trimIndent()
      assertThat(projectInspectionProfileManager.state).isEqualTo(doNotUseProjectProfileState)

      val inspectionDir = Paths.get(project.stateStore.projectConfigDir, "inspectionProfiles")
      val file = inspectionDir.resolve("profiles_settings.xml")
      project.saveStore()
      assertThat(file).exists()
      val doNotUseProjectProfileData = """
      <component name="InspectionProjectProfileManager">
        <settings>
          <option name="USE_PROJECT_PROFILE" value="false" />
          <version value="1.0" />
        </settings>
      </component>""".trimIndent()
      assertThat(file.readText()).isEqualTo(doNotUseProjectProfileData)

      // test load
      file.delete()

      project.baseDir.refresh(false, true)
      (ProjectManager.getInstance() as StoreAwareProjectManager).flushChangedProjectFileAlarm()
      assertThat(projectInspectionProfileManager.state).isEmpty()

      file.write(doNotUseProjectProfileData)
      project.baseDir.refresh(false, true)
      (ProjectManager.getInstance() as StoreAwareProjectManager).flushChangedProjectFileAlarm()
      assertThat(projectInspectionProfileManager.state).isEqualTo(doNotUseProjectProfileState)
    }
  }

  @Test fun `do not save default project profile`() {
    loadAndUseProject(tempDirManager, {
      it.path
    }) { project ->
      val inspectionDir = Paths.get(project.stateStore.projectConfigDir, "inspectionProfiles")
      val profileFile = inspectionDir.resolve("Project_Default.xml")
      assertThat(profileFile).doesNotExist()

      val projectInspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)
      assertThat(projectInspectionProfileManager.state).isEmpty()

      projectInspectionProfileManager.currentProfile

      assertThat(projectInspectionProfileManager.state).isEmpty()

      project.saveStore()

      assertThat(profileFile).doesNotExist()
    }
  }

  @Test fun `profiles`() {
    loadAndUseProject(tempDirManager, {
      it.path
    }) { project ->
      val projectInspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)
      projectInspectionProfileManager.forceLoadSchemes()

      assertThat(projectInspectionProfileManager.state).isEmpty()

      // cause to use app profile
      val currentProfile = projectInspectionProfileManager.currentProfile
      assertThat(currentProfile.isProjectLevel).isTrue()
      currentProfile.setToolEnabled("Convert2Diamond", false)

      project.saveStore()

      val inspectionDir = Paths.get(project.stateStore.projectConfigDir, "inspectionProfiles")
      val file = inspectionDir.resolve("profiles_settings.xml")

      assertThat(file).doesNotExist()
      val profileFile = inspectionDir.resolve("Project_Default.xml")
      assertThat(profileFile.readText()).isEqualTo("""
      <component name="InspectionProjectProfileManager">
        <profile version="1.0">
          <option name="myName" value="Project Default" />
          <inspection_tool class="Convert2Diamond" enabled="false" level="WARNING" enabled_by_default="false" />
        </profile>
      </component>""".trimIndent())

      profileFile.write("""
      <component name="InspectionProjectProfileManager">
        <profile version="1.0">
          <option name="myName" value="Project Default" />
          <inspection_tool class="Convert2Diamond" enabled="false" level="ERROR" enabled_by_default="false" />
        </profile>
      </component>""".trimIndent())

      project.baseDir.refresh(false, true)
      (ProjectManager.getInstance() as StoreAwareProjectManager).flushChangedProjectFileAlarm()
      assertThat(projectInspectionProfileManager.currentProfile.getToolDefaultState("Convert2Diamond", project).level).isEqualTo(HighlightDisplayLevel.ERROR)
    }
  }

  @Test fun `ipr`() {
    val emptyProjectFile = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
      </project>""".trimIndent()
    loadAndUseProject(tempDirManager, {
      it.writeChild("test${ProjectFileType.DOT_DEFAULT_EXTENSION}", emptyProjectFile).path
    }) { project ->
      val projectInspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)
      projectInspectionProfileManager.forceLoadSchemes()

      assertThat(projectInspectionProfileManager.state).isEmpty()

      val currentProfile = projectInspectionProfileManager.currentProfile
      assertThat(currentProfile.isProjectLevel).isTrue()
      currentProfile.setToolEnabled("Convert2Diamond", false)
      currentProfile.profileChanged()

      project.saveStore()
      val projectFile = Paths.get((project.stateStore).projectFilePath)

      assertThat(projectFile.parent.resolve(".inspectionProfiles")).doesNotExist()

      val expected = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="InspectionProjectProfileManager">
          <profile version="1.0">
            <option name="myName" value="Project Default" />
            <inspection_tool class="Convert2Diamond" enabled="false" level="WARNING" enabled_by_default="false" />
          </profile>
          <version value="1.0" />
        </component>
      </project>""".trimIndent()
      assertThat(projectFile.readText()).isEqualTo(expected)

      currentProfile.disableAllTools()
      currentProfile.profileChanged()
      project.saveStore()
      assertThat(projectFile.readText()).isNotEqualTo(expected)
      assertThat(projectFile.parent.resolve(".inspectionProfiles")).doesNotExist()
    }
  }
}