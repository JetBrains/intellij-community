// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@TestApplication
@RunInEdt
class ProjectFrameCapabilitiesServiceTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun uiPolicyUsesAggregatedCapabilitiesAndCapabilitiesAreCached() {
    val uiPolicyRef = AtomicReference(
      ProjectFrameUiPolicy(
        projectPaneToActivateId = "pane-1",
        toolWindowLayoutProfileId = "layout-profile-1",
      )
    )
    val capabilitiesComputationCount = AtomicInteger()
    val capabilitiesRef = AtomicReference(setOf(ProjectFrameCapability.WELCOME_EXPERIENCE))
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(
        object : ProjectFrameCapabilitiesProvider {
          override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
            capabilitiesComputationCount.incrementAndGet()
            return capabilitiesRef.get()
          }

          override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
            return null
          }
        },
        object : ProjectFrameCapabilitiesProvider {
          override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
            return emptySet()
          }

          override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
            return uiPolicyRef.get().takeIf { capabilities.contains(ProjectFrameCapability.WELCOME_EXPERIENCE) }
          }
        },
      ),
      disposable,
    )

    val service = ProjectFrameCapabilitiesService()
    assertThat(service.getUiPolicy(project)?.projectPaneToActivateId).isEqualTo("pane-1")
    assertThat(service.getUiPolicy(project)?.toolWindowLayoutProfileId).isEqualTo("layout-profile-1")

    uiPolicyRef.set(
      ProjectFrameUiPolicy(
        projectPaneToActivateId = "pane-2",
        toolWindowLayoutProfileId = "layout-profile-2",
      )
    )
    assertThat(service.getUiPolicy(project)?.projectPaneToActivateId).isEqualTo("pane-2")
    assertThat(service.getUiPolicy(project)?.toolWindowLayoutProfileId).isEqualTo("layout-profile-2")

    assertThat(service.getAll(project)).isEqualTo(setOf(ProjectFrameCapability.WELCOME_EXPERIENCE))
    assertThat(service.has(project, ProjectFrameCapability.WELCOME_EXPERIENCE)).isTrue()

    capabilitiesRef.set(emptySet())
    assertThat(service.getAll(project)).isEqualTo(setOf(ProjectFrameCapability.WELCOME_EXPERIENCE))
    assertThat(capabilitiesComputationCount.get()).isEqualTo(1)
  }

  @Test
  fun supportsSuppressProjectViewCapability() {
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(
        object : ProjectFrameCapabilitiesProvider {
          override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
            return setOf(ProjectFrameCapability.SUPPRESS_PROJECT_VIEW)
          }

          override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
            return null
          }
        },
      ),
      disposable,
    )

    val service = ProjectFrameCapabilitiesService()
    assertThat(service.has(project, ProjectFrameCapability.SUPPRESS_PROJECT_VIEW)).isTrue()
    assertThat(service.getAll(project)).isEqualTo(setOf(ProjectFrameCapability.SUPPRESS_PROJECT_VIEW))
  }

  @Test
  fun supportsSuppressBackgroundActivitiesCapability() {
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(
        object : ProjectFrameCapabilitiesProvider {
          override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
            return setOf(ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES)
          }

          override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
            return null
          }
        },
      ),
      disposable,
    )

    val service = ProjectFrameCapabilitiesService()
    assertThat(service.has(project, ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES)).isTrue()
    assertThat(service.getAll(project)).isEqualTo(setOf(ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES))
  }

  @Test
  fun supportsSuppressIndexingActivitiesCapability() {
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(
        object : ProjectFrameCapabilitiesProvider {
          override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
            return setOf(ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES)
          }

          override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
            return null
          }
        },
      ),
      disposable,
    )

    val service = ProjectFrameCapabilitiesService()
    assertThat(service.has(project, ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES)).isTrue()
    assertThat(service.getAll(project)).isEqualTo(setOf(ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES))
  }
}
