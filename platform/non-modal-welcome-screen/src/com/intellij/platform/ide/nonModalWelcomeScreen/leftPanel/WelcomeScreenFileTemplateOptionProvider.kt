package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsContexts
import javax.swing.Icon

data class TemplateOption(
  val templateName: String,
  @NlsContexts.Label val displayName: String,
  val icon: Icon? = null,
)

/**
 * Allows plugins to contribute template options to the Welcome Screen.
 *
 * This extension point enables dynamic contribution of template options
 * without hardcoding them in the Welcome Screen handler.
 */
abstract class WelcomeScreenFileTemplateOptionProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeScreenFileTemplateOptionProvider> =
      ExtensionPointName.create("com.intellij.platform.ide.nonModalWelcomeScreen.templateOptionProvider")

    fun getForTemplateKey(templateKey: String): WelcomeScreenFileTemplateOptionProvider? {
      return EP_NAME.lazySequence().firstOrNull { it.templateKey == templateKey }
    }
  }

  protected abstract val templateKey: String

  /**
   * Returns the list of template options this provider contributes.
   */
  abstract fun getTemplateOptions(): List<TemplateOption>
}
