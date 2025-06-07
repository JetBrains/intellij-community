// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.testFramework.fixtures.impl

import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectStartupActivity
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.testFramework.fixtures.MultiProjectTestFixture
import com.intellij.openapi.externalSystem.util.DEFAULT_SYNC_TIMEOUT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.withProjectAsync
import org.jetbrains.annotations.Nls
import org.junit.jupiter.api.Assertions
import java.nio.file.Path

class MultiProjectTestFixtureImpl: MultiProjectTestFixture {

  override suspend fun openProject(projectPath: Path): Project {
    return awaitOpenProjectConfiguration {
      openProjectAsync(projectPath, UnlinkedProjectStartupActivity())
    }
  }

  override suspend fun linkProject(project: Project, projectPath: Path, systemId: ProjectSystemId) {
    val extension = ExternalSystemUnlinkedProjectAware.EP_NAME.findFirstSafe { it.systemId == systemId }
    Assertions.assertNotNull(extension) {
      "Cannot find applicable extension to link $systemId project"
    }
    awaitProjectConfiguration(project) {
      extension!!.linkAndLoadProjectAsync(project, projectPath.toCanonicalPath())
    }
  }

  override suspend fun unlinkProject(project: Project, projectPath: Path, systemId: ProjectSystemId) {
    val extension = ExternalSystemUnlinkedProjectAware.EP_NAME.findFirstSafe { it.systemId == systemId }
    Assertions.assertNotNull(extension) {
      "Cannot find applicable extension to link $systemId project"
    }
    awaitProjectConfiguration(project) {
      extension!!.unlinkProject(project, projectPath.toCanonicalPath())
    }
  }

  override suspend fun awaitOpenProjectConfiguration(openProject: suspend () -> Project): Project {
    return openProject().withProjectAsync { project ->
      TestObservation.awaitConfiguration(project, DEFAULT_SYNC_TIMEOUT)
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
    }
  }

  override suspend fun <R> awaitProjectConfiguration(project: Project, action: suspend () -> R): R {
    return project.trackActivity(TestProjectConfigurationActivityKey, action).also {
      TestObservation.awaitConfiguration(project, DEFAULT_SYNC_TIMEOUT)
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
    }
  }

  private object TestProjectConfigurationActivityKey : ActivityKey {
    override val presentableName: @Nls String
      get() = "The test multi-project configuration"
  }
}