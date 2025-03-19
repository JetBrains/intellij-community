// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getBoolean
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.Strings
import com.intellij.ui.*
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.tabs.JBTabsEx
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.UiDecorator.UiDecoration
import com.intellij.ui.tabs.impl.JBTabsImpl.Companion.isSelectionClick
import com.intellij.ui.tabs.impl.TabLabel.MergedUiDecoration
import com.intellij.util.MathUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.StartupUiUtil.labelFont
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*
import java.awt.BorderLayout.NORTH
import java.awt.BorderLayout.SOUTH
import java.awt.event.*
import java.util.function.Function
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import kotlin.math.max
import kotlin.math.min

@Suppress("LeakingThis")
open class TabLabel @Internal constructor(
  @JvmField @Internal protected val tabs: JBTabsImpl,
  val info: TabInfo,
) : JPanel(/* isDoubleBuffered = */ false), Accessible, UiCompatibleDataProvider {
  // if this System property is set to true 'close' button would be shown on the left of text (it's on the right by default)
  @JvmField
  protected val label: SimpleColoredComponent

  private val icon: LayeredIcon
  private var overlaidIcon: Icon? = null

  @JvmField
  @Internal
  protected var actionPanel: ActionPanel? = null
  private var isCentered = false

  @JvmField
  internal var isCompressionEnabled: Boolean = false

  var isForcePaintBorders: Boolean = false

  private val labelPlaceholder = Wrapper(/* isDoubleBuffered = */ false)

  init {
    label = createLabel(tabs = tabs, info = info)

    // Allow focus so that user can TAB into the selected TabLabel and then
    // navigate through the other tabs using the LEFT/RIGHT keys.
    isFocusable = ScreenReader.isActive()
    isOpaque = false
    layout = TabLabelLayout()

    labelPlaceholder.isOpaque = false
    labelPlaceholder.isFocusable = false
    label.isFocusable = false
    @Suppress("LeakingThis")
    add(labelPlaceholder, BorderLayout.CENTER)

    @Suppress("LeakingThis")
    setAlignmentToCenter(true)

    icon = createLayeredIcon()

    @Suppress("LeakingThis")
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_PRESSED)) {
          return
        }

        if (isSelectionClick(e) && info.isEnabled) {
          val selectedInfo = tabs.selectedInfo
          if (selectedInfo != info) {
            this@TabLabel.info.previousSelection = selectedInfo
          }
          val c = SwingUtilities.getDeepestComponentAt(e.component, e.x, e.y)
          if (c is InplaceButton) {
            return
          }

          tabs.select(info = info, requestFocus = true)
          val container = PopupUtil.getPopupContainerFor(this@TabLabel)
          if (container != null && ClientProperty.isTrue(container.content, MorePopupAware::class.java)) {
            container.cancel()
          }
        }
        else {
          handlePopup(e)
        }
      }

      override fun mouseClicked(e: MouseEvent) {
        handlePopup(e)
      }

      override fun mouseReleased(e: MouseEvent) {
        info.previousSelection = null
        handlePopup(e)
      }

      override fun mouseEntered(e: MouseEvent) {
        isHovered = true
      }

      override fun mouseExited(e: MouseEvent) {
        isHovered = false
      }
    })

    if (isFocusable) {
      // Navigate to the previous/next tab when LEFT/RIGHT is pressed.
      @Suppress("LeakingThis")
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_LEFT) {
            val index = this@TabLabel.tabs.getIndexOf(this@TabLabel.info)
            if (index >= 0) {
              e.consume()
              // Select the previous tab, then set the focus its TabLabel.
              val previous = this@TabLabel.tabs.findEnabledBackward(index, true)
              if (previous != null) {
                this@TabLabel.tabs.select(previous, false).doWhenDone { this@TabLabel.tabs.selectedLabel!!.requestFocusInWindow() }
              }
            }
          }
          else if (e.keyCode == KeyEvent.VK_RIGHT) {
            val index = this@TabLabel.tabs.getIndexOf(this@TabLabel.info)
            if (index >= 0) {
              e.consume()
              // Select the previous tab, then set the focus its TabLabel.
              val next = this@TabLabel.tabs.findEnabledForward(index, true)
              if (next != null) {
                // Select the next tab, then set the focus its TabLabel.
                this@TabLabel.tabs.select(next, false).doWhenDone { this@TabLabel.tabs.selectedLabel!!.requestFocusInWindow() }
              }
            }
          }
        }
      })

      // Repaint when we gain/lost focus so that the focus cue is displayed.
      addFocusListener(object : FocusListener {
        override fun focusGained(e: FocusEvent) {
          repaint()
        }

        override fun focusLost(e: FocusEvent) {
          repaint()
        }
      })
    }
  }

  open var isHovered: Boolean
    get() = tabs.isHoveredTab(this)
    protected set(value) {
      if (isHovered == value) {
        return
      }

      if (value) {
        tabs.setHovered(this)
      }
      else {
        tabs.unHover(this)
      }
    }

  private val isSelected: Boolean
    get() = tabs.selectedLabel === this

  // we don't want the focus unless we are the selected tab
  override fun isFocusable(): Boolean = tabs.selectedLabel === this && super.isFocusable()

  private fun createLabel(tabs: JBTabsImpl, info: TabInfo?): SimpleColoredComponent {
    val label: SimpleColoredComponent = object : SimpleColoredComponent() {
      override fun getFont(): Font {
        val font = super.getFont()
        if (isFontSet || !tabs.useSmallLabels()) {
          return font
        }
        else {
          return RelativeFont.NORMAL.fromResource("EditorTabs.fontSizeOffset", -2, scale(11f)).derive(labelFont)
        }
      }

      override fun getActiveTextColor(attributesColor: Color?): Color? {
        val painterAdapter = tabs.tabPainterAdapter
        val theme = painterAdapter.getTabTheme()
        val foreground = if (tabs.selectedInfo == info && (attributesColor == null || UIUtil.getLabelForeground() == attributesColor)) {
          if (tabs.isActiveTabs(info)) theme.underlinedTabForeground else theme.underlinedTabInactiveForeground
        }
        else {
          super.getActiveTextColor(attributesColor)
        }
        return editLabelForeground(foreground)
      }

      override fun paintIcon(g: Graphics, icon: Icon, offset: Int) {
        val iconAlpha = getIconAlpha()
        if (iconAlpha == 1f) {
          super.paintIcon(g, icon, offset)
        }
        else {
          GraphicsUtil.paintWithAlpha(g, iconAlpha) {
            super.paintIcon(g, icon, offset)
          }
        }
      }
    }
    label.isOpaque = false
    label.border = null
    label.isIconOpaque = false
    label.ipad = JBInsets.emptyInsets()

    return label
  }

  // allows to edit the label foreground right before painting
  open fun editLabelForeground(baseForeground: Color?): Color? = baseForeground

  // allows editing the icon right before painting
  open fun getIconAlpha(): Float = 1f

  val isPinned: Boolean
    get() = info.isPinned

  override fun getPreferredSize(): Dimension {
    val size = notStrictPreferredSize
    if (isPinned) {
      size.width = min(TabLayout.getMaxPinnedTabWidth(), size.width)
    }
    return size
  }

  val notStrictPreferredSize: Dimension
    get() = super.getPreferredSize()

  open fun setAlignmentToCenter(toCenter: Boolean) {
    if (isCentered != toCenter || labelComponent.parent == null) {
      setPlaceholderContent(toCenter = toCenter, component = labelComponent)
    }
  }

  protected fun setPlaceholderContent(toCenter: Boolean, component: JComponent) {
    labelPlaceholder.removeAll()

    val content: JComponent = if (toCenter) Centerizer(component, Centerizer.TYPE.BOTH) else Centerizer(component, Centerizer.TYPE.VERTICAL)
    labelPlaceholder.setContent(content)

    isCentered = toCenter
  }

  fun paintOffscreen(g: Graphics) {
    synchronized(treeLock) {
      validateTree()
    }
    doPaint(g)
  }

  final override fun paint(g: Graphics) {
    if (tabs.isDropTarget(info)) {
      if (tabs.dropSide == -1) {
        g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
        g.fillRect(0, 0, width, height)
      }
      return
    }

    doPaint(g)
    if (shouldPaintFadeout()) {
      paintFadeout(g)
    }
  }

  protected open fun shouldPaintFadeout(): Boolean = !Registry.`is`("ui.no.bangs.and.whistles", false) && tabs.isSingleRow

  protected fun paintFadeout(g: Graphics) {
    val g2d = g.create() as Graphics2D
    try {
      val tabBg = effectiveBackground
      val transparent = ColorUtil.withAlpha(tabBg, 0.0)
      val borderThickness = tabs.borderThickness
      val width = JBUI.scale(MathUtil.clamp(Registry.intValue("ide.editor.tabs.fadeout.width", 10), 1, 200))

      val rect = bounds
      rect.height -= borderThickness + (if (isSelected) tabs.tabPainter.getTabTheme().underlineHeight else borderThickness)
      // Fadeout for left part (needed only in top and bottom placements)
      if (rect.x < 0) {
        val leftRect = Rectangle(-rect.x, borderThickness, width, rect.height - 2 * borderThickness)
        paintGradientRect(g2d, leftRect, tabBg, transparent)
      }

      val contentRect = labelPlaceholder.bounds
      // Fadeout for right side before pin/close button (needed only in side placements and in squeezing layout)
      if (contentRect.width < labelPlaceholder.preferredSize.width + tabs.tabHGap) {
        val rightRect = Rectangle(contentRect.x + contentRect.width - width, borderThickness, width, rect.height - 2 * borderThickness)
        paintGradientRect(g2d, rightRect, transparent, tabBg)
      }
      else if (tabs.effectiveLayout.isScrollable && rect.width < preferredSize.width + tabs.tabHGap
      ) {
        val rightRect = Rectangle(rect.width - width, borderThickness, width, rect.height - 2 * borderThickness)
        paintGradientRect(g2d, rightRect, transparent, tabBg)
      }
    }
    finally {
      g2d.dispose()
    }
  }

  private fun doPaint(g: Graphics) {
    super.paint(g)
  }

  val isLastPinned: Boolean
    get() {
      if (info.isPinned && getBoolean("editor.keep.pinned.tabs.on.left")) {
        val tabs = tabs.tabs
        for (i in tabs.indices) {
          val cur = tabs[i]
          if (cur == info && i < tabs.size - 1) {
            val next = tabs[i + 1]
            // check that cur and next are in the same row
            return (!next.isPinned && this.tabs.getTabLabel(next)!!.y == this.y)
          }
        }
      }
      return false
    }

  val isNextToLastPinned: Boolean
    get() {
      if (!info.isPinned && getBoolean("editor.keep.pinned.tabs.on.left")) {
        val tabs = tabs.getVisibleInfos()
        var wasPinned = false
        for (info in tabs) {
          if (wasPinned && info == this.info) {
            return true
          }
          wasPinned = info.isPinned
        }
      }
      return false
    }

  val isLastInRow: Boolean
    get() {
      val infos = tabs.getVisibleInfos()
      for (ind in 0 until infos.size - 1) {
        val cur = tabs.getTabLabel(infos[ind])
        if (cur === this) {
          val next = tabs.getTabLabel(infos[ind + 1])
          return cur.y != next!!.y
        }
      }
      // can be empty in case of dragging tab label
      return !infos.isEmpty() && infos[infos.size - 1] == info
    }

  protected open fun handlePopup(e: MouseEvent) {
    if (e.clickCount != 1 || !e.isPopupTrigger || PopupUtil.getPopupContainerFor(this) != null) {
      return
    }

    if (e.x < 0 || e.x >= e.component.width || e.y < 0 || e.y >= e.component.height) {
      return
    }

    var place = tabs.popupPlace
    place = place ?: ActionPlaces.UNKNOWN
    tabs.popupInfo = info

    val tabsPopupGroup = tabs.popupGroup
    val thisTabs = this.tabs
    val tabNavigationGroup = this.tabs.run { navigationActions.takeIf { addNavigationGroup } }
    val toShow = object : ActionGroup(), DumbAware {
      override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
        val tabs = e?.getData(JBTabsEx.NAVIGATION_ACTIONS_KEY) as? JBTabsImpl

        return (tabsPopupGroup?.let { ActionWrapperUtil.getChildren(e, this, it) + Separator() } ?: emptyArray()) +
               (tabNavigationGroup.takeIf { tabs === thisTabs }?.let { ActionWrapperUtil.getChildren(e, this, it) } ?: emptyArray())
      }
    }
    this.tabs.activePopup = ActionManager.getInstance().createActionPopupMenu(place, toShow).component
    this.tabs.activePopup!!.addPopupMenuListener(this.tabs.popupListener)

    this.tabs.activePopup!!.addPopupMenuListener(this.tabs)
    JBPopupMenu.showByEvent(e, this.tabs.activePopup!!)
  }

  fun setText(text: SimpleColoredText?) {
    label.change({
                   label.clear()
                   label.icon = if (hasIcons()) icon else null
                   text?.appendToComponent(label)
                 }, false)

    invalidateIfNeeded()
  }

  private fun invalidateIfNeeded() {
    if (label.rootPane == null) {
      return
    }

    val d = label.size
    val pref = label.preferredSize
    if (d != null && d == pref) {
      return
    }

    label.invalidate()

    if (actionPanel != null) {
      actionPanel!!.invalidate()
    }

    tabs.revalidateAndRepaint(false)
  }

  fun setIcon(icon: Icon?) {
    setIcon(icon, 0)
  }

  private fun hasIcons(): Boolean {
    for (layer1 in icon.allLayers) {
      if (layer1 != null) {
        return true
      }
    }
    return false
  }

  private fun setIcon(icon: Icon?, layer: Int) {
    val layeredIcon = this.icon
    layeredIcon.setIcon(icon, layer)
    if (hasIcons()) {
      label.icon = layeredIcon
    }
    else {
      label.icon = null
    }

    invalidateIfNeeded()
  }

  protected fun createLayeredIcon(): LayeredIcon {
    return object : LayeredIcon(2) {
      override fun getIconWidth(): Int {
        val iconWidth = super.getIconWidth()
        val tabWidth = this@TabLabel.width
        val minTabWidth = JBUI.scale(MIN_WIDTH_TO_CROP_ICON)
        return if (isCompressionEnabled && tabWidth < minTabWidth) {
          max(iconWidth - (minTabWidth - tabWidth), iconWidth / 2)
        }
        else {
          iconWidth
        }
      }

      override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create(x, y, iconWidth, iconHeight)
        try {
          super.paintIcon(c, g2, 0, 0)
        }
        finally {
          g2.dispose()
        }
      }
    }
  }

  fun apply(decoration: UiDecoration) {
    if (decoration.labelFont != null) {
      font = decoration.labelFont
      labelComponent.font = decoration.labelFont
    }

    val resultDec = mergeUiDecorations(decoration, JBTabsImpl.defaultDecorator.getDecoration())
    border = EmptyBorder(resultDec.labelInsets)
    label.iconTextGap = resultDec.iconTextGap

    val contentInsets = resultDec.contentInsetsSupplier.apply(actionsPosition)
    labelPlaceholder.border = EmptyBorder(contentInsets)
  }

  open fun setTabActions(group: ActionGroup?) {
    removeOldActionPanel()
    if (group == null) {
      return
    }

    actionPanel = ActionPanel(
      /* tabs = */ tabs,
      /* tabInfo = */ info,
      /* pass = */ { processMouseEvent(SwingUtilities.convertMouseEvent(it.component, it, this)) },
      /* hover = */ { isHovered = it }
    )
    toggleShowActions(false)
    add(actionPanel!!, if (isTabActionsOnTheRight) BorderLayout.EAST else BorderLayout.WEST)

    tabs.revalidateAndRepaint(false)
  }

  protected open val isShowTabActions: Boolean
    get() = true

  protected open val isTabActionsOnTheRight: Boolean
    get() = true

  val actionsPosition: ActionsPosition
    get() {
      return when {
        isShowTabActions && actionPanel != null -> if (isTabActionsOnTheRight) ActionsPosition.RIGHT else ActionsPosition.LEFT
        else -> ActionsPosition.NONE
      }
    }

  private fun removeOldActionPanel() {
    actionPanel?.let {
      it.parent.remove(actionPanel)
      actionPanel = null
    }
  }

  fun updateTabActions(): Boolean = actionPanel != null && actionPanel!!.update()

  private fun setAttractionIcon(icon: Icon?) {
    if (this.icon.getIcon(0) == null) {
      setIcon(null, 1)
      overlaidIcon = icon
    }
    else {
      setIcon(icon, 1)
      overlaidIcon = null
    }
  }

  fun repaintAttraction(): Boolean {
    if (!tabs.attractions.contains(info)) {
      if (icon.isLayerEnabled(1)) {
        icon.setLayerEnabled(1, false)
        setAttractionIcon(null)
        invalidateIfNeeded()
        return true
      }
      return false
    }

    var needsUpdate = false

    if (icon.getIcon(1) !== info.getAlertIcon()) {
      setAttractionIcon(info.getAlertIcon())
      needsUpdate = true
    }

    val maxInitialBlinkCount = 5
    val maxReFireBlinkCount = maxInitialBlinkCount + 2
    if (info.blinkCount < maxInitialBlinkCount && info.isAlertRequested) {
      icon.setLayerEnabled(1, !icon.isLayerEnabled(1))
      if (info.blinkCount == 0) {
        needsUpdate = true
      }
      info.blinkCount += 1

      if (info.blinkCount == maxInitialBlinkCount) {
        info.resetAlertRequest()
      }

      repaint()
    }
    else {
      if (info.blinkCount < maxReFireBlinkCount && info.isAlertRequested) {
        icon.setLayerEnabled(1, !icon.isLayerEnabled(1))
        info.blinkCount += 1

        if (info.blinkCount == maxReFireBlinkCount) {
          info.blinkCount = maxInitialBlinkCount
          info.resetAlertRequest()
        }

        repaint()
      }
      else {
        needsUpdate = !icon.isLayerEnabled(1)
        icon.setLayerEnabled(1, true)
      }
    }

    invalidateIfNeeded()

    return needsUpdate
  }

  final override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    paintBackground(g)
  }

  private fun paintBackground(g: Graphics) {
    tabs.tabPainterAdapter.paintBackground(label = this, g = g, tabs = tabs)
  }

  protected val effectiveBackground: Color
    get() {
      val bg = tabs.tabPainter.getBackgroundColor()
      val customBg = tabs.tabPainter.getCustomBackground(info.tabColor, isSelected, tabs.isActiveTabs(info), isHovered)
      return if (customBg != null) ColorUtil.alphaBlending(customBg, bg) else bg
    }

  final override fun paintChildren(g: Graphics) {
    super.paintChildren(g)

    if (labelComponent.parent == null) {
      return
    }

    val textBounds = SwingUtilities.convertRectangle(labelComponent.parent, labelComponent.bounds, this)
    // Paint border around label if we got the focus
    if (isFocusOwner) {
      g.color = UIUtil.getTreeSelectionBorderColor()
      UIUtil.drawDottedRectangle(g, textBounds.x, textBounds.y, textBounds.x + textBounds.width - 1, textBounds.y + textBounds.height - 1)
    }

    if (overlaidIcon == null) {
      return
    }

    if (icon.isLayerEnabled(1)) {
      val top = (size.height - overlaidIcon!!.iconHeight) / 2

      overlaidIcon!!.paintIcon(this, g, textBounds.x - overlaidIcon!!.iconWidth / 2, top)
    }
  }

  fun setTabActionsAutoHide(autoHide: Boolean) {
    if (actionPanel == null || actionPanel!!.isAutoHide == autoHide) {
      return
    }

    actionPanel!!.isAutoHide = autoHide
  }

  fun toggleShowActions(show: Boolean) {
    actionPanel?.toggleShowActions(show)
  }

  final override fun toString(): String = info.text

  fun setTabEnabled(enabled: Boolean) {
    labelComponent.isEnabled = enabled
  }

  val labelComponent: JComponent
    get() = label

  final override fun getToolTipText(event: MouseEvent): String? {
    val iconWidth = label.icon?.iconWidth ?: JBUI.scale(16)
    if (((label.visibleRect.width >= iconWidth * 2 || !UISettings.getInstance().showTabsTooltips)
         && label.findFragmentAt(RelativePoint(event).getPoint(label).x) == SimpleColoredComponent.FRAGMENT_ICON)
    ) {
      icon.getToolTip(false)?.let {
        return Strings.capitalize(it)
      }
    }
    return super.getToolTipText(event)
  }

  final override fun uiDataSnapshot(sink: DataSink) {
    DataSink.uiDataSnapshot(sink, info.component)
  }

  enum class ActionsPosition {
    RIGHT, LEFT, NONE
  }

  data class MergedUiDecoration(
    @JvmField val labelInsets: Insets,
    @JvmField val contentInsetsSupplier: Function<ActionsPosition, Insets>,
    @JvmField val iconTextGap: Int,
  )

  final override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleTabLabel()
    }
    return accessibleContext
  }

  protected inner class AccessibleTabLabel : AccessibleJPanel() {
    override fun getAccessibleName(): String? = super.getAccessibleName() ?: label.accessibleContext.accessibleName

    override fun getAccessibleDescription(): String? = super.getAccessibleDescription() ?: label.accessibleContext.accessibleDescription

    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PAGE_TAB
  }

  inner class TabLabelLayout : BorderLayout() {
    private var isRightAlignment = false

    fun setRightAlignment(rightAlignment: Boolean) {
      isRightAlignment = rightAlignment
    }

    override fun addLayoutComponent(comp: Component, constraints: Any) {
      checkConstraints(constraints)
      super.addLayoutComponent(comp, constraints)
    }

    override fun layoutContainer(parent: Container) {
      val prefWidth = parent.preferredSize.width
      synchronized(parent.treeLock) {
        when {
          !info.isPinned &&
          tabs.effectiveLayout.isScrollable &&
          (isNewUI() && !isHovered || tabs.isHorizontalTabs) &&
          isShowTabActions && isTabActionsOnTheRight && parent.width < prefWidth -> {
            layoutScrollable(parent)
          }
          !info.isPinned && isCompressionEnabled && !isHovered && !isSelected && parent.width < prefWidth -> {
            layoutCompressible(parent)
          }
          else -> super.layoutContainer(parent)
        }
      }
    }

    private fun layoutScrollable(parent: Container) {
      val spaceTop = parent.insets.top
      val spaceLeft = parent.insets.left
      val spaceBottom = parent.height - parent.insets.bottom
      val spaceHeight = spaceBottom - spaceTop

      var xOffset = spaceLeft
      xOffset = layoutComponent(xOffset, getLayoutComponent(WEST), spaceTop, spaceHeight)
      xOffset -= getShift(parent)
      xOffset = layoutComponent(xOffset, getLayoutComponent(CENTER), spaceTop, spaceHeight)
      layoutComponent(xOffset, getLayoutComponent(EAST), spaceTop, spaceHeight)
    }

    private fun getShift(parent: Container): Int {
      if (isRightAlignment) {
        val width = parent.bounds.width
        if (width > 0) {
          val shift = parent.preferredSize.width - width
          if (shift > 0) {
            return shift
          }
        }
      }
      return 0
    }

    private fun layoutComponent(xOffset: Int, component: Component?, spaceTop: Int, spaceHeight: Int): Int {
      @Suppress("NAME_SHADOWING")
      var xOffset = xOffset
      if (component != null) {
        val prefWestWidth = component.preferredSize.width
        component.setBounds(xOffset, spaceTop, prefWestWidth, spaceHeight)
        xOffset += prefWestWidth + hgap
      }
      return xOffset
    }

    private fun layoutCompressible(parent: Container) {
      val insets = parent.insets
      val height = parent.height - insets.bottom - insets.top
      var curX = insets.left
      val maxX = parent.width - insets.right

      val left = getLayoutComponent(WEST)
      val center = getLayoutComponent(CENTER)
      val right = getLayoutComponent(EAST)

      if (left != null) {
        left.setBounds(0, 0, 0, 0)
        val decreasedLen = parent.preferredSize.width - parent.width
        val width = max((left.preferredSize.width - decreasedLen).toDouble(), 0.0).toInt()
        curX += width
      }

      if (center != null) {
        val width = min(center.preferredSize.width, (maxX - curX))
        center.setBounds(curX, insets.top, width, height)
      }

      right?.setBounds(0, 0, 0, 0)
    }
  }
}

private fun checkConstraints(constraints: Any) {
  if (NORTH == constraints || SOUTH == constraints) {
    LOG.warn(IllegalArgumentException("constraints=$constraints"))
  }
}

private val LOG = logger<TabLabel>()
private const val MIN_WIDTH_TO_CROP_ICON = 39

private fun paintGradientRect(g: Graphics2D, rect: Rectangle, fromColor: Color, toColor: Color) {
  g.paint = GradientPaint(rect.x.toFloat(), rect.y.toFloat(), fromColor, (rect.x + rect.width).toFloat(), rect.y.toFloat(), toColor)
  g.fill(rect)
}

internal fun mergeUiDecorations(customDec: UiDecoration, defaultDec: UiDecoration): MergedUiDecoration {
  return MergedUiDecoration(
    labelInsets = mergeInsets(custom = customDec.labelInsets, def = defaultDec.labelInsets!!),
    contentInsetsSupplier = Function { position ->
      val def = defaultDec.contentInsetsSupplier!!.apply(position)
      if (customDec.contentInsetsSupplier != null) {
        return@Function mergeInsets(customDec.contentInsetsSupplier.apply(position), def)
      }
      def
    },
    iconTextGap = customDec.iconTextGap ?: defaultDec.iconTextGap!!,
  )
}

private fun mergeInsets(custom: Insets?, def: Insets): Insets {
  if (custom != null) {
    @Suppress("UseDPIAwareInsets")
    return Insets(getValue(def.top, custom.top), getValue(def.left, custom.left),
                  getValue(def.bottom, custom.bottom), getValue(def.right, custom.right))
  }
  return def
}

private fun getValue(currentValue: Int, newValue: Int): Int = if (newValue == -1) currentValue else newValue
