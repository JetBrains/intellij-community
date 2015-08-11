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
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.runInEdtAndWait
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.StringStartsWith.startsWith
import org.hamcrest.io.FileMatchers.anExistingFile
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

fun createProject(tempDirManager: TemporaryDirectory, task: (Project) -> Unit) {
  createOrLoadProject(tempDirManager, task)
}

fun loadProject(tempDirManager: TemporaryDirectory, directoryBased: Boolean = true, projectCreator: ((File) -> Unit)? = null, task: (Project) -> Unit) {
  createOrLoadProject(tempDirManager, task, directoryBased, projectCreator)
}

private fun createOrLoadProject(tempDirManager: TemporaryDirectory, task: (Project) -> Unit, directoryBased: Boolean = true, projectCreator: ((File) -> Unit)? = null) {
  var projectFile = tempDirManager.newDirectory()
  if (!directoryBased) {
    projectFile = File(projectFile, "test${ProjectFileType.DOT_DEFAULT_EXTENSION}")
  }

  projectCreator?.invoke(projectFile)
  runInEdtAndWait {
    val projectManager = ProjectManagerEx.getInstanceEx()
    var project = if (projectCreator == null) (projectManager as ProjectManagerImpl).newProject(null, projectFile.systemIndependentPath, true, false, false)!! else projectManager.loadProject(projectFile.path)!!
    try {
      task(project)
    }
    finally {
      runWriteAction { Disposer.dispose(project) }
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
    loadProject(tempDirManager, true, { FileUtil.writeToFile(File(File(it, Project.DIRECTORY_STORE_FOLDER), "misc.xml"), iprFileContent) }) {project ->
      test(project)
    }
  }

  public Test fun fileBasedStorage() {
    loadProject(tempDirManager, false, { FileUtil.writeToFile(it, iprFileContent) }) {project ->
      test(project)
    }
  }

  private fun test(project: Project) {
    val testComponent = TestComponent()
    project.stateStore.initComponent(testComponent, true)
    assertThat(testComponent.getState(), equalTo(TestState("customValue")))

    testComponent.getState()!!.value = "foo"
    StoreUtil.save(project.stateStore, project)

    val file = File(project.stateStore.getStateStorageManager().expandMacros(StoragePathMacros.PROJECT_FILE))
    assertThat(file, anExistingFile())
    // test exact string - xml prolog, line separators, indentation and so on must be exactly the same
    // todo get rid of default component states here
    assertThat(file.readText(), startsWith(iprFileContent.replace("customValue", "foo").replace("</project>", "")))
  }
}