// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.*
import com.intellij.ui.*
import com.intellij.ui.AppUIUtil.targetToDevice
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBHtmlPaneConfiguration.Companion.builder
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.Alarm
import com.intellij.util.ui.*
import com.intellij.util.ui.StartupUiUtil.isDarkTheme
import com.intellij.util.ui.StartupUiUtil.isWaylandToolkit
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.MouseEvent
import javax.swing.*
import kotlin.concurrent.Volatile
import kotlin.math.max

// Android team doesn't want to use new mockito for now, so, class cannot be final
@Service
class IdeTooltipManager(coroutineScope: CoroutineScope) : Disposable {
  private var isEnabled = false

  private var helpTooltipManager: HelpTooltipManager? = null
  private var hideHelpTooltip = false

  @Volatile
  private var currentComponent: Component? = null

  @Volatile
  private var queuedComponent: Component? = null

  @Volatile
  private var processingComponent: Component? = null

  private var balloon: Balloon? = null

  private var currentEvent: MouseEvent? = null
  private var currentTipIsCentered = false

  private var lastDisposable: Disposable? = null

  private var hideRunnable: Runnable? = null

  private var showDelay = true

  private val alarm = Alarm(coroutineScope, Alarm.ThreadToUse.SWING_THREAD)

  private var x = 0
  private var y = 0

  private var currentTooltip: IdeTooltip? = null
  private var showRequest: Runnable? = null
  private var queuedTooltip: IdeTooltip? = null

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect(coroutineScope)
    connection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        hideCurrent(mouseEvent = null, action = action, event = event)
      }
    })
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        hideCurrentNow(animationEnabled = false)
      }
    })

    coroutineScope.useRegistryManagerWhenReadyJavaAdapter { registryManager ->
      Toolkit.getDefaultToolkit().addAWTEventListener({ this.eventDispatched(it) }, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
      isEnabled = registryManager.`is`(IDE_TOOLTIP_CALLOUT_KEY)
      processEnabled(registryManager.`is`(IDE_HELP_TOOLTIP_ENABLED_KEY))
    }
  }

  companion object {
    @JvmField
    internal val TOOLTIP_COLOR_KEY: ColorKey = ColorKey.createColorKey("TOOLTIP", null)

    private val CUSTOM_TOOLTIP = Key.create<IdeTooltip>("custom.tooltip")
    private val DUMMY_LISTENER = MouseEventAdapter<Void?>(null)

    @Suppress("UseJBColor")
    @JvmField
    @Internal
    val GRAPHITE_COLOR: Color = Color(100, 100, 100, 230)

    private const val IDE_TOOLTIP_CALLOUT_KEY = "ide.tooltip.callout"
    @Suppress("SpellCheckingInspection")
    private const val IDE_HELP_TOOLTIP_ENABLED_KEY = "ide.helptooltip.enabled"
    private val LOG = logger<IdeTooltipManager>()

    @JvmStatic
    fun getInstance(): IdeTooltipManager = service()

    @JvmStatic
    fun initPane(text: @NlsContexts.Tooltip String?, hintHint: HintHint, layeredPane: JLayeredPane?): JEditorPane {
      return initPane(html = Html(text), hintHint = hintHint, layeredPane = layeredPane, limitWidthToScreen = true)
    }

    @JvmStatic
    fun initPane(
      html: @NlsContexts.Tooltip Html,
      hintHint: HintHint,
      layeredPane: JLayeredPane?,
      limitWidthToScreen: Boolean,
    ): JEditorPane {
      val styleConfiguration = JBHtmlPaneStyleConfiguration()
      val paneConfiguration = builder()
        .customStyleSheet("pre {white-space: pre-wrap;} code, pre {overflow-wrap: anywhere;}")
        .build()

      val prefSizeWasComputed = Ref(false)
      val pane = if (!limitWidthToScreen) {
        JBHtmlPane(styleConfiguration, paneConfiguration)
      }
      else {
        LimitedWidthJBHtmlPane(styleConfiguration, paneConfiguration, prefSizeWasComputed, hintHint, layeredPane)
      }

      var text: @NonNls String = HintUtil.prepareHintText(html, hintHint)
      // Remove <style> rule for <code> added by prepareHintText() call
      text = text.replaceFirst("code \\{font-size:[0-9.]*pt;}".toRegex(), "")

      if (hintHint.isOwnBorderAllowed) {
        setBorder(pane)
        setColors(pane)
      }
      else {
        pane.border = null
      }

      if (!hintHint.isAwtTooltip) {
        prefSizeWasComputed.set(true)
      }

      pane.isOpaque = hintHint.isOpaqueAllowed
      pane.background = hintHint.textBackground

      pane.text = text

      if (!limitWidthToScreen) {
        targetToDevice(pane, layeredPane)
      }

      return pane
    }

    @JvmStatic
    fun setColors(pane: JComponent) {
      pane.foreground = JBColor.foreground()
      pane.background = HintUtil.getInformationColor()
      pane.isOpaque = true
    }

    fun setBorder(pane: JComponent) {
      @Suppress("UseJBColor")
      pane.border = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black), JBUI.Borders.empty(0, 5))
    }

    private fun isClickProcessor(balloon: Balloon?): Boolean = balloon is BalloonImpl && balloon.isClickProcessor

    private fun isAnimationEnabled(balloon: Balloon?): Boolean = balloon is BalloonImpl && balloon.isAnimationEnabled

    private fun isBlockClicks(balloon: Balloon): Boolean = balloon is BalloonImpl && balloon.isBlockClicks

    private fun isMovingForward(balloon: Balloon, target: RelativePoint): Boolean {
      return balloon is BalloonImpl && balloon.isMovingForward(target)
    }

    private fun isInside(balloon: Balloon, target: RelativePoint): Boolean = balloon is IdeTooltip.Ui && balloon.isInside(target)
  }

  internal class MyRegistryListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      val key = value.key
      if (key == IDE_TOOLTIP_CALLOUT_KEY) {
        val instance = getInstance()
        instance.isEnabled = value.asBoolean()
        instance.processEnabled(Registry.`is`(IDE_HELP_TOOLTIP_ENABLED_KEY))
      }
      if (key == IDE_HELP_TOOLTIP_ENABLED_KEY) {
        getInstance().processEnabled(value.asBoolean())
      }
    }
  }

  private fun eventDispatched(event: AWTEvent) {
    if (!isEnabled) {
      return
    }

    val me = event as MouseEvent
    processingComponent = me.component
    try {
      if (me.id == MouseEvent.MOUSE_ENTERED) {
        if (processingComponent != null && isWaylandToolkit()) {
          if (helpTooltipManager != null && helpTooltipManager!!.fromSameWindowAs(processingComponent!!)) {
            // Since we don't fully control popups positions in Wayland, they are
            // a lot more likely to appear right under the mouse pointer.
            // Don't cancel the popup just because the mouse has left its
            // parent component as it may have entered the popup itself.
            alarm.cancelAllRequests()
            return
          }
        }
        var canShow = true
        if (componentContextHasChanged(processingComponent)) {
          canShow = hideCurrent(me, null, null)
        }
        if (canShow) {
          maybeShowFor(processingComponent, me)
        }
      }
      else if (me.id == MouseEvent.MOUSE_EXITED) {
        // we hide tooltip (but not hint!) when it's shown over myComponent and mouse exits this component
        if (processingComponent === currentComponent && currentTooltip != null &&
            !currentTooltip!!.isHint && balloon != null
        ) {
          balloon!!.setAnimationEnabled(false)
          hideCurrent(null, null, null, null, false)
        }
        else if (processingComponent === currentComponent || processingComponent === queuedComponent) {
          if (isWaylandToolkit()) {
            // The mouse pointer has left the tooltip's "parent" component. Don't immediately
            // cancel the popup, wait a moment to see if the mouse entered the popup itself.
            alarm.addRequest({
                               hideCurrent(me, null, null)
                             }, 200)
          }
          else {
            hideCurrent(me, null, null)
          }
        }
      }
      else if (me.id == MouseEvent.MOUSE_MOVED) {
        if (processingComponent === currentComponent || processingComponent === queuedComponent) {
          if (balloon != null && balloon!!.wasFadedIn()) {
            maybeShowFor(processingComponent, me)
          }
          else {
            if (!currentTipIsCentered) {
              x = me.x
              y = me.y
              if (processingComponent is JComponent &&
                  !isTooltipDefined(processingComponent as JComponent, me) &&
                  (queuedTooltip == null || !queuedTooltip!!.isHint)
              ) {
                hideCurrent(me, null, null) //There is no tooltip or hint here, let's proceed it as MOUSE_EXITED
              }
              else {
                maybeShowFor(processingComponent, me)
              }
            }
          }
        }
        else if (currentComponent == null && queuedComponent == null) {
          maybeShowFor(processingComponent, me)
        }
        else if (queuedComponent == null) {
          hideCurrent(me)
        }
      }
      else if (me.id == MouseEvent.MOUSE_PRESSED) {
        val clickOnTooltip = balloon != null &&
                             balloon === JBPopupFactory.getInstance().getParentBalloonFor(processingComponent)
        if (processingComponent === currentComponent || (clickOnTooltip && !isClickProcessor(balloon))) {
          hideCurrent(me, null, null, null, !clickOnTooltip)
        }
      }
      else if (me.id == MouseEvent.MOUSE_DRAGGED) {
        hideCurrent(me, null, null)
      }
    }
    finally {
      processingComponent = null
    }
  }

  private fun componentContextHasChanged(eventComponent: Component?): Boolean {
    if (eventComponent === currentComponent) return false

    if (queuedTooltip != null) {
      // The case when a tooltip is going to appear on the Component but the MOUSE_ENTERED event comes to the Component before it,
      // we don't want to hide the tooltip in that case (IDEA-194208)
      val tooltipPoint = queuedTooltip!!.point
      if (tooltipPoint != null) {
        val realQueuedComponent =
          SwingUtilities.getDeepestComponentAt(queuedTooltip!!.component, tooltipPoint.x, tooltipPoint.y)
        return eventComponent !== realQueuedComponent
      }
    }

    return true
  }

  private fun maybeShowFor(c: Component?, me: MouseEvent) {
    showForComponent(c, me, false)
  }

  private fun showForComponent(c: Component?, me: MouseEvent, now: Boolean) {
    if (c !is JComponent) return

    val wnd = SwingUtilities.getWindowAncestor(c)
    if (wnd == null) return

    if (!wnd.isActive) {
      if (JBPopupFactory.getInstance().isChildPopupFocused(wnd)) return
    }

    if (!isTooltipDefined(c, me)) {
      hideCurrent(null)
      return
    }

    val centerDefault = c.getClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT) == true
    val centerStrict = c.getClientProperty(UIUtil.CENTER_TOOLTIP_STRICT) == true
    var shift = if (centerStrict) 0 else if (centerDefault) 4 else 0

    // Balloon may appear exactly above useful content, such behavior is rather annoying.
    var rowBounds: Rectangle? = null
    if (c is JTree) {
      val path = c.getClosestPathForLocation(me.x, me.y)
      if (path != null) {
        rowBounds = c.getPathBounds(path)
      }
    }
    else if (c is JList<*>) {
      val row = c.locationToIndex(me.point)
      if (row > -1) {
        rowBounds = c.getCellBounds(row, row)
      }
    }
    if (rowBounds != null && rowBounds.y + 4 < me.y) {
      shift += me.y - rowBounds.y - 4
    }

    showTooltipForEvent(
      c = c,
      me = me,
      toCenter = centerStrict || centerDefault,
      shift = shift,
      posChangeX = -shift,
      posChangeY = -shift,
      now = now,
    )
  }

  private fun isTooltipDefined(component: JComponent, event: MouseEvent): Boolean {
    return !component.getToolTipText(event).isNullOrEmpty() || getCustomTooltip(component) != null
  }

  private fun showTooltipForEvent(
    c: JComponent,
    me: MouseEvent,
    toCenter: Boolean,
    shift: Int,
    posChangeX: Int,
    posChangeY: Int,
    now: Boolean,
  ) {
    var tooltip = getCustomTooltip(c)
    if (tooltip == null) {
      if (helpTooltipManager != null) {
        currentComponent = c
        hideHelpTooltip = true
        helpTooltipManager!!.showTooltip(c, me)
        return
      }

      val aText = c.getToolTipText(me).toString()
      tooltip = object : IdeTooltip(c, me.point, null,  /*new Object()*/c, aText) {
        override fun beforeShow(): Boolean {
          currentEvent = me

          if (!c.isShowing) {
            return false
          }

          val text = c.getToolTipText(currentEvent)
          if (text.isNullOrBlank()) {
            return false
          }

          val visibleRect = if (c.parent is JViewport) {
            (c.parent as JViewport).viewRect
          }
          else if (IdeMouseEventDispatcher.isDiagramViewComponent(c)) {
            c.bounds
          }
          else {
            c.visibleRect
          }
          if (!visibleRect.contains(point)) {
            return false
          }

          val layeredPane = ComponentUtil.getParentOfType(JLayeredPane::class.java, c as Component)
          val pane = initPane(text = text, hintHint = HintHint(me).setAwtTooltip(true), layeredPane = layeredPane)
          val wrapper = Wrapper(pane)
          tipComponent = wrapper
          return true
        }
      }
        .setToCenter(toCenter)
        .setCalloutShift(shift)
        .setPositionChangeShift(posChangeX, posChangeY)
        .setLayer(Balloon.Layer.top)
    }
    else if (currentTooltip === tooltip) {
      // don't re-show the same custom tooltip on every mouse movement
      return
    }

    show(tooltip!!, now)
  }

  /**
   * Checks the component for tooltip visualization activities.
   * Can be called from non-dispatch threads.
   *
   * @return true if the component is taken a part in any tooltip activity
   */
  @ApiStatus.Experimental
  @Contract(value = "null -> false", pure = true)
  fun isProcessing(tooltipOwner: Component?): Boolean {
    return tooltipOwner != null &&
           (tooltipOwner === currentComponent || tooltipOwner === queuedComponent || tooltipOwner === processingComponent)
  }

  /**
   * Updates shown tooltip pop-up in current position with actual tooltip text if it is already visible.
   * The action is useful for background-calculated tooltip (ex. crumbs tooltips).
   * Does nothing in other cases.
   *
   * @param tooltipOwner for which the tooltip is updating
   */
  @ApiStatus.Experimental
  fun updateShownTooltip(tooltipOwner: Component?) {
    if (!hasCurrent() || currentComponent == null || currentComponent !== tooltipOwner) {
      return
    }

    try {
      val reposition: MouseEvent
      val currentEvent = currentEvent!!
      if (GraphicsEnvironment.isHeadless()) {
        reposition = currentEvent
      }
      else {
        val topLeftComponent = currentComponent!!.locationOnScreen
        val screenLocation = MouseInfo.getPointerInfo().location
        @Suppress("DEPRECATION")
        reposition = MouseEvent(
          currentEvent.component,
          currentEvent.id,
          currentEvent.getWhen(),
          currentEvent.modifiers,
          screenLocation.x - topLeftComponent.x,
          screenLocation.y - topLeftComponent.y,
          screenLocation.x,
          screenLocation.y,
          currentEvent.clickCount,
          currentEvent.isPopupTrigger,
          currentEvent.button)
      }
      showForComponent(c = currentComponent, me = reposition, now = true)
    }
    catch (ignore: IllegalComponentStateException) {
    }
  }

  fun setCustomTooltip(component: JComponent, tooltip: IdeTooltip?) {
    component.putClientProperty(CUSTOM_TOOLTIP, tooltip)
    // We need to register a dummy mouse listener to make sure events will be generated for this specific component, not its parent
    component.removeMouseListener(DUMMY_LISTENER)
    component.removeMouseMotionListener(DUMMY_LISTENER)
    if (tooltip != null) {
      component.addMouseListener(DUMMY_LISTENER)
      component.addMouseMotionListener(DUMMY_LISTENER)
    }
  }

  fun getCustomTooltip(component: JComponent?): IdeTooltip? = ClientProperty.get(component, CUSTOM_TOOLTIP)

  fun show(tooltip: IdeTooltip, now: Boolean): IdeTooltip = show(tooltip = tooltip, now = now, animationEnabled = true)

  fun show(tooltip: IdeTooltip, now: Boolean, animationEnabled: Boolean): IdeTooltip {
    alarm.cancelAllRequests()

    hideCurrent(null, tooltip)

    queuedComponent = tooltip.component
    queuedTooltip = tooltip

    showRequest = Runnable {
      if (showRequest == null) {
        return@Runnable
      }
      if (queuedComponent !== tooltip.component || !tooltip.component.isShowing) {
        hideCurrent(null, tooltip, null, null, animationEnabled)
        return@Runnable
      }
      if (tooltip.beforeShow()) {
        doShow(tooltip, animationEnabled)
      }
      else {
        hideCurrent(null, tooltip, null, null, animationEnabled)
      }
    }

    if (now) {
      showRequest!!.run()
    }
    else {
      alarm.addRequest(showRequest!!, if (showDelay) tooltip.showDelay else tooltip.initialReshowDelay)
    }

    return tooltip
  }

  private fun doShow(tooltip: IdeTooltip, animationEnabled: Boolean) {
    val toCenterX: Boolean
    val toCenterY: Boolean

    var toCenter = tooltip.isToCenter
    var small = false
    if (!toCenter && tooltip.isToCenterIfSmall) {
      val size = tooltip.component.size
      toCenterX = size.width < 64
      toCenterY = size.height < 64
      toCenter = toCenterX || toCenterY
      small = true
    }
    else {
      toCenterX = true
      toCenterY = true
    }

    var effectivePoint = tooltip.point
    if (effectivePoint == null) {
      LOG.warn("No point specified for a tooltip for the component " + tooltip.component)
      effectivePoint = Point()
      // might as well just center it, since we're not given a point
      toCenter = true
    }
    if (toCenter) {
      val bounds = tooltip.component.bounds
      effectivePoint.x = if (toCenterX) bounds.width / 2 else effectivePoint.x
      effectivePoint.y = if (toCenterY) bounds.height / 2 else effectivePoint.y
    }

    if (currentComponent === tooltip.component && balloon != null && !balloon!!.isDisposed) {
      balloon!!.show(RelativePoint(tooltip.component, effectivePoint), tooltip.preferredPosition)
      return
    }

    if (currentComponent === tooltip.component && effectivePoint == Point(x, y)) {
      return
    }

    val bg = if (tooltip.textBackground != null) tooltip.textBackground else getTextBackground(true)
    val fg = if (tooltip.textForeground != null) tooltip.textForeground else getTextForeground(true)
    val borderColor = if (tooltip.borderColor != null) tooltip.borderColor else JBUI.CurrentTheme.Tooltip.borderColor()

    val builder = JBPopupFactory.getInstance().createBalloonBuilder(tooltip.tipComponent)
      .setFillColor(bg)
      .setBorderColor(borderColor)
      .setBorderInsets(tooltip.borderInsets)
      .setAnimationCycle(if (animationEnabled) RegistryManager.getInstance().intValue("ide.tooltip.animationCycle") else 0)
      .setShowCallout(true)
      .setPointerSize(tooltip.pointerSize)
      .setCalloutShift(if (small && tooltip.calloutShift == 0) 2 else tooltip.calloutShift)
      .setPositionChangeXShift(tooltip.positionChangeX)
      .setPositionChangeYShift(tooltip.positionChangeY)
      .setHideOnKeyOutside(!tooltip.isExplicitClose)
      .setHideOnAction(!tooltip.isExplicitClose)
      .setRequestFocus(tooltip.isRequestFocus)
      .setLayer(tooltip.layer)
    tooltip.tipComponent.foreground = fg
    tooltip.tipComponent.border = tooltip.componentBorder
    tooltip.tipComponent.font = if (tooltip.font != null) tooltip.font else getTextFont(true)

    if (tooltip.isPointerShiftedToStart) {
      builder.setPointerShiftedToStart(true).setCornerRadius(JBUI.scale(8))
    }

    balloon = builder.createBalloon()

    balloon!!.setAnimationEnabled(animationEnabled)
    tooltip.setUi(if (balloon is IdeTooltip.Ui) balloon as IdeTooltip.Ui else null)
    currentComponent = tooltip.component
    x = effectivePoint.x
    y = effectivePoint.y
    currentTipIsCentered = toCenter
    currentTooltip = tooltip
    showRequest = null
    queuedComponent = null
    queuedTooltip = null

    lastDisposable = balloon
    Disposer.register(lastDisposable!!) { lastDisposable = null }

    balloon!!.show(RelativePoint(tooltip.component, effectivePoint), tooltip.preferredPosition)
    alarm.addRequest({
                       if (currentTooltip === tooltip && tooltip.canBeDismissedOnTimeout()) {
                         hideCurrent(null, null, null)
                       }
                     }, tooltip.dismissDelay)
  }

  fun getTextForeground(@Suppress("UNUSED_PARAMETER") awtTooltip: Boolean): Color = UIUtil.getToolTipForeground()

  fun getLinkForeground(@Suppress("UNUSED_PARAMETER") awtTooltip: Boolean): Color = JBUI.CurrentTheme.Link.Foreground.ENABLED

  fun getTextBackground(@Suppress("UNUSED_PARAMETER") awtTooltip: Boolean): Color {
    val color = EditorColorsUtil.getGlobalOrDefaultColor(TOOLTIP_COLOR_KEY)
    return color ?: UIUtil.getToolTipBackground()
  }

  @Suppress("SpellCheckingInspection")
  fun getUlImg(@Suppress("UNUSED_PARAMETER") awtTooltip: Boolean): String {
    return if (isDarkTheme) "/general/mdot-white.png" else "/general/mdot.png"
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("use {@link JBUI.CurrentTheme.Tooltip#borderColor()} instead.")
  fun getBorderColor(@Suppress("UNUSED_PARAMETER") awtTooltip: Boolean): Color = JBUI.CurrentTheme.Tooltip.borderColor()

  fun isOwnBorderAllowed(awtTooltip: Boolean): Boolean = !awtTooltip

  fun isOpaqueAllowed(awtTooltip: Boolean): Boolean = !awtTooltip

  fun getTextFont(@Suppress("UNUSED_PARAMETER") awtTooltip: Boolean): Font = UIManager.getFont("ToolTip.font")

  fun hasCurrent(): Boolean = currentTooltip != null

  fun hideCurrent(mouseEvent: MouseEvent?): Boolean = hideCurrent(mouseEvent = mouseEvent, tooltipToShow = null)

  private fun hideCurrent(
    mouseEvent: MouseEvent?,
    tooltipToShow: IdeTooltip? = null,
    action: AnAction? = null,
    event: AnActionEvent? = null,
    animationEnabled: Boolean = isAnimationEnabled(balloon),
  ): Boolean {
    if (helpTooltipManager != null && hideHelpTooltip) {
      hideCurrentNow(false)
      return true
    }

    if (currentTooltip != null && mouseEvent != null && currentTooltip!!.isInside(RelativePoint(mouseEvent))) {
      if (mouseEvent.button == MouseEvent.NOBUTTON || balloon == null || isBlockClicks(balloon!!)) {
        return false
      }
    }

    showRequest = null
    queuedComponent = null
    queuedTooltip = null

    if (currentTooltip == null) {
      return true
    }

    if (balloon != null) {
      val target = if (mouseEvent != null) RelativePoint(mouseEvent) else null
      val isInsideOrMovingForward = target != null && (isInside(balloon!!, target) || isMovingForward(balloon!!, target))
      val canAutoHide = currentTooltip!!.canAutohideOn(TooltipEvent(mouseEvent, isInsideOrMovingForward, action, event))
      val implicitMouseMove = mouseEvent != null &&
                              (mouseEvent.id == MouseEvent.MOUSE_MOVED || mouseEvent.id == MouseEvent.MOUSE_EXITED || mouseEvent.id == MouseEvent.MOUSE_ENTERED)
      if (!canAutoHide
          || (isInsideOrMovingForward && implicitMouseMove)
          || (currentTooltip!!.isExplicitClose && implicitMouseMove)
          || (tooltipToShow != null && !tooltipToShow.isHint && Comparing.equal(currentTooltip, tooltipToShow))
      ) {
        if (hideRunnable != null) {
          hideRunnable = null
        }
        return false
      }
    }

    hideRunnable = Runnable {
      if (hideRunnable != null) {
        hideCurrentNow(animationEnabled)
        hideRunnable = null
      }
    }

    if (mouseEvent != null && mouseEvent.button == MouseEvent.NOBUTTON) {
      alarm.addRequest(hideRunnable!!, RegistryManager.getInstance().intValue("ide.tooltip.autoDismissDeadZone"))
    }
    else {
      hideRunnable?.run()
      hideRunnable = null
    }

    return true
  }

  fun hideCurrentNow(animationEnabled: Boolean) {
    helpTooltipManager?.hideTooltip()

    balloon?.let { balloon ->
      balloon.setAnimationEnabled(animationEnabled)
      balloon.hide()
      currentTooltip!!.onHidden()
      showDelay = false
      alarm.addRequest({ showDelay = true }, RegistryManager.getInstance().intValue("ide.tooltip.reshowDelay"))
    }

    hideHelpTooltip = false
    showRequest = null
    currentTooltip = null

    balloon = null

    currentComponent = null
    queuedComponent = null
    queuedTooltip = null
    currentEvent = null
    currentTipIsCentered = false
    x = -1
    y = -1
  }

  private fun processEnabled(isHelpTooltipEnabled: Boolean) {
    if (isEnabled) {
      ToolTipManager.sharedInstance().isEnabled = false
      if (isHelpTooltipEnabled) {
        if (helpTooltipManager == null) {
          helpTooltipManager = HelpTooltipManager()
        }
        return
      }
    }
    else {
      ToolTipManager.sharedInstance().isEnabled = true
    }
    helpTooltipManager?.let {
      it.hideTooltip()
      helpTooltipManager = null
    }
  }

  override fun dispose() {
    hideCurrentNow(false)
    if (lastDisposable != null) {
      Disposer.dispose(lastDisposable!!)
    }
  }

  fun hide(tooltip: IdeTooltip?) {
    if (currentTooltip === tooltip || tooltip == null || tooltip === queuedTooltip) {
      hideCurrent(mouseEvent = null, tooltipToShow = null, action = null)
    }
  }

  fun cancelAutoHide() {
    hideRunnable = null
  }

  fun isQueuedToShow(tooltip: IdeTooltip?): Boolean = queuedTooltip == tooltip
}

private class LimitedWidthJBHtmlPane(
  styleConfiguration: JBHtmlPaneStyleConfiguration,
  paneConfiguration: JBHtmlPaneConfiguration,
  private val prefSizeWasComputed: Ref<Boolean>,
  private val hintHint: HintHint,
  private val layeredPane: JLayeredPane?,
) : JBHtmlPane(styleConfiguration, paneConfiguration) {
  private var prefSize: Dimension? = null

  override fun getPreferredSize(): Dimension {
    if (!prefSizeWasComputed.get() && hintHint.isAwtTooltip) {
      var lp = layeredPane
      if (lp == null) {
        val rootPane = UIUtil.getRootPane(this)
        if (rootPane != null && rootPane.size.width > 0) {
          lp = rootPane.layeredPane
        }
      }

      val size: Dimension
      if (lp != null) {
        targetToDevice(this, lp)
        size = lp.size
        prefSizeWasComputed.set(true)
      }
      else {
        size = ScreenUtil.getScreenRectangle(0, 0).size
      }
      val fitWidth = (size.width * 0.8).toInt()
      val prefSizeOriginal = super.getPreferredSize()
      if (prefSizeOriginal.width > fitWidth) {
        setSize(Dimension(fitWidth, Int.MAX_VALUE))
        val fixedWidthSize = super.getPreferredSize()
        val minSize = super.getMinimumSize()
        prefSize = Dimension(max(fitWidth.toDouble(), minSize.width.toDouble()).toInt(), fixedWidthSize.height)
      }
      else {
        prefSize = Dimension(prefSizeOriginal)
      }
    }

    val s = if (prefSize == null) super.getPreferredSize() else Dimension(prefSize)
    border?.let {
      JBInsets.addTo(s, it.getBorderInsets(this))
    }
    return s
  }

  override fun setPreferredSize(preferredSize: Dimension) {
    super.setPreferredSize(preferredSize)
    prefSize = preferredSize
  }
}
