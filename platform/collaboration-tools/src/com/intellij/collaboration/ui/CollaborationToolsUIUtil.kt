// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.application.subscribe
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.JComponentOverlay
import com.intellij.collaboration.ui.util.bindProgressIn
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.observable.properties.AbstractObservableProperty
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.components.panels.BackgroundRoundedPanel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.vcs.ui.ProgressStripe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.properties.Delegates

object CollaborationToolsUIUtil {
  internal val COMPONENT_SCOPE_KEY: Key<CoroutineScope> = Key.create("Collaboration.Component.Coroutine.Scope")

  val animatedLoadingIcon: Icon = AnimatedIcon.Default.INSTANCE

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
      }).installOn(component).also {
        it.revalidate()
      }
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
  fun wrapWithProgressStripe(scope: CoroutineScope, loadingFlow: Flow<Boolean>, component: JComponent): JComponent {
    return ProgressStripe(component, scope.nestedDisposable()).apply {
      bindProgressIn(scope, loadingFlow)
    }
  }

  /**
   * Wrap component with [SingleComponentCenteringLayout] to show component in a center
   */
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
   * Finds the proper focus target for [panel] and set focus to it
   */
  fun focusPanel(panel: JComponent) {
    val toFocus = IdeFocusManager.findInstanceByComponent(panel).getFocusTargetFor(panel)
    toFocus?.requestFocusInWindow()
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

  /**
   * A text label with a rounded rectangle as a background
   * To be used for various tags and badges
   */
  fun createTagLabel(text: @Nls String): JComponent = createTagLabel(SingleValueModel(text))

  fun createTagLabel(model: SingleValueModel<@Nls String?>): JComponent =
    JLabel(model.value).apply {
      font = JBFont.small()
      foreground = CodeReviewColorUtil.Review.stateForeground
      border = JBUI.Borders.empty(0, 4)
      model.addListener {
        text = it
      }
    }.let {
      BackgroundRoundedPanel(4, SingleComponentCenteringLayout()).apply {
        border = JBUI.Borders.empty()
        background = CodeReviewColorUtil.Review.stateBackground
        add(it)
      }
    }

  /**
   * Turns the flow into an observable property collected under the given scope.
   *
   * Note: this collects the state flow which will never complete. The passed scope
   * thus needs to be cancelled manually or through a disposing scope for example
   * for collecting to stop.
   */
  fun <T> StateFlow<T>.asObservableIn(scope: CoroutineScope): AbstractObservableProperty<T> =
    object : AbstractObservableProperty<T>() {
      override fun get() = value

      init {
        scope.launch {
          collect { state ->
            fireChangeEvent(state)
          }
        }
      }
    }

  /**
   * Hides the component if none of the children are visible
   * TODO: handle children list mutability
   */
  fun hideWhenNoVisibleChildren(component: JComponent) {
    val children = component.components
    component.isVisible = children.any { it.isVisible }
    for (child in children) {
      UIUtil.runWhenVisibilityChanged(child) { component.isVisible = children.any { it.isVisible } }
    }
  }

  @ApiStatus.Internal
  inline fun validateAndApplyAction(panel: DialogPanel, action: () -> Unit) {
    panel.apply()
    val errors = panel.validateAll()
    if (errors.isEmpty()) {
      action()
      panel.reset()
    }
    else {
      val componentWithError = errors.first().component ?: return
      focusPanel(componentWithError)
    }
  }
}

@Suppress("FunctionName")
fun VerticalListPanel(gap: Int = 0): JPanel =
  ScrollablePanel(SwingConstants.VERTICAL, ListLayout.vertical(gap)).apply {
    isOpaque = false
  }

@Suppress("FunctionName")
fun HorizontalListPanel(gap: Int = 0): JPanel =
  ScrollablePanel(SwingConstants.HORIZONTAL, ListLayout.horizontal(gap)).apply {
    isOpaque = false
  }

@Suppress("FunctionName")
fun ScrollablePanel(@MagicConstant(intValues = [SwingConstants.HORIZONTAL.toLong(), SwingConstants.VERTICAL.toLong()]) orientation: Int,
                    layout: LayoutManager? = null): JPanel =
  OrientableScrollablePanel(orientation, layout)

private class OrientableScrollablePanel(private val orientation: Int, layout: LayoutManager?) : JPanel(layout), Scrollable {

  private var verticalUnit = 1
  private var horizontalUnit = 1

  init {
    check(orientation == SwingConstants.VERTICAL || orientation == SwingConstants.HORIZONTAL) {
      "SwingConstants.VERTICAL or SwingConstants.HORIZONTAL is expected for orientation, got $orientation"
    }
  }

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

@Suppress("FunctionName")
fun ClippingRoundedPanel(arcRadius: Int = 8, borderColor: Color = JBColor.border(), layoutManager: LayoutManager? = null): JPanel =
  ClippingRoundedPanel(arcRadius, layoutManager).apply {
    border = RoundedLineBorder(borderColor, (arcRadius + 1) * 2)
  }

/**
 * A panel with rounded corners which rounds the corners of both its background and its children
 * Supposed to be used ONLY when there is not enough space between the children and panel edges, AND background color is dynamic
 *
 * For simpler cases where only the background should be rounded one should use [com.intellij.ui.components.panels.BackgroundRoundedPanel]
 */
@Suppress("FunctionName")
fun ClippingRoundedPanel(arcRadius: Int = 8, layoutManager: LayoutManager? = null): JPanel =
  RoundedPanel(layoutManager, arcRadius)

fun jbColorFromHex(name: @NonNls String, light: @NonNls String, dark: @NonNls String): JBColor =
  JBColor.namedColor(name, jbColorFromHex(light, dark))

fun jbColorFromHex(light: @NonNls String, dark: @NonNls String): JBColor =
  JBColor(ColorUtil.fromHex(light), ColorUtil.fromHex(dark))


/**
 * Loading label with animated icon
 */
@Suppress("FunctionName")
@JvmOverloads
fun LoadingLabel(labelText: @NlsContexts.Label String? = null): JLabel = JLabel(CollaborationToolsUIUtil.animatedLoadingIcon).apply {
  name = "Animated loading label"
  text = labelText
}

/**
 * Loading label with a text
 */
@Suppress("FunctionName")
fun LoadingTextLabel(): JLabel = JLabel(ApplicationBundle.message("label.loading.page.please.wait")).apply {
  foreground = UIUtil.getContextHelpForeground()
  name = "Textual loading label"
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

/**
 * A simple stub that can be used to transfer focus to an empty panel
 */
@ApiStatus.Internal
@Suppress("FunctionName")
fun FocusableStub(): JComponent =
  JLabel().apply {
    isFocusable = true
  }

/**
 * Update the content and re-request focus if the content had it previously
 */
fun Wrapper.setContentPreservingFocus(content: JComponent?) {
  if (content == null) {
    setContent(null)
  }
  else {
    runPreservingFocus {
      setContent(content)
    }
  }
}

private fun JPanel.runPreservingFocus(runnable: () -> Unit) {
  val focused = isFocusAncestor()
  runnable()
  if (focused) {
    requestFocusPreferred()
  }
}

/**
 * Request focus on the component or a child determined by a focus policy
 */
fun JComponent.requestFocusPreferred() {
  CollaborationToolsUIUtil.focusPanel(this)
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
