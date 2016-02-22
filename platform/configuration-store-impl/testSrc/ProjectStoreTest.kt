/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import com.intellij.util.PathUtil
import com.intellij.util.readText
import com.intellij.util.systemIndependentPath
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Paths

fun createProjectAndUseInLoadComponentStateMode(tempDirManager: TemporaryDirectory, directoryBased: Boolean = false, task: (Project) -> Unit) {
  createOrLoadProject(tempDirManager, task, directoryBased = directoryBased)
}

fun loadAndUseProject(tempDirManager: TemporaryDirectory, projectCreator: ((VirtualFile) -> String), task: (Project) -> Unit) {
  createOrLoadProject(tempDirManager, task, projectCreator, false)
}

private fun createOrLoadProject(tempDirManager: TemporaryDirectory, task: (Project) -> Unit, projectCreator: ((VirtualFile) -> String)? = null, directoryBased: Boolean) {
  runInEdtAndWait {
    var filePath: String
    if (projectCreator == null) {
      filePath = tempDirManager.newPath("test${if (directoryBased) "" else ProjectFileType.DOT_DEFAULT_EXTENSION}").systemIndependentPath
    }
    else {
      filePath = runWriteAction { projectCreator(tempDirManager.newVirtualDirectory()) }
    }

    val projectManager = ProjectManagerEx.getInstanceEx() as ProjectManagerImpl
    var project = if (projectCreator == null) projectManager.newProject(null, filePath, true, false)!! else projectManager.loadProject(filePath)!!
    project.runInLoadComponentStateMode {
      try {
        projectManager.openTestProject(project)
        task(project)
      }
      finally {
        projectManager.closeProject(project, false, true, false)
      }
    }
  }
}

internal class ProjectStoreTest {
  companion object {
    @JvmField
    @ClassRule val projectRule = ProjectRule()
  }

  val tempDirManager = TemporaryDirectory()

  private val ruleChain = RuleChain(tempDirManager)
  @Rule fun getChain() = ruleChain

  @Language("XML")
  private val iprFileContent =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\">\n  <component name=\"AATestComponent\">\n    <option name=\"value\" value=\"customValue\" />\n  </component>\n</project>"

  @State(name = "AATestComponent")
  private class TestComponent : PersistentStateComponent<TestState> {
    private var state: TestState? = null

    override fun getState() = state

    override fun loadState(state: TestState) {
      this.state = state
    }
  }

  data class TestState(var value: String = "default")

  @Test fun directoryBasedStorage() {
    loadAndUseProject(tempDirManager, {
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", iprFileContent)
      it.path
    }) { project ->
      val testComponent = test(project as ProjectEx)

      assertThat(project.basePath).isEqualTo(PathUtil.getParentPath((PathUtil.getParentPath(project.projectFilePath!!))))

      // test reload on external change
      val file = File(project.stateStore.stateStorageManager.expandMacros(PROJECT_FILE))
      file.writeText(file.readText().replace("""<option name="value" value="foo" />""", """<option name="value" value="newValue" />"""))

      project.baseDir.refresh(false, true)
      (ProjectManager.getInstance() as StoreAwareProjectManager).flushChangedAlarm()

      assertThat(testComponent.state).isEqualTo(TestState("newValue"))
    }
  }

  @Test fun fileBasedStorage() {
    loadAndUseProject(tempDirManager, { it.writeChild("test${ProjectFileType.DOT_DEFAULT_EXTENSION}", iprFileContent).path }) { project ->
      test(project as ProjectEx)

      assertThat(project.basePath).isEqualTo(PathUtil.getParentPath(project.projectFilePath!!))
    }
  }

  @Test fun saveProjectName() {
    loadAndUseProject(tempDirManager, {
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", iprFileContent)
      it.path
    }) { project ->
      val store = project.stateStore as IProjectStore
      assertThat(store.nameFile).doesNotExist()
      val newName = "Foo"
      val oldName = project.name
      (project as ProjectImpl).setProjectName(newName)
      project.saveStore()
      assertThat(store.nameFile).hasContent(newName)

      project.setProjectName(oldName)
      project.saveStore()
      assertThat(store.nameFile).doesNotExist()
    }
  }

  @Test fun `saved project name must be not removed just on open`() {
    val name = "saved project name must be not removed just on open"
    loadAndUseProject(tempDirManager, {
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", iprFileContent)
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/.name", name)
      it.path
    }) { project ->
      val store = project.stateStore as IProjectStore
      assertThat(store.nameFile).hasContent(name)

      project.saveStore()
      assertThat(store.nameFile).hasContent(name)

      (project as ProjectImpl).setProjectName(name)
      project.saveStore()
      assertThat(store.nameFile).hasContent(name)

      project.setProjectName("foo")
      project.saveStore()
      assertThat(store.nameFile).hasContent("foo")

      project.setProjectName(name)
      project.saveStore()
      assertThat(store.nameFile).doesNotExist()
    }
  }

  private fun test(project: Project): TestComponent {
    val testComponent = TestComponent()
    project.stateStore.initComponent(testComponent, true)
    assertThat(testComponent.state).isEqualTo(TestState("customValue"))

    testComponent.state!!.value = "foo"
    project.saveStore()

    val file = Paths.get(project.stateStore.stateStorageManager.expandMacros(PROJECT_FILE))
    assertThat(file).isRegularFile()
    // test exact string - xml prolog, line separators, indentation and so on must be exactly the same
    // todo get rid of default component states here
    assertThat(file.readText()).startsWith(iprFileContent.replace("customValue", "foo").replace("</project>", ""))

    return testComponent
  }
}