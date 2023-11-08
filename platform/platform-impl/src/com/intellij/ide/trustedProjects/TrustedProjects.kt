// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.impl.TrustedProjectSettings
import com.intellij.ide.impl.TrustedProjectsStatistics
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

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
    val implicitTrustedState = getImplicitTrustedProjectState(locatedProject)
    if (implicitTrustedState != ThreeState.UNSURE) {
      return implicitTrustedState
    }
    val legacyTrustedState = getLegacyTrustedProjectState(locatedProject)
    if (legacyTrustedState != ThreeState.UNSURE) {
      return legacyTrustedState
    }
    return ThreeState.UNSURE
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

  fun isTrustedCheckDisabled(): Boolean = ApplicationManager.getApplication().isUnitTestMode ||
                                          ApplicationManager.getApplication().isHeadlessEnvironment ||
                                          java.lang.Boolean.getBoolean("idea.trust.all.projects")

  private fun isTrustedCheckDisabledForProduct(): Boolean = java.lang.Boolean.getBoolean("idea.trust.disabled")

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use isProjectTrusted instead")
  fun isProjectImplicitlyTrusted(projectDir: Path?, project: Project?): Boolean {
    val locatedProject = when {
      projectDir != null -> TrustedProjectsLocator.locateProject(projectDir, project)
      project != null -> TrustedProjectsLocator.locateProject(project)
      else -> null
    }
    return getImplicitTrustedProjectState(locatedProject) == ThreeState.YES
  }

  private fun getImplicitTrustedProjectState(locatedProject: LocatedProject?): ThreeState {
    if (isTrustedCheckDisabled() || isTrustedCheckDisabledForProduct()) {
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

  @Suppress("DEPRECATION")
  private fun getLegacyTrustedProjectState(locatedProject: LocatedProject): ThreeState {
    val project = locatedProject.project
    if (project != null) {
      val trustedProjectSettings = project.service<TrustedProjectSettings>()
      val legacyTrustedState = trustedProjectSettings.trustedState
      if (legacyTrustedState != ThreeState.UNSURE) {
        // we were asking about this project in the previous IDE version => migrate
        setExplicitTrustedProjectState(locatedProject, legacyTrustedState.toBoolean())
        trustedProjectSettings.trustedState = ThreeState.UNSURE
        return legacyTrustedState
      }
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