// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.impl.TrustedProjectsStatistics
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.util.ThreeState
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

object TrustedProjects {
  @ApiStatus.Internal
  const val TRUST_HEADLESS_DISABLED_PROPERTY: String = "idea.trust.headless.disabled"

  @JvmStatic
  fun isProjectTrusted(project: Project): Boolean = isProjectTrusted(TrustedProjectsLocator.locateProject(project))

  @JvmStatic
  fun setProjectTrusted(project: Project, isTrusted: Boolean) {
    setProjectTrusted(TrustedProjectsLocator.locateProject(project), isTrusted)
  }

  @ApiStatus.Internal
  fun getProjectTrustedState(project: Project): ThreeState = getProjectTrustedState(TrustedProjectsLocator.locateProject(project))

  @JvmStatic
  fun isProjectTrusted(path: Path): Boolean = isProjectTrusted(path, project = null)

  @JvmStatic
  fun setProjectTrusted(path: Path, isTrusted: Boolean) {
    setProjectTrusted(path, project = null, isTrusted)
  }

  @ApiStatus.Internal
  fun getProjectTrustedState(path: Path): ThreeState = getProjectTrustedState(path, project = null)

  @JvmStatic
  fun isProjectTrusted(path: Path, project: Project?): Boolean = isProjectTrusted(TrustedProjectsLocator.locateProject(path, project))

  @JvmStatic
  fun setProjectTrusted(path: Path, project: Project?, isTrusted: Boolean) {
    setProjectTrusted(TrustedProjectsLocator.locateProject(path, project), isTrusted)
  }

  @ApiStatus.Internal
  fun getProjectTrustedState(path: Path, project: Project?): ThreeState = getProjectTrustedState(TrustedProjectsLocator.locateProject(path, project))

  @ApiStatus.Internal
  fun isProjectTrusted(locatedProject: LocatedProject): Boolean = getProjectTrustedState(locatedProject) == ThreeState.YES

  @ApiStatus.Internal
  fun getProjectTrustedState(locatedProject: LocatedProject): ThreeState {
    val explicitTrustedState = TrustedPaths.getInstance().getProjectTrustedState(locatedProject)
    return when {
      isTrustedCheckDisabledForProduct() -> ThreeState.YES
      // before the explicit state: a recorded answer for a system path is stale and must not win
      isSystemTrusted(locatedProject) -> ThreeState.YES
      explicitTrustedState != ThreeState.UNSURE -> explicitTrustedState
      LightEdit.owns(locatedProject.project) && locatedProject.project === LightEditUtil.getProjectIfCreated() -> ThreeState.YES
      TrustedPathsSettings.getInstance().isProjectTrusted(locatedProject) -> {
        TrustedProjectsStatistics.PROJECT_IMPLICITLY_TRUSTED_BY_PATH.log(locatedProject.project)
        ThreeState.YES
      }
      else -> ThreeState.UNSURE
    }
  }

  @ApiStatus.Internal
  fun setProjectTrusted(locatedProject: LocatedProject, isTrusted: Boolean) {
    // a system path is trusted implicitly: keep it out of the persistent state,
    // so it never appears in Settings | Trusted Locations and cannot be revoked there
    if (isSystemTrusted(locatedProject)) {
      return
    }
    val trustedPaths = TrustedPaths.getInstance()
    val oldState = trustedPaths.getProjectTrustedState(locatedProject)
    trustedPaths.setProjectTrustedState(locatedProject, isTrusted)
    val newState = trustedPaths.getProjectTrustedState(locatedProject)
    if (oldState != newState) {
      val syncPublisher = application.messageBus.syncPublisher(TrustedProjectsListener.TOPIC)
      when (isTrusted) {
        true -> syncPublisher.onProjectTrusted(locatedProject)
        else -> syncPublisher.onProjectUntrusted(locatedProject)
      }
    }
  }

  /**
   * Whether the trusted project dialog may offer to trust every project in [projectPath]'s parent directory.
   *
   * A project stored inside the IDE's own configuration directory shares that parent with every other such project -
   * this is what a frontend does with the projects it mirrors, see `ThinClientProjectUtil.createProjectDir`. Trusting
   * the location would silently trust all of them, present and future, and would put an IDE-internal path into the
   * user's trusted locations.
   */
  @ApiStatus.Internal
  fun isProjectLocationOfferedForTrust(projectPath: Path): Boolean {
    val parent = projectPath.parent ?: return false
    return !parent.startsWith(PathManager.getOriginalConfigDir())
  }

  /**
   * Whether [path] is a system path: a path the IDE owns, currently the welcome-screen ("Home") project directory.
   *
   * A system path is trusted unconditionally, like the custom VM options file. Its trust state is never
   * persisted, and the path never appears in Settings | Trusted Locations (IJPL-254558). The check depends
   * only on the registered [WelcomeScreenProjectProvider], not on the non-modal welcome-screen toggle,
   * so a stale recorded answer is healed after the toggle changes.
   */
  @ApiStatus.Internal
  fun isSystemTrustedPath(path: Path): Boolean {
    val welcomeScreenProjectPath = WelcomeScreenProjectProvider.getWelcomeScreenProjectPath() ?: return false
    return path.startsWith(welcomeScreenProjectPath)
  }

  /** Whether every root of [locatedProject] is a [system path][isSystemTrustedPath]. */
  private fun isSystemTrusted(locatedProject: LocatedProject): Boolean {
    val welcomeScreenProjectPath = WelcomeScreenProjectProvider.getWelcomeScreenProjectPath() ?: return false
    val roots = locatedProject.projectRoots
    return roots.isNotEmpty() && roots.all { it.startsWith(welcomeScreenProjectPath) }
  }

  /**
   * Checks that IDEA is loaded with a safe environment.
   * Therefore, the trusted check isn't needed in this mode.
   * I.e., all projects are automatically trusted in this mode.
   */
  @ApiStatus.Internal
  fun isTrustedCheckDisabled(): Boolean {
    if (System.getProperty("idea.trust.all.projects").toBoolean()) {
      return true
    }
    val isHeadlessMode = application.isUnitTestMode || application.isHeadlessEnvironment
    return isHeadlessMode && System.getProperty(TRUST_HEADLESS_DISABLED_PROPERTY, "true").toBoolean()
  }

  private fun isTrustedCheckDisabledForProduct(): Boolean = System.getProperty("idea.trust.disabled").toBoolean() || isTrustedCheckDisabled()
}
