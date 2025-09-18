// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.backend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.ApiStatus

/**
 * Allows invoking a feature provided by a plugin on the non-modal Welcome Screen.
 *
 * This will allow dynamically enabling/disabling the feature based on the plugin's state
 * without breaking IDE customization plugin dependencies.
 *
 * Should either not depend on any other plugin
 * or be registered in a corresponding customization plugin which is not required for the main plugin.
 *
 * This class is a backend part, for UI see `WelcomeScreenFeatureUI`.
 */
@ApiStatus.Internal
abstract class WelcomeScreenFeatureBackend {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeScreenFeatureBackend> =
      ExtensionPointName.create("com.intellij.platform.ide.welcomeScreenFeatureBackend")

    fun getFeatureIds(): List<String> {
      return EP_NAME.extensionList.map { it.featureKey }
    }

    fun getForFeatureKey(featureKey: String): WelcomeScreenFeatureBackend? {
      return EP_NAME.lazySequence().firstOrNull { it.featureKey == featureKey }
    }
  }

  protected abstract val featureKey: String

  abstract fun onClick(project: Project)
}

@ApiStatus.Internal
abstract class WelcomeScreenToolwindowFeatureBackend : WelcomeScreenFeatureBackend() {
  protected abstract val toolWindowId: String

  final override fun onClick(project: Project) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
    toolWindow?.activate(null, true)
  }
}
