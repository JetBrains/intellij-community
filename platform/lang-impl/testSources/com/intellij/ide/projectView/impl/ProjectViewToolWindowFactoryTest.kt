// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RunInEdt
class ProjectViewToolWindowFactoryTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun isNotApplicableWhenProjectViewIsSuppressed() {
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(provider(setOf(ProjectFrameCapability.SUPPRESS_PROJECT_VIEW))),
      disposable,
    )

    runBlocking {
      assertThat(ProjectViewToolWindowFactory().isApplicableAsync(project)).isFalse()
    }
  }

  @Test
  fun isApplicableWhenProjectViewIsNotSuppressed() {
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(provider(emptySet())),
      disposable,
    )

    runBlocking {
      assertThat(ProjectViewToolWindowFactory().isApplicableAsync(project)).isTrue()
    }
  }

  private fun provider(capabilities: Set<ProjectFrameCapability>): ProjectFrameCapabilitiesProvider {
    return object : ProjectFrameCapabilitiesProvider {
      override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
        return capabilities
      }

      override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
        return null
      }
    }
  }
}
