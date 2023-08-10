// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.application.subscribe
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.JComponentOverlay
import com.intellij.collaboration.ui.util.bindProgressIn
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.content.Content
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.vcs.log.ui.frame.ProgressStripe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.properties.Delegates

object CollaborationToolsUIUtil {
  val COMPONENT_SCOPE_KEY = Key.create<CoroutineScope>("Collaboration.Component.Coroutine.Scope")

  val animatedLoadingIcon = AnimatedIcon.Default.INSTANCE

  /**
   * Connects [searchTextField] to a [list] to be used as a filter
   */
  fun <T> attachSearch(list: JList<T>, searchTextField: SearchTextField, searchBy: (T) -> String) {
    val speedSearch = SpeedSearch(false)
    val filteringListModel = NameFilteringListModel<T>(list.model, searchBy, speedSearch::shouldBeShowing, speedSearch.filter::orEmpty)
    list.model = filteringListModel

    searchTextField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = speedSearch.updatePattern(searchTextField.text)
    })

    speedSearch.addChangeListener {
      val prevSelection = list.selectedValue // save to restore the selection on filter drop
      filteringListModel.refilter()
      if (filteringListModel.size > 0) {
        val fullMatchIndex = if (speedSearch.isHoldingFilter) filteringListModel.closestMatchIndex
        else filteringListModel.getElementIndex(prevSelection)
        if (fullMatchIndex != -1) {
          list.selectedIndex = fullMatchIndex
        }

        if (filteringListModel.size <= list.selectedIndex || !filteringListModel.contains(list.selectedValue)) {
          list.selectedIndex = 0
        }
      }
    }

    ScrollingUtil.installActions(list)
    ScrollingUtil.installActions(list, searchTextField.textEditor)
  }

  /**
   * Show an error on [component] if there's one in [errorValue]
   */
  @Internal
  fun installValidator(component: JComponent, errorValue: SingleValueModel<@Nls String?>) {
    UiNotifyConnector.installOn(component, ValidatorActivatable(errorValue, component), false)
  }

  private class ValidatorActivatable(
    private val errorValue: SingleValueModel<@Nls String?>,
    private val component: JComponent
  ) : Activatable {
    private var validatorDisposable: Disposable? = null
    private var validator: ComponentValidator? = null

    init {
      errorValue.addListener {
        validator?.revalidate()
      }
    }

    override fun showNotify() {
      validatorDisposable = Disposer.newDisposable("Component validator")
      validator = ComponentValidator(validatorDisposable!!).withValidator(Supplier {
        errorValue.value?.let { ValidationInfo(it, component) }
      }).installOn(component)
    }

    override fun hideNotify() {
      validatorDisposable?.let { Disposer.dispose(it) }
      validatorDisposable = null
      validator = null
    }
  }

  /**
   * Show progress label over [component]
   */
  @Internal
  fun wrapWithProgressOverlay(component: JComponent, inProgressValue: SingleValueModel<Boolean>): JComponent {
    val busyLabel = JLabel(AnimatedIcon.Default())
    inProgressValue.addAndInvokeListener {
      busyLabel.isVisible = it
      component.isEnabled = !it
    }
    return JComponentOverlay.createCentered(component, busyLabel)
  }

  /**
   * Show progress stripe above [component]
   */
  @Internal
  fun wrapWithProgressStripe(scope: CoroutineScope, loadingFlow: Flow<Boolean>, component: JComponent): JComponent {
    return ProgressStripe(component, scope.nestedDisposable(), ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
      bindProgressIn(scope, loadingFlow)
    }
  }

  /**
   * Wrap component with [SingleComponentCenteringLayout] to show component in a center
   */
  @Internal
  fun moveToCenter(component: JComponent): JComponent {
    return JPanel(SingleComponentCenteringLayout()).apply {
      isOpaque = false
      add(component)
    }
  }

  /**
   * Adds actions to transfer focus by tab/shift-tab key for given [component].
   *
   * May be helpful for overwriting tab symbol input for text fields
   */
  fun registerFocusActions(component: JComponent) {
    component.registerKeyboardAction({ component.transferFocus() },
                                     KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
                                     JComponent.WHEN_FOCUSED)
    component.registerKeyboardAction({ component.transferFocusBackward() },
                                     KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                                     JComponent.WHEN_FOCUSED)
  }

  /**
   * Add [listener] that will be invoked on each UI update
   */
  @Deprecated("Not needed when using proper color and fonts. For complicated colors see JBColor.lazy")
  fun <T : JComponent> overrideUIDependentProperty(component: T, listener: T.() -> Unit) {
    UiNotifyConnector.installOn(component, object : Activatable {
      private var listenerDisposable: Disposable? by Delegates.observable(null) { _, oldValue, _ ->
        oldValue?.also { Disposer.dispose(it) }
      }

      override fun showNotify() {
        val disposable = Disposer.newDisposable("LAF listener disposable for $component")
        LafManagerListener.TOPIC.subscribe(disposable, LafManagerListener { listener(component) })
        listenerDisposable = disposable
      }

      override fun hideNotify() {
        listenerDisposable = null
      }
    })
    listener(component)
  }

  /**
   * Makes the button blue like a default button in dialogs
   */
  fun JButton.defaultButton(): JButton = apply {
    isDefault = true
  }

  /**
   * Makes the button blue like a default button in dialogs
   */
  var JButton.isDefault: Boolean
    get() = ClientProperty.isTrue(this, DarculaButtonUI.DEFAULT_STYLE_KEY)
    set(value) {
      if (value) {
        ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
      }
      else {
        ClientProperty.remove(this, DarculaButtonUI.DEFAULT_STYLE_KEY)
      }
    }

  /**
   * Removes http(s) protocol and trailing slash from given [url]
   */
  @Suppress("HttpUrlsUsage")
  @NlsSafe
  fun cleanupUrl(@NlsSafe url: String): String = url
    .removePrefix("https://")
    .removePrefix("http://")
    .removeSuffix("/")

  /**
   * Checks if focus is somewhere down the hierarchy from [component]
   */
  fun isFocusParent(component: JComponent): Boolean {
    val focusOwner = IdeFocusManager.findInstanceByComponent(component).focusOwner ?: return false
    return SwingUtilities.isDescendingFrom(focusOwner, component)
  }

  /**
   * Finds the proper focus target for [panel] and set focus to it
   */
  fun focusPanel(panel: JComponent) {
    val focusManager = IdeFocusManager.findInstanceByComponent(panel)
    val toFocus = focusManager.getFocusTargetFor(panel) ?: return
    focusManager.doWhenFocusSettlesDown { focusManager.requestFocus(toFocus, true) }
  }

  fun setComponentPreservingFocus(content: Content, component: JComponent) {
    val focused = isFocusParent(content.component)
    content.component = component
    if (focused) {
      focusPanel(content.component)
    }
  }

  fun getFocusBorderInset(): Int {
    val bw: Int = if (UIUtil.isUnderDefaultMacTheme()) 3 else DarculaUIUtil.BW.unscaled.toInt()
    val lw: Int = if (UIUtil.isUnderDefaultMacTheme()) 0 else DarculaUIUtil.LW.unscaled.toInt()
    return bw + lw
  }

  fun wrapWithLimitedSize(component: JComponent, maxWidth: Int? = null, maxHeight: Int? = null): JComponent {
    val layout = SizeRestrictedSingleComponentLayout.constant(maxWidth, maxHeight)
    return JPanel(layout).apply {
      name = "Size limit wrapper"
      isOpaque = false
      add(component)
    }
  }

  fun wrapWithLimitedSize(component: JComponent, maxSize: DimensionRestrictions): JComponent {
    val layout = SizeRestrictedSingleComponentLayout().apply {
      this.maxSize = maxSize
    }
    return JPanel(layout).apply {
      name = "Size limit wrapper"
      isOpaque = false
      add(component)
    }
  }

  fun getLabelBackground(hexColor: String): JBColor {
    val color = ColorUtil.fromHex(hexColor)
    return JBColor(color, ColorUtil.darker(color, 3))
  }

  fun getLabelForeground(bg: Color): Color = if (ColorUtil.isDark(bg)) JBColor.WHITE else JBColor.BLACK

  /**
   * Use method for different sizes depending on the type of UI (old/new).
   *
   * Must be used only as a property: `get()`
   */
  fun getSize(oldUI: Int, newUI: Int): Int = if (ExperimentalUI.isNewUI()) newUI else oldUI

  /**
   * Use method for different sizes depending on the type of UI (old/new).
   *
   * Must be used only as a property: `get()`
   */
  fun getInsets(oldUI: Insets, newUI: Insets): Insets = if (ExperimentalUI.isNewUI()) newUI else oldUI

  fun createTagLabel(text: @Nls String): JComponent =
    JLabel(text).apply {
      font = JBFont.small()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(0, 4)
    }.let {
      RoundedPanel(SingleComponentCenteringLayout(), 4).apply {
        border = JBUI.Borders.empty()
        background = UIUtil.getPanelBackground()
        add(it)
      }
    }
}

@Suppress("FunctionName")
fun VerticalListPanel(gap: Int = 0): JPanel =
  ScrollablePanel(ListLayout.vertical(gap), SwingConstants.VERTICAL).apply {
    isOpaque = false
  }

@Suppress("FunctionName")
fun HorizontalListPanel(gap: Int = 0): JPanel =
  ScrollablePanel(ListLayout.horizontal(gap), SwingConstants.HORIZONTAL).apply {
    isOpaque = false
  }

private class ScrollablePanel(layout: LayoutManager?, private val orientation: Int)
  : JPanel(layout), Scrollable {

  private var verticalUnit = 1
  private var horizontalUnit = 1

  override fun addNotify() {
    super.addNotify()
    val fontMetrics = getFontMetrics(font)
    verticalUnit = fontMetrics.maxAscent + fontMetrics.maxDescent
    horizontalUnit = fontMetrics.charWidth('W')
  }

  override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
    if (orientation == SwingConstants.HORIZONTAL) horizontalUnit else verticalUnit

  override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
    if (orientation == SwingConstants.HORIZONTAL) visibleRect.width else visibleRect.height

  override fun getPreferredScrollableViewportSize(): Dimension? = preferredSize

  override fun getScrollableTracksViewportWidth(): Boolean = orientation == SwingConstants.VERTICAL

  override fun getScrollableTracksViewportHeight(): Boolean = orientation == SwingConstants.HORIZONTAL
}

/**
 * Loading label with animated icon
 */
@Suppress("FunctionName")
fun LoadingLabel(): JLabel = JLabel(CollaborationToolsUIUtil.animatedLoadingIcon).apply {
  name = "Animated loading label"
}

/**
 * Scrollpane without background and borders
 */
@Suppress("FunctionName")
fun TransparentScrollPane(content: JComponent): JScrollPane =
  ScrollPaneFactory.createScrollPane(content, true).apply {
    isOpaque = false
    viewport.isOpaque = false
  }

internal fun <E> ListModel<E>.findIndex(item: E): Int {
  for (i in 0 until size) {
    if (getElementAt(i) == item) return i
  }
  return -1
}

internal val <E> ListModel<E>.items
  get() = Iterable {
    object : Iterator<E> {
      private var idx = -1

      override fun hasNext(): Boolean = idx < size - 1

      override fun next(): E {
        idx++
        return getElementAt(idx)
      }
    }
  }

fun ComboBoxModel<*>.selectFirst() {
  val size = size
  if (size == 0) {
    return
  }
  val first = getElementAt(0)
  selectedItem = first
}
