// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.ex

import com.intellij.codeInspection.ex.DisableInspectionToolModCommandAction
import com.intellij.configurationStore.LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.profile.codeInspection.PROFILE_DIR
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.project.stateStore
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.InitInspectionRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.refreshProjectConfigDir
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DisableInspectionToolModCommandActionTest {
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

  /** @param setUp prepares the project directory before the project loads */
  private fun doTest(setUp: (Path) -> Unit = {}, task: suspend (Project) -> Unit) {
    runBlocking(Dispatchers.Default) {
      createOrLoadProject(
        tempDirManager = tempDirManager,
        projectCreator = { Path.of(it.path).also(setUp) },
        task = { project ->
          project.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true)
          ProjectInspectionProfileManager.getInstance(project).forceLoadSchemes()
          task(project)
        },
        runPostStartUpActivities = true,
        loadComponentState = true,
      )
    }
  }

  @Test
  fun `update existing profile file`() {
    doTest { project ->
      val profileManager = ProjectInspectionProfileManager.getInstance(project)
      profileManager.currentProfile.setToolEnabled("Convert2Diamond", false)
      project.stateStore.save()
      val profileFile = project.stateStore.directoryStorePath!!.resolve(PROFILE_DIR).resolve("Project_Default.xml")
      assertThat(profileFile).exists()
      refreshProjectConfigDir(project)

      val (context, command) = computeCommand(project, "Convert2Lambda")
      assertThat(command).isInstanceOf(ModUpdateFileText::class.java)
      command as ModUpdateFileText
      assertThat(command.file.name).isEqualTo("Project_Default.xml")
      assertThat(command.newText).isEqualTo("""
        <component name="InspectionProjectProfileManager">
          <profile version="1.0">
            <option name="myName" value="Project Default" />
            <inspection_tool class="Convert2Diamond" enabled="false" level="WARNING" enabled_by_default="false" />
            <inspection_tool class="Convert2Lambda" enabled="false" level="WARNING" enabled_by_default="false" />
          </profile>
        </component>""".trimIndent())
      // The command only inserts text, and the fragments change the old text to the new text
      assertThat(command.updatedRanges).isNotEmpty()
      assertThat(command.updatedRanges).allMatch { it.oldLength == 0 }
      assertThat(applyFragments(command)).isEqualTo(command.newText)
      // The action must not change the profile in memory
      assertThat(profileManager.currentProfile.getToolDefaultState("Convert2Lambda", project).isEnabled).isTrue()

      executeAndReload(project, context, command)
      assertThat(profileFile.readText()).isEqualTo(command.newText)
      assertThat(profileManager.currentProfile.getToolDefaultState("Convert2Lambda", project).isEnabled).isFalse()
      assertThat(profileManager.currentProfile.getToolDefaultState("Convert2Diamond", project).isEnabled).isFalse()
    }
  }

  @Test
  fun `disable in the scope of the file`() {
    doTest(setUp = { root ->
      val profileFile = root.resolve(".idea").resolve(PROFILE_DIR).resolve("Project_Default.xml")
      profileFile.parent.createDirectories()
      profileFile.writeText("""
        <component name="InspectionProjectProfileManager">
          <profile version="1.0">
            <option name="myName" value="Project Default" />
            <inspection_tool class="Convert2Lambda" enabled="true" level="WARNING" enabled_by_default="true">
              <scope name="All" level="ERROR" enabled="true" />
            </inspection_tool>
          </profile>
        </component>""".trimIndent())
    }) { project ->
      val (_, command) = computeCommand(project, "Convert2Lambda")
      command as ModUpdateFileText
      // Only the state of the scope that contains the file is disabled
      assertThat(command.newText).isEqualTo("""
        <component name="InspectionProjectProfileManager">
          <profile version="1.0">
            <option name="myName" value="Project Default" />
            <inspection_tool class="Convert2Lambda" enabled="true" level="WARNING" enabled_by_default="true">
              <scope name="All" level="ERROR" enabled="false" />
            </inspection_tool>
          </profile>
        </component>""".trimIndent())
    }
  }

  @Test
  fun `not available when the tool is disabled`() {
    doTest(setUp = { root -> writeProfileWithDisabledTool(root) }) { project ->
      val action = DisableInspectionToolModCommandAction("Convert2Lambda")
      readAction {
        val context = createContext(project)
        assertThat(action.getPresentation(context)).isNull()
        assertThat(action.perform(context).isEmpty).isTrue()
      }
    }
  }

  @Test
  fun `no change when the file already disables the tool`() {
    doTest(setUp = { root -> writeProfileWithDisabledTool(root) }) { project ->
      // The profile in memory is older than the file
      ProjectInspectionProfileManager.getInstance(project).currentProfile.setToolEnabled("Convert2Lambda", true)
      val (_, command) = computeCommand(project, "Convert2Lambda")
      assertThat(command.isEmpty).isTrue()
    }
  }

  @Test
  fun `create profile file`() {
    doTest { project ->
      val configDir = project.stateStore.directoryStorePath!!
      configDir.createDirectories()
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(configDir)
      val profileFile = configDir.resolve(PROFILE_DIR).resolve("Project_Default.xml")
      assertThat(profileFile).doesNotExist()

      val (context, command) = computeCommand(project, "Convert2Lambda")
      val creations = command.unpack().filterIsInstance<ModCreateFile>()
      assertThat(creations.map { it.file.name }).containsExactly(PROFILE_DIR, "Project_Default.xml")
      assertThat(creations[0].content).isInstanceOf(ModCreateFile.Directory::class.java)
      assertThat((creations[1].content as ModCreateFile.Text).text).isEqualTo("""
        <component name="InspectionProjectProfileManager">
          <profile version="1.0">
            <option name="myName" value="Project Default" />
            <inspection_tool class="Convert2Lambda" enabled="false" level="WARNING" enabled_by_default="false" />
          </profile>
        </component>""".trimIndent())

      executeAndReload(project, context, command)
      // A new file gets the system line separator
      assertThat(StringUtil.convertLineSeparators(profileFile.readText())).isEqualTo((creations[1].content as ModCreateFile.Text).text)
    }
  }

  @Test
  fun `not available for application profile`() {
    doTest { project ->
      ProjectInspectionProfileManager.getInstance(project).setRootProfile(null)
      val presentation = readAction {
        DisableInspectionToolModCommandAction("Convert2Lambda").getPresentation(createContext(project))
      }
      assertThat(presentation).isNull()
    }
  }

  private suspend fun computeCommand(project: Project, toolId: String): Pair<ActionContext, ModCommand> = readAction {
    val context = createContext(project)
    val action = DisableInspectionToolModCommandAction(toolId)
    assertThat(action.getPresentation(context)).isNotNull()
    context to action.perform(context)
  }

  private fun writeProfileWithDisabledTool(root: Path) {
    val profileFile = root.resolve(".idea").resolve(PROFILE_DIR).resolve("Project_Default.xml")
    profileFile.parent.createDirectories()
    profileFile.writeText("""
      <component name="InspectionProjectProfileManager">
        <profile version="1.0">
          <option name="myName" value="Project Default" />
          <inspection_tool class="Convert2Lambda" enabled="false" level="WARNING" enabled_by_default="false" />
        </profile>
      </component>""".trimIndent())
  }

  private fun applyFragments(command: ModUpdateFileText): String {
    val text = StringBuilder(command.oldText)
    for (fragment in command.updatedRanges) {
      val newFragment = command.newText.substring(fragment.offset, fragment.offset + fragment.newLength)
      text.replace(fragment.offset, fragment.offset + fragment.oldLength, newFragment)
    }
    return text.toString()
  }

  private fun createContext(project: Project): ActionContext {
    // The file needs a virtual file, so that a scope of the profile can contain it
    val file = PsiFileFactory.getInstance(project).createFileFromText("A.java", JavaFileType.INSTANCE, "class A {}", 0, true)
    return ActionContext.from(null, file)
  }

  private suspend fun executeAndReload(project: Project, context: ActionContext, command: ModCommand) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        CommandProcessor.getInstance().executeCommand(project, {
          ModCommandExecutor.getInstance().executeInBatch(context, command)
        }, null, null)
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
    refreshProjectConfigDir(project)
    StoreReloadManager.getInstance(project).reloadChangedStorageFiles()
  }
}
