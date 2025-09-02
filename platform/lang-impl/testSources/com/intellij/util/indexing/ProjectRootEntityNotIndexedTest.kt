// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.find.TextSearchService
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.CommonProcessors
import com.intellij.workspaceModel.ide.ProjectRootEntity
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@TestApplication
class ProjectRootEntityNotIndexedTest {

  companion object {
    val projectFixture = multiverseProjectFixture(
      openProjectTask = OpenProjectTask.build().copy(
        runConfigurators = true
      ), openAfterCreation = true) {
      dir("src") {
        file("Test.java", "public class Test {}")
      }
      dir("nestedDir") {
        file("nestedFile.txt", "Test content for nested file")
      }
      file("topLevelFile.txt", "Test content for top level file")
    }
  }

  @Test
  @Disabled("Need to enable ProjectRootProjectActivity")
  fun `ProjectRootEntity not indexed`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val workspaceModel = project.serviceAsync<WorkspaceModel>()
    val projectRootEntity = workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()
    assertTrue(projectRootEntity.isNotEmpty(), "ProjectRootEntity was not found")

    readAction {
      IndexingTestUtil.waitUntilIndexesAreReady(project)
      assertTrue(findFilesWithText("Test content for nested file", project).isEmpty(), "Nested file was indexed")
      assertTrue(findFilesWithText("Test content for top level file", project).isEmpty(), "Top level file was indexed")
      assertTrue(findFilesWithText("public class Test", project).isEmpty(), "Class was indexed")
    }
  }

  private fun findFilesWithText(text: String, project: Project): Collection<VirtualFile> {
    val service = ApplicationManager.getApplication().service<TextSearchService>()
    val processor = CommonProcessors.CollectProcessor<VirtualFile>()
    service.processFilesWithText(text, processor, GlobalSearchScope.allScope(project))
    return processor.results
  }
}