// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import androidx.compose.runtime.Composable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.newEditor.ExternalUpdateRequest
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

/**
 * A [SearchableConfigurable] whose UI is rendered with the Swing-Compose runtime.
 *
 * Subclasses implement [ComposeContent] and provide [getId]/[getDisplayName]; the composition is
 * hosted and disposed by [composeSwingPanel].
 *
 * The page stands where a page written with `panel { }` stands.
 *
 * Persisted settings are wired with [bind]: it returns a [SettingState] seeded from the
 * setting (so the state is the single source of UI truth), and the base derives
 * [isModified]/[apply]/[reset] from every binding. A page with extra behavior may still override
 * those and call `super`.
 *
 * @see com.intellij.platform.compose.ComposeSearchableConfigurable
 * @see com.intellij.openapi.options.BoundSearchableConfigurable
 */
@ApiStatus.Experimental
public abstract class ComposeSwingSearchableConfigurable : SearchableConfigurable, Configurable.NoMargin {
  private val bindings = mutableListOf<SettingState<*>>()
  private var uiDisposable: Disposable? = null
  private var page: JComponent? = null

  /** Content of this configurable. */
  @Composable
  public open fun ComposeContent() {
  }

  /**
   * Binds a persisted [property] to a [SettingState] (a Compose state seeded from its current value).
   * The returned state is the single source of UI truth; [isModified]/[apply]/[reset] are handled for
   * it automatically. Use `by` to read/write it directly: `var enabled by bind(settings::enabled)`.
   */
  protected fun <T> bind(property: KMutableProperty0<T>): SettingState<T> =
    bind(get = { property.get() }, set = { property.set(it) })

  /**
   * Binds a computed setting (read with [get], written with [set]) — for settings without a backing
   * property. Prefer [bind] with a property reference when one exists.
   */
  protected fun <T> bind(get: () -> T, set: (T) -> Unit): SettingState<T> =
    SettingState(get, set).also { bindings += it }

  final override fun createComponent(): JComponent {
    val uiDisposable = Disposer.newDisposable(javaClass.name)
    this.uiDisposable = uiDisposable
    return composeSwingPanel(uiDisposable) { ComposeContent() }
      // The Settings dialog margins a page by the runtime type and the layout of the component it is handed,
      // which a panel hosting a composition cannot be, so the page states its own margins and opts out of the
      // dialog's with NoMargin.
      .apply { addContainerListener(PageMargins) }
      .also { page = it }
  }

  /**
   * Disposes the composition [createComponent] mounted. The Settings dialog builds a page again after
   * disposing it, so this leaves the page ready for another [createComponent]. An override must call
   * `super`.
   */
  override fun disposeUIResources() {
    uiDisposable?.let(Disposer::dispose)
    uiDisposable = null
    page = null
  }

  /**
   * The first component on the page that can take focus, which is what the dialog focuses when the page is
   * opened. A page whose first field is not the one to start in overrides this.
   *
   * The page composes when it reaches a window, so this returns `null` for a page that has not been shown.
   */
  override fun getPreferredFocusedComponent(): JComponent? =
    page?.let { IdeFocusTraversalPolicy.getPreferredFocusedComponent(it) }

  /**
   * Asks the Settings dialog to re-read [isModified].
   *
   * The dialog polls it off input gestures, so a page whose state changed on its own - a background
   * detection completing, a service pushing an update - has to say so for Apply and Reset to enable.
   */
  protected fun requestModifiedCheck() {
    ApplicationManager.getApplication().messageBus
      .syncPublisher(ExternalUpdateRequest.TOPIC)
      .requestUpdate(this)
  }

  override fun isModified(): Boolean = bindings.any { it.isModified }

  override fun apply() {
    bindings.forEach { it.apply() }
  }

  override fun reset() {
    bindings.forEach { it.reset() }
  }
}

/**
 * Gives a page its margins, on the component the composition mounts rather than on the host around it.
 *
 * A form laid out by the platform grid puts a row that starts with a check box a few pixels left of the
 * content the rest of the rows start at, so the box's painted edge stands where their text does, and the grid
 * can only step out that far inside the insets of the component it lays out. Margins on the host would leave
 * it nowhere to step, and every row that does not start with a check box would stand that much too far right.
 *
 * The measurements are the ones [com.intellij.openapi.options.ex.ConfigurableCardPanel] gives a page it
 * margins itself, chosen the same way: a grid counts the space above its first row and below its last as part
 * of itself, and anything else does not.
 */
private object PageMargins : ContainerAdapter() {
  override fun componentAdded(e: ContainerEvent) {
    val content = e.child as? JComponent ?: return
    content.border =
      if (content.layout is GridLayout) JBUI.Borders.empty(5, 16, 10, 16)
      else JBUI.Borders.empty(11, 16, 16, 16)
  }
}
