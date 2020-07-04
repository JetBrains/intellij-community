// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.configurationStore.LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.profile.codeInspection.PROFILE_DIR
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.*
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class ProjectInspectionManagerTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val initInspectionRule = InitInspectionRule()

  private fun doTest(task: suspend (Project) -> Unit) {
    runBlocking {
      loadAndUseProjectInLoadComponentStateMode(tempDirManager, { Paths.get(it.path) }, task)
    }
  }

  @Test
  fun component() {
    doTest { project ->
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

      val inspectionDir = project.stateStore.projectConfigDir!!.resolve(PROFILE_DIR)
      val file = inspectionDir.resolve("profiles_settings.xml")
      project.stateStore.save()
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

      refreshProjectConfigDir(project)
      StoreReloadManager.getInstance().reloadChangedStorageFiles()
      assertThat(projectInspectionProfileManager.state).isEmpty()

      file.write(doNotUseProjectProfileData)
      refreshProjectConfigDir(project)
      StoreReloadManager.getInstance().reloadChangedStorageFiles()
      assertThat(projectInspectionProfileManager.state).isEqualTo(doNotUseProjectProfileState)
    }
  }

  @Test
  fun `do not save default project profile`() {
    doTest { project ->
      val inspectionDir = project.stateStore.projectConfigDir!!.resolve(PROFILE_DIR)
      val profileFile = inspectionDir.resolve("Project_Default.xml")
      assertThat(profileFile).doesNotExist()

      val projectInspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)
      assertThat(projectInspectionProfileManager.state).isEmpty()

      projectInspectionProfileManager.currentProfile

      assertThat(projectInspectionProfileManager.state).isEmpty()

      project.stateStore.save()

      assertThat(profileFile).doesNotExist()
    }
  }

  @Test
  fun profiles() {
    doTest { project ->
      project.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true)

      val projectInspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)
      projectInspectionProfileManager.forceLoadSchemes()

      assertThat(projectInspectionProfileManager.state).isEmpty()

      // cause to use app profile
      val currentProfile = projectInspectionProfileManager.currentProfile
      assertThat(currentProfile.isProjectLevel).isTrue()
      currentProfile.setToolEnabled("Convert2Diamond", false)

      project.stateStore.save()

      val inspectionDir = project.stateStore.projectConfigDir!!.resolve(PROFILE_DIR)
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

      refreshProjectConfigDir(project)
      StoreReloadManager.getInstance().reloadChangedStorageFiles()
      assertThat(projectInspectionProfileManager.currentProfile.getToolDefaultState("Convert2Diamond", project).level).isEqualTo(
        HighlightDisplayLevel.ERROR)
    }
  }

  @Test
  fun `detect externally added profiles`() {
    doTest { project ->
      project.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true)
      val profileManager = ProjectInspectionProfileManager.getInstance(project)
      profileManager.forceLoadSchemes()

      assertThat(profileManager.profiles.joinToString { it.name }).isEqualTo("Project Default")
      assertThat(profileManager.currentProfile.isProjectLevel).isTrue()
      assertThat(profileManager.currentProfile.name).isEqualTo("Project Default")

      val projectConfigDir = project.stateStore.projectConfigDir!!

      // test creation of .idea/inspectionProfiles dir, not .idea itself
      projectConfigDir.createDirectories()
      LocalFileSystem.getInstance().refreshAndFindFileByPath(projectConfigDir.toString())

      val profileDir = projectConfigDir.resolve(PROFILE_DIR)
      profileDir.writeChild("profiles_settings.xml", """<component name="InspectionProjectProfileManager">
        <settings>
          <option name="PROJECT_PROFILE" value="idea.default" />
          <version value="1.0" />
          <info color="eb9904">
            <option name="FOREGROUND" value="0" />
            <option name="BACKGROUND" value="eb9904" />
            <option name="ERROR_STRIPE_COLOR" value="eb9904" />
            <option name="myName" value="Strong Warning" />
            <option name="myVal" value="50" />
            <option name="myExternalName" value="Strong Warning" />
            <option name="myDefaultAttributes">
              <option name="ERROR_STRIPE_COLOR" value="eb9904" />
            </option>
          </info>
        </settings>
      </component>""")
      writeDefaultProfile(profileDir)
      profileDir.writeChild("idea_default_teamcity.xml", """
        <component name="InspectionProjectProfileManager">
        <profile version="1.0">
          <option name="myName" value="idea.default.teamcity" />
          <inspection_tool class="AbsoluteAlignmentInUserInterface" enabled="false" level="WARNING" enabled_by_default="false">
            <scope name="android" level="WARNING" enabled="false" />
          </inspection_tool>
        </profile>
      </component>""")

      refreshProjectConfigDir(project)
      StoreReloadManager.getInstance().reloadChangedStorageFiles()

      assertThat(profileManager.currentProfile.isProjectLevel).isTrue()
      assertThat(profileManager.currentProfile.name).isEqualTo("Project Default")
      assertThat(profileManager.profiles.joinToString { it.name }).isEqualTo("Project Default, idea.default.teamcity")

      profileDir.delete()
      writeDefaultProfile(profileDir)

      refreshProjectConfigDir(project)
      StoreReloadManager.getInstance().reloadChangedStorageFiles()

      assertThat(profileManager.currentProfile.name).isEqualTo("Project Default")

      project.stateStore.save()
      assertThat(profileDir.resolve("Project_Default.xml")).isEqualTo(DEFAULT_PROJECT_PROFILE_CONTENT)
    }
  }

  @Test
  fun ipr() = runBlocking {
    val emptyProjectFile = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
      </project>""".trimIndent()
    loadAndUseProjectInLoadComponentStateMode(tempDirManager, {
      Paths.get(it.writeChild("test${ProjectFileType.DOT_DEFAULT_EXTENSION}", emptyProjectFile).path)
    }) { project ->
      val projectInspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)
      projectInspectionProfileManager.forceLoadSchemes()

      assertThat(projectInspectionProfileManager.state).isEmpty()

      val currentProfile = projectInspectionProfileManager.currentProfile
      assertThat(currentProfile.isProjectLevel).isTrue()
      currentProfile.setToolEnabled("Convert2Diamond", false)
      currentProfile.profileChanged()

      project.stateStore.save()
      val projectFile = project.stateStore.projectFilePath

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
      project.stateStore.save()
      assertThat(projectFile.readText()).isNotEqualTo(expected)
      assertThat(projectFile.parent.resolve(".inspectionProfiles")).doesNotExist()
    }
  }
}

private val DEFAULT_PROJECT_PROFILE_CONTENT = """
  <component name="InspectionProjectProfileManager">
    <profile version="1.0">
      <option name="myName" value="Project Default" />
      <inspection_tool class="ActionCableChannelNotFound" enabled="false" level="WARNING" enabled_by_default="false" />
    </profile>
  </component>""".trimIndent()

private fun writeDefaultProfile(profileDir: Path) {
  profileDir.writeChild("Project_Default.xml", DEFAULT_PROJECT_PROFILE_CONTENT)
}