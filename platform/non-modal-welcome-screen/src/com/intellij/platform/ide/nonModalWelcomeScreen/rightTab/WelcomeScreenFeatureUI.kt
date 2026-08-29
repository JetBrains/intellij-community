package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent

/**
 * UI-part of `WelcomeScreenFeatureProvider`. Adds a button to the non-modal
 * Welcome Screen for each feature.
 *
 * Only one implementation of this interface should be registered per `featureKey`,
 * while multiple backends may be registered for the same feature.
 *
 * A feature states two presentations of itself. The button goes into the feature grid. The section goes under that
 * grid. Both answer to one [featureKey].
 */
@ApiStatus.Internal
abstract class WelcomeScreenFeatureUI {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeScreenFeatureUI> =
      ExtensionPointName.create("com.intellij.platform.ide.welcomeScreenFeatureUi")

    fun getForFeatureKey(featureKey: String): WelcomeScreenFeatureUI? {
      return EP_NAME.lazySequence().firstOrNull { it.featureKey == featureKey }
    }

    internal fun features(): List<WelcomeScreenFeatureUI> = EP_NAME.extensionList
  }

  abstract val featureKey: String

  abstract val icon: Icon

  /**
   * The button label.
   *
   * A plugin overrides this to keep the label in its own message bundle. It stays `null` when the product's
   * [WelcomeRightTabContentProvider] supplies the label.
   */
  open val text: @Nls String? get() = null

  /** Sections read in this order under the feature grid. */
  open val contentOrder: Int get() = 0

  /**
   * The section this feature contributes under the feature grid, or `null` for a feature that states a button only.
   *
   * Called off the EDT, so an implementation owns the hop to the EDT that its own Swing construction needs. The tab
   * waits for this call before it fills its body. So an implementation returns a component that already states its
   * size, and fills that component from a scope of its own.
   */
  open suspend fun createContent(project: Project): Content? = null

  /**
   * One section of the welcome right tab, and what the tab needs to place, focus and release it.
   *
   * @param preferredFocusedComponent the supplier of the focus target. The tab asks it on the EDT each time it needs
   * the target, because a section can fill itself after the tab places it. So the supplier returns `null` while the
   * section has no target yet, and the live target after that. Stays `null` for a section that takes no focus.
   */
  class Content(
    val component: JComponent,
    val preferredFocusedComponent: (() -> JComponent?)? = null,
    val disposable: Disposable? = null,
  )
}
