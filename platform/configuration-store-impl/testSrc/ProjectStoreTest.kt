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
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

fun createProject(tempDirManager: TemporaryDirectory, task: (Project) -> Unit) {
  createOrLoadProject(tempDirManager, task)
}

fun loadProject(tempDirManager: TemporaryDirectory, projectCreator: ((VirtualFile) -> String)? = null, task: (Project) -> Unit) {
  createOrLoadProject(tempDirManager, task, projectCreator)
}

private fun createOrLoadProject(tempDirManager: TemporaryDirectory, task: (Project) -> Unit, projectCreator: ((VirtualFile) -> String)? = null) {
  runInEdtAndWait {
    var filePath: String
    if (projectCreator == null) {
      filePath = tempDirManager.newDirectory("test${ProjectFileType.DOT_DEFAULT_EXTENSION}").systemIndependentPath
    }
    else {
      filePath = runWriteAction { projectCreator(tempDirManager.newVirtualDirectory()) }
    }

    val projectManager = ProjectManagerEx.getInstanceEx() as ProjectManagerImpl
    var project = if (projectCreator == null) projectManager.newProject(null, filePath, true, false, false)!! else projectManager.loadProject(filePath)!!
    try {
      projectManager.openTestProject(project)
      task(project)
    }
    finally {
      projectManager.closeProject(project, false, true, false)
    }
  }
}

class ProjectStoreTest {
  companion object {
    ClassRule val projectRule = ProjectRule()
  }

  val tempDirManager = TemporaryDirectory()

  private val ruleChain = RuleChain(tempDirManager)

  public Rule fun getChain(): RuleChain = ruleChain

  Language("XML")
  private val iprFileContent =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<project version=\"4\">\n" +
      "  <component name=\"AATestComponent\">\n" +
      "    <option name=\"value\" value=\"customValue\" />\n" +
      "  </component>\n" +
      "</project>"

  State(name = "AATestComponent", storages = arrayOf(Storage(file = StoragePathMacros.PROJECT_FILE)))
  private class TestComponent : PersistentStateComponent<TestState> {
    private var state: TestState? = null

    override fun getState() = state

    override fun loadState(state: TestState) {
      this.state = state
    }
  }

  data class TestState(var value: String = "default")

  public Test fun directoryBasedStorage() {
    loadProject(tempDirManager, {
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", iprFileContent)
      it.path
    }) { project ->
      val testComponent = test(project)

      // test reload on external change
      val file = File(project.stateStore.getStateStorageManager().expandMacros(StoragePathMacros.PROJECT_FILE))
      file.writeText(file.readText().replace("""<option name="value" value="foo" />""", """<option name="value" value="newValue" />"""))

      project.getBaseDir().refresh(false, true)
      (ProjectManager.getInstance() as StoreAwareProjectManager).flushChangedAlarm()

      assertThat(testComponent.getState()).isEqualTo(TestState("newValue"))
    }
  }

  public Test fun fileBasedStorage() {
    loadProject(tempDirManager, { it.writeChild("test${ProjectFileType.DOT_DEFAULT_EXTENSION}", iprFileContent).path }) { project ->
      test(project)
    }
  }

  private fun test(project: Project): TestComponent {
    val testComponent = TestComponent()
    project.stateStore.initComponent(testComponent, true)
    assertThat(testComponent.getState()).isEqualTo(TestState("customValue"))

    testComponent.getState()!!.value = "foo"
    project.saveStore()

    val file = File(project.stateStore.getStateStorageManager().expandMacros(StoragePathMacros.PROJECT_FILE))
    assertThat(file).isFile()
    // test exact string - xml prolog, line separators, indentation and so on must be exactly the same
    // todo get rid of default component states here
    assertThat(file.readText()).startsWith(iprFileContent.replace("customValue", "foo").replace("</project>", ""))

    return testComponent
  }
}