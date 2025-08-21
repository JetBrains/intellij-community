package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.jewel.ui.icon.IconKey

/**
 * Allows invoking a feature provided by a plugin on the non-modal Welcome Screen.
 *
 * This will allow dynamically enabling/disabling the feature based on the plugin's state
 * without breaking IDE customization plugin dependencies.
 *
 * Should either not depend on any other plugin
 * or be registered in a corresponding customization plugin which is not required for the main plugin.
 */
abstract class GoWelcomeScreenFeatureProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<GoWelcomeScreenFeatureProvider> =
      ExtensionPointName.create("com.goide.welcomeScreenFeatureProvider")

    fun getForFeatureKey(featureKey: String): GoWelcomeScreenFeatureProvider? {
      return EP_NAME.lazySequence().firstOrNull { it.featureKey == featureKey }
    }
  }

  protected abstract val featureKey: String

  abstract val icon: IconKey

  abstract fun invoke(project: Project)
}

abstract class GoWelcomeScreenToolwindowFeatureProvider : GoWelcomeScreenFeatureProvider() {
  protected abstract val toolWindowId: String

  final override fun invoke(project: Project) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
    toolWindow?.activate(null, true)
  }
}
