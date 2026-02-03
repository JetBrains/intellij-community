package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.ui.icon.IconKey

/**
 * UI-part of `WelcomeScreenFeatureProvider`. Adds a button to the non-modal
 * Welcome Screen for each feature.
 *
 * Only one implementation of this interface should be registered per `featureKey`,
 * while multiple backends may be registered for the same feature.
 */
@ApiStatus.Internal
abstract class WelcomeScreenFeatureUI {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeScreenFeatureUI> =
      ExtensionPointName.create("com.intellij.platform.ide.welcomeScreenFeatureUi")

    fun getForFeatureKey(featureKey: String): WelcomeScreenFeatureUI? {
      return EP_NAME.lazySequence().firstOrNull { it.featureKey == featureKey }
    }
  }

  abstract val featureKey: String

  abstract val icon: IconKey
}
