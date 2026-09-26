// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.configurationStore

import com.intellij.ide.actions.convertToDirectoryBasedFormat
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.project.stateStore
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.loadProjectAndCheckResults
import com.intellij.util.io.FileTextMatcher
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import com.intellij.util.io.directoryContentOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class SaveAsDirectoryBasedFormatActionTest : BareTestFixtureTestCase() {
  @get:Rule
  val tempDirectory = TemporaryDirectory()

  @Test
  fun `convert sample project to directory based format`() = runBlocking {
    val projectFile = Path.of(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject-ipr/sampleProject.ipr")
    val projectDir = Path.of(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    loadProjectAndCheckResults(listOf(projectFile), tempDirectory) { project ->
      val store = project.stateStore
      val baseDir = store.projectFilePath.parent
      val ideaDir = baseDir.resolve(DIRECTORY_STORE_FOLDER)
      convertToDirectoryBasedFormat(project = project, store = store, baseDir = baseDir, ideaDir = ideaDir)
      project.stateStore.save()

      val expected = directoryContentOf(projectDir).mergeWith(directoryContent { file("sampleProject.ipr") })
      Path.of(project.basePath!!).assertMatches(expected, FileTextMatcher.ignoreLineSeparators())
    }
  }
}
