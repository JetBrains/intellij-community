// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.ide.trustedProjects.TrustedProjectsStateProvider
import com.intellij.util.ThreeState

/** Stores the trust state of every project in [TrustedPaths]. It is registered with `order="last"`. */
internal class TrustedPathsStateProvider : TrustedProjectsStateProvider {

  override fun isApplicable(locatedProject: LocatedProject): Boolean = true

  override fun getProjectTrustedState(locatedProject: LocatedProject): ThreeState {
    return TrustedPaths.getInstance().getProjectTrustedState(locatedProject)
  }

  override fun setProjectTrusted(locatedProject: LocatedProject, isTrusted: Boolean) {
    TrustedPaths.getInstance().setProjectTrustedState(locatedProject, isTrusted)
  }
}
