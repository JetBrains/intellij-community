// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.impl.TrustedProjectsStatistics
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TrustedProjects {
  fun isProjectTrusted(locatedProject: LocatedProject): Boolean = getProjectTrustedState(locatedProject) == ThreeState.YES

  fun getProjectTrustedState(locatedProject: LocatedProject): ThreeState {
    val explicitTrustedState = TrustedPaths.getInstance().getProjectTrustedState(locatedProject)
    return when {
      explicitTrustedState != ThreeState.UNSURE -> explicitTrustedState
      isTrustedCheckDisabledForProduct() -> ThreeState.YES
      LightEdit.owns(locatedProject.project) -> ThreeState.YES
      TrustedPathsSettings.getInstance().isProjectTrusted(locatedProject) -> {
        TrustedProjectsStatistics.PROJECT_IMPLICITLY_TRUSTED_BY_PATH.log(locatedProject.project)
        ThreeState.YES
      }
      else -> ThreeState.UNSURE
    }
  }

  fun setProjectTrusted(locatedProject: LocatedProject, isTrusted: Boolean) {
    val trustedPaths = TrustedPaths.getInstance()
    val oldState = trustedPaths.getProjectTrustedState(locatedProject)
    trustedPaths.setProjectTrustedState(locatedProject, isTrusted)
    val newState = trustedPaths.getProjectTrustedState(locatedProject)
    if (oldState != newState) {
      val syncPublisher = ApplicationManager.getApplication().messageBus.syncPublisher(TrustedProjectsListener.TOPIC)
      when (isTrusted) {
        true -> syncPublisher.onProjectTrusted(locatedProject)
        else -> syncPublisher.onProjectUntrusted(locatedProject)
      }
    }
  }

  /**
   * Checks that IDEA is loaded with safe environment. In this mode, trusted checks aren't needed at all.
   */
  fun isTrustedCheckDisabled(): Boolean {
    val app = ApplicationManager.getApplication()
    return app.isUnitTestMode || app.isHeadlessEnvironment || java.lang.Boolean.getBoolean("idea.trust.all.projects")
  }

  private fun isTrustedCheckDisabledForProduct(): Boolean = isTrustedCheckDisabled() || java.lang.Boolean.getBoolean("idea.trust.disabled")
}