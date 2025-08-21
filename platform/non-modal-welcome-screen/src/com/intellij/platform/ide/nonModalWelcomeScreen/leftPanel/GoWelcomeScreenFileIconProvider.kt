package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import javax.swing.Icon

/**
 * Extension point for providing welcome screen icons from customization plugins.
 * This allows plugins to provide their own icons for the welcome screen.
 */
abstract class GoWelcomeScreenFileIconProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<GoWelcomeScreenFileIconProvider> =
      ExtensionPointName.create("com.goide.welcomeScreenFileIconProvider")

    fun getForPluginId(pluginId: PluginId): GoWelcomeScreenFileIconProvider? {
      return EP_NAME.lazySequence().firstOrNull { it.pluginId == pluginId }
    }
  }

  protected abstract val pluginId: PluginId

  abstract val fileIcon: Icon

}
