// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

/**
 * Holds the explicit trust state of projects.
 * [TrustedProjects] uses the first provider that is applicable to a project.
 * The default provider stores every project in [TrustedPaths] and is registered with `order="last"`.
 */
@ApiStatus.Internal
interface TrustedProjectsStateProvider {

  /**
   * Checks if this provider holds the trust state of [locatedProject].
   *
   * @param locatedProject the project roots to check.
   * @return `true` when this provider holds the trust state of [locatedProject].
   */
  fun isApplicable(locatedProject: LocatedProject): Boolean

  /**
   * Gets the explicit trust state of an applicable [locatedProject].
   *
   * @param locatedProject the project roots to check.
   * @return the trust state, or [ThreeState.UNSURE] when no answer is known.
   */
  fun getProjectTrustedState(locatedProject: LocatedProject): ThreeState

  /**
   * Records the trust answer for an applicable [locatedProject].
   *
   * @param locatedProject the project roots to record.
   * @param isTrusted the trust answer.
   */
  fun setProjectTrusted(locatedProject: LocatedProject, isTrusted: Boolean)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<TrustedProjectsStateProvider> = ExtensionPointName("com.intellij.trustedProjectsStateProvider")
  }
}
