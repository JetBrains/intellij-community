// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanel

/**
 * Contributes only non-modal startup UI policy for welcome-experience projects.
 *
 * Core welcome capability detection (for example [ProjectFrameCapability.WELCOME_EXPERIENCE])
 * is intentionally provided by `WelcomeScreenProjectFrameCapabilitiesProvider` in `platform-impl`.
 * Keeping capability computation in `platform-impl` avoids coupling the foundational welcome
 * project semantics to this module's loading.
 *
 * This provider must remain a consumer of aggregated capabilities instead of re-implementing
 * welcome-project detection. The UI policy is enabled only when
 * [ProjectFrameCapability.WELCOME_EXPERIENCE] is already present. This prevents predicate
 * duplication and keeps module responsibilities clear:
 * - `platform-impl`: core frame capabilities
 * - `non-modal-welcome-screen`: non-modal pane/toolwindow startup policy
 */
internal class NonModalWelcomeScreenProjectFrameCapabilitiesProvider : ProjectFrameCapabilitiesProvider {
  override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
    return emptySet()
  }

  override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
    if (!capabilities.contains(ProjectFrameCapability.WELCOME_EXPERIENCE)) {
      return null
    }
    if (project.getUserData(ProjectFrameCapabilitiesService.TOOL_WINDOW_INIT) == true) {
      val properties = PropertiesComponent.getInstance(project)
      if (properties.getValue(FIRST_RUN_PROPERTY) == null) {
        properties.setValue(FIRST_RUN_PROPERTY, true)
        return NON_MODAL_WELCOME_SCREEN_FIRST_RUN_UI_POLICY
      }
    }
    return NON_MODAL_WELCOME_SCREEN_UI_POLICY
  }
}

/**
 * Marks a welcome project that got [NON_MODAL_WELCOME_SCREEN_FIRST_RUN_UI_POLICY] once.
 * Increment the suffix when the exclusive tool window set changes, so that existing welcome projects get the new set once.
 */
private const val FIRST_RUN_PROPERTY = "NON_MODAL_WELCOME_SCREEN_FIRST_RUN_V2"

private val NON_MODAL_WELCOME_SCREEN_UI_POLICY =ProjectFrameUiPolicy(
  projectPaneToActivateId = WelcomeScreenProjectProvider.getProjectPaneToActivateId() ?: WelcomeScreenLeftPanel.ID,
  activatesProjectViewOnStartup = true,
)

private val NON_MODAL_WELCOME_SCREEN_FIRST_RUN_UI_POLICY =
  NON_MODAL_WELCOME_SCREEN_UI_POLICY.copy(toolWindowIdsToExclusiveShowing = WelcomeScreenProjectProvider.getToolWindowIdsToExclusiveShowing())