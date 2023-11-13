// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
internal object TrustedProjects {

  fun isProjectTrusted(locatedProject: LocatedProject): Boolean {
    return getProjectTrustedState(locatedProject) == ThreeState.YES
  }

  fun getProjectTrustedState(locatedProject: LocatedProject): ThreeState {
    val explicitTrustedState = getExplicitTrustedProjectState(locatedProject)
    if (explicitTrustedState != ThreeState.UNSURE) {
      return explicitTrustedState
    }
    return getImplicitTrustedProjectState(locatedProject)
  }

  fun setProjectTrusted(locatedProject: LocatedProject, isTrusted: Boolean) {
    val oldState = getExplicitTrustedProjectState(locatedProject)
    setExplicitTrustedProjectState(locatedProject, isTrusted)
    val newState = getExplicitTrustedProjectState(locatedProject)
    val syncPublisher = ApplicationManager.getApplication().messageBus.syncPublisher(TrustedProjectsListener.TOPIC)
    if (oldState != newState) {
      when (isTrusted) {
        true -> syncPublisher.onProjectTrusted(locatedProject)
        else -> syncPublisher.onProjectUntrusted(locatedProject)
      }
    }
  }

  private fun getExplicitTrustedProjectState(locatedProject: LocatedProject): ThreeState {
    val trustedPaths = TrustedPaths.getInstance()
    val trustedStates = locatedProject.projectRoots.map { trustedPaths.getProjectPathTrustedState(it) }
    return mergeTrustedProjectStates(trustedStates)
  }

  private fun setExplicitTrustedProjectState(locatedProject: LocatedProject, isTrusted: Boolean) {
    val trustedPaths = TrustedPaths.getInstance()
    for (projectRoot in locatedProject.projectRoots) {
      trustedPaths.setProjectPathTrusted(projectRoot, isTrusted)
    }
  }

  /**
   * Checks that IDEA is loaded in safe mode. In this mode, trusted checks aren't needed at all.
   */
  fun isTrustedCheckDisabled(): Boolean =
    ApplicationManager.getApplication().isUnitTestMode ||
    ApplicationManager.getApplication().isHeadlessEnvironment ||
    java.lang.Boolean.getBoolean("idea.trust.all.projects")

  private fun isTrustedCheckDisabledForProduct(): Boolean =
    isTrustedCheckDisabled() ||
    java.lang.Boolean.getBoolean("idea.trust.disabled")

  private fun getImplicitTrustedProjectState(locatedProject: LocatedProject?): ThreeState {
    if (isTrustedCheckDisabledForProduct()) {
      return ThreeState.YES
    }
    if (LightEdit.owns(locatedProject?.project)) {
      return ThreeState.YES
    }
    val trustedPaths = TrustedPathsSettings.getInstance()
    if (locatedProject != null && locatedProject.projectRoots.all { trustedPaths.isPathTrusted(it) }) {
      TrustedProjectsStatistics.PROJECT_IMPLICITLY_TRUSTED_BY_PATH.log(locatedProject.project)
      return ThreeState.YES
    }
    return ThreeState.UNSURE
  }

  private fun mergeTrustedProjectStates(states: List<ThreeState>): ThreeState {
    return states.fold(ThreeState.YES) { acc, it ->
      when {
        acc == ThreeState.UNSURE -> ThreeState.UNSURE
        it == ThreeState.UNSURE -> ThreeState.UNSURE
        acc == ThreeState.NO -> ThreeState.NO
        it == ThreeState.NO -> ThreeState.NO
        else -> ThreeState.YES
      }
    }
  }
}