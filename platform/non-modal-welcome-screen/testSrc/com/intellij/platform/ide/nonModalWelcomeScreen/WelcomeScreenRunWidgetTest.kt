// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks that the main toolbar run widget stays hidden in a welcome-screen project.
 */
@TestApplication
internal class WelcomeScreenRunWidgetTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val projectFixture = projectFixture()

  @Test
  fun `welcome project hides the run widget`() {
    val project = projectFixture.get()
    markAsWelcomeProject(project)

    assertFalse(updateRunWidget(project).isVisible)
  }

  @Test
  fun `regular project keeps the run widget`() {
    assertTrue(updateRunWidget(projectFixture.get()).isVisible)
  }

  // A fresh mask replaces the provider list, so the service drops the capabilities it cached for the project.
  private fun markAsWelcomeProject(welcomeProject: Project) {
    val provider = object : ProjectFrameCapabilitiesProvider {
      override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
        return if (project === welcomeProject) setOf(ProjectFrameCapability.WELCOME_EXPERIENCE) else emptySet()
      }

      override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
    }
    ExtensionTestUtil.maskExtensions(ProjectFrameCapabilitiesService.EP_NAME, listOf(provider), disposable)
  }

  private fun updateRunWidget(project: Project): Presentation {
    val action = ActionManager.getInstance().getAction("NewUiRunWidget")
    assertNotNull(action, "The run widget action is not registered")
    val dataContext = SimpleDataContext.getProjectContext(project)
    val event = AnActionEvent.createEvent(dataContext, null, ActionPlaces.MAIN_TOOLBAR, ActionUiKind.TOOLBAR, null)
    action.update(event)
    return event.presentation
  }
}
