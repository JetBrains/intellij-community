// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
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

    return NON_MODAL_WELCOME_SCREEN_UI_POLICY
  }
}

private val NON_MODAL_WELCOME_SCREEN_UI_POLICY = ProjectFrameUiPolicy(
  projectPaneToActivateId = WelcomeScreenLeftPanel.ID,
  startupToolWindowIdToActivate = ToolWindowId.PROJECT_VIEW,
)
