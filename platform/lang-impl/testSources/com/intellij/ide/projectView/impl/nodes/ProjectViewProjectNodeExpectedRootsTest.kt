// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes

import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.registerProjectRoot
import com.intellij.workspaceModel.ide.unregisterProjectRoot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The project open registers the project root entity after the first update of the Project View.
 * The view shows the project directory before the entity exists, with the node it keeps after the entity arrives.
 */
@TestApplication
class ProjectViewProjectNodeExpectedRootsTest {
  companion object {
    val projectFixture = multiverseProjectFixture(openAfterCreation = true) {
      file("a.txt", "a")
    }
  }

  @Test
  fun `the project directory is the top-level root before and after its project root entity exists`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val projectDir = project.guessProjectDir()!!
    val projectPath = projectDir.toNioPath()
    val urlManager = project.serviceAsync<WorkspaceModel>().getVirtualFileUrlManager()
    unregisterProjectRoot(project, projectPath.toVirtualFileUrl(urlManager))
    assertThat(projectRootEntities(project)).isEmpty()

    val nodeBefore = projectDirectoryNode(project)
    assertThat(nodeBefore.virtualFile).isEqualTo(projectDir)

    registerProjectRoot(project, projectPath)
    assertThat(projectRootEntities(project)).hasSize(1)

    assertThat(projectDirectoryNode(project)).isEqualTo(nodeBefore)
  }

  private suspend fun projectRootEntities(project: Project): List<ProjectRootEntity> {
    return project.serviceAsync<WorkspaceModel>().currentSnapshot.entities(ProjectRootEntity::class.java).toList()
  }

  private suspend fun projectDirectoryNode(project: Project): PsiDirectoryNode {
    return readAction {
      ProjectViewProjectNode(project, ViewSettings.DEFAULT).children.filterIsInstance<PsiDirectoryNode>().single()
    }
  }
}
