// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.TemporaryDirectoryExtension
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.useProjectAsync
import com.intellij.workspaceModel.ide.ProjectRootEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class AbstractNewProjectStepTest {
  @RegisterExtension
  private val tempDir = TemporaryDirectoryExtension()

  @Test
  @Timeout(30)
  @RegistryKey(key = "ide.create.project.root.entity", value = "true")
  fun `project root entity is created when a project is generated`(): Unit = timeoutRunBlocking(timeout = 30.seconds) {
    val projectDir = tempDir.newPath("project")
    val project = withContext(Dispatchers.EDT) {
      AbstractNewProjectStep.doGenerateProject<Unit>(null, projectDir.toString(), null, Unit)
    }

    requireNotNull(project).useProjectAsync { openedProject ->
      val roots = openedProject.workspaceModel.currentSnapshot.entities<ProjectRootEntity>().toList()
      assertThat(roots.map { it.root }).containsExactly(projectDir.toVirtualFileUrl(openedProject))
    }
  }
}

private fun Path.toVirtualFileUrl(project: Project): VirtualFileUrl =
  toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
