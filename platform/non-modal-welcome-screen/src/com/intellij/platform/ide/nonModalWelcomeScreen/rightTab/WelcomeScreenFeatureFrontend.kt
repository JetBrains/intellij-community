// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.ApiStatus

/**
 * Handles a click on a feature of the non-modal Welcome Screen in the frontend.
 *
 * A feature that needs no backend registers this class instead of `WelcomeScreenFeatureBackend`. So the feature stays
 * available in a session without a backend, such as the light product mode. [WelcomeScreenFeatureApi.getInstance]
 * merges the frontend features into the backend features by [featureKey]. A frontend feature takes the click when
 * both sides register one key.
 *
 * For the UI of the feature see [WelcomeScreenFeatureUI].
 */
@ApiStatus.Internal
abstract class WelcomeScreenFeatureFrontend {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeScreenFeatureFrontend> =
      ExtensionPointName.create("com.intellij.platform.ide.welcomeScreenFeatureFrontend")

    internal fun getFeatureIds(): List<String> {
      return EP_NAME.extensionList.map { it.featureKey }
    }

    internal fun getForFeatureKey(featureKey: String): WelcomeScreenFeatureFrontend? {
      return EP_NAME.lazySequence().firstOrNull { it.featureKey == featureKey }
    }
  }

  protected abstract val featureKey: String

  /** Called on the EDT. */
  abstract fun onClick(project: Project)
}

@ApiStatus.Internal
abstract class WelcomeScreenToolwindowFeatureFrontend : WelcomeScreenFeatureFrontend() {
  protected abstract val toolWindowId: String

  final override fun onClick(project: Project) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
    toolWindow?.activate(null, true)
  }
}
