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
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.testFramework.FixtureRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.runInEdtAndWait
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsNull.notNullValue
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
  val tempDirManager = TemporaryDirectory()
  val fixtureManager = FixtureRule()

  private val ruleChain = RuleChain(tempDirManager, fixtureManager)

  public Rule fun getChain(): RuleChain = ruleChain

  private fun getIprFileContent() =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
      "<project version=\"4\">" +
      "  <component name=\"TestComponent\">" +
      "    <option name=\"VALUE\" value=\"true\"/>" +
      "  </component>" +
      "</project>"

  State(name = "TestComponent", storages = arrayOf(Storage(file = StoragePathMacros.PROJECT_FILE)))
  private class TestComponent : PersistentStateComponent<DataBean> {
    private var _state: DataBean? = null

    override fun getState() = _state

    override fun loadState(state: DataBean) {
      _state = state
    }
  }

  data class DataBean(var value: Boolean = false)

  public Test fun testLoadFromDirectoryStorage() {
    loadProject(tempDirManager, true, { FileUtil.writeToFile(File(File(it, Project.DIRECTORY_STORE_FOLDER), "misc.xml"), getIprFileContent()) }) {
      val testComponent = TestComponent()
      it.stateStore.initComponent(testComponent, true)
      assertThat(testComponent.getState(), notNullValue())
    }
  }

  public Test fun testLoadFromOldStorage() {
    loadProject(tempDirManager, false, { FileUtil.writeToFile(it, getIprFileContent()) }) {
      val testComponent = TestComponent()
      it.stateStore.initComponent(testComponent, true)
      assertThat(testComponent.getState(), notNullValue())
    }
  }
}