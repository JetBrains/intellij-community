package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import javax.swing.Icon

/**
 * Extension point for providing welcome screen icons from customization plugins.
 * This allows plugins to provide their own icons for the welcome screen.
 */
abstract class WelcomeScreenFileIconProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeScreenFileIconProvider> =
      ExtensionPointName.create("com.intellij.platform.ide.nonModalWelcomeScreen.welcomeScreenFileIconProvider")

    fun getForPluginId(pluginId: PluginId): WelcomeScreenFileIconProvider? {
      return EP_NAME.lazySequence().firstOrNull { it.pluginId == pluginId }
    }
  }

  protected abstract val pluginId: PluginId

  abstract val fileIcon: Icon

}
