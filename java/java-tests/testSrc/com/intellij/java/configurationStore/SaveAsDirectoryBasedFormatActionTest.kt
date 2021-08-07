// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.ide.actions.SaveAsDirectoryBasedFormatAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.loadProjectAndCheckResults
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import com.intellij.util.io.directoryContentOf
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class SaveAsDirectoryBasedFormatActionTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @get:Rule
  val tempDirectory = TemporaryDirectory()

  @Test
  fun `convert sample project to directory based format`() {
    val iprProjectFile = Path.of(PathManagerEx.getCommunityHomePath(), "jps", "model-serialization", "testData", "sampleProject-ipr", "sampleProject.ipr")
    val projectDir = Path.of(PathManagerEx.getCommunityHomePath(), "jps", "model-serialization", "testData", "sampleProject")
    loadProjectAndCheckResults(listOf(iprProjectFile), tempDirectory) { project ->
      runWriteActionAndWait {
        SaveAsDirectoryBasedFormatAction.convertToDirectoryBasedFormat(project)
      }
      project.stateStore.save()
      Path.of(project.basePath!!).assertMatches(directoryContentOf(projectDir).mergeWith(directoryContent {
        file("sampleProject.ipr")
      }))
    }
  }
}