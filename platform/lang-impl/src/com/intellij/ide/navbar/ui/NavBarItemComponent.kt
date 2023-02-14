// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.ide.navbar.vm.NavBarItemVm
import com.intellij.ide.navigationToolbar.ui.AbstractNavBarUI
import com.intellij.ide.navigationToolbar.ui.AbstractNavBarUI.getDecorationOffset
import com.intellij.ide.navigationToolbar.ui.AbstractNavBarUI.getFirstElementLeftOffset
import com.intellij.ide.navigationToolbar.ui.ImageType
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.StatusBar.Breadcrumbs
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.*
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

internal class NavBarItemComponent(
  cs: CoroutineScope,
  private val vm: NavBarItemVm,
  private val panel: NewNavBarPanel,
) : SimpleColoredComponent() {

  init {
    isOpaque = false
    ipad = navBarItemInsets()
    if (ExperimentalUI.isNewUI()) {
      iconTextGap = JBUIScale.scale(4)
    }
    myBorder = null
    border = null
    if (isItemComponentFocusable()) {
      // Take ownership of Tab/Shift-Tab navigation (to move focus out of nav bar panel), as
      // navigation between items is handled by the Left/Right cursor keys. This is similar
      // to the behavior a JRadioButton contained inside a GroupBox.
      isFocusable = true
      focusTraversalKeysEnabled = false
      addKeyListener(NavBarItemComponentTabKeyListener(panel))
      if (isFloating) {
        addFocusListener(NavBarDialogFocusListener(panel))
      }
    }
    else {
      isFocusable = false
    }
    font = RelativeFont.NORMAL.fromResource("NavBar.fontSizeOffset", 0).derive(font)

    cs.launch(Dispatchers.EDT, CoroutineStart.UNDISPATCHED) {
      vm.selected.collect {
        update()
      }
    }

    addMouseListener(ItemPopupHandler())
    addMouseListener(ItemMouseListener())
  }

  private inner class ItemPopupHandler : PopupHandler() {

    override fun invokePopup(comp: Component?, x: Int, y: Int) {
      focusItem()
      vm.select()
      ActionManager.getInstance()
        .createActionPopupMenu(ActionPlaces.NAVIGATION_BAR_POPUP, NavBarContextMenuActionGroup())
        .also {
          it.setTargetComponent(panel)
        }
        .component
        .show(panel, this@NavBarItemComponent.x + x, this@NavBarItemComponent.y + y)
    }
  }

  private inner class ItemMouseListener : MouseAdapter() {

    override fun mousePressed(e: MouseEvent) {
      if (!SystemInfo.isWindows) {
        click(e)
      }
    }

    override fun mouseReleased(e: MouseEvent) {
      if (SystemInfo.isWindows) {
        click(e)
      }
    }

    private fun click(e: MouseEvent) {
      if (e.isConsumed) {
        return
      }
      if (e.isPopupTrigger) {
        return
      }
      if (e.clickCount == 1) {
        focusItem()
        vm.select()
        vm.showPopup()
        e.consume()
      }
      else if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
        vm.activate()
        e.consume()
      }
    }

    override fun mouseEntered(e: MouseEvent) {
      if (e.isConsumed || !ExperimentalUI.isNewUI()) {
        return
      }
      isHovered = true
      update()
      e.consume()
    }

    override fun mouseExited(e: MouseEvent) {
      if (e.isConsumed || !ExperimentalUI.isNewUI()) {
        return
      }
      isHovered = false
      update()
      e.consume()
    }
  }

  val text: @Nls String get() = vm.presentation.text

  private val isFloating: Boolean get() = panel.isFloating

  private val isSelected: Boolean get() = vm.selected.value

  private val isFocused: Boolean get() = panel.isItemFocused()

  private var isHovered: Boolean = false

  override fun getFont(): Font? = navBarItemFont()

  override fun setOpaque(isOpaque: Boolean): Unit = super.setOpaque(false)

  override fun getMinimumSize(): Dimension = preferredSize

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    val offsets = Dimension()
    JBInsets.addTo(offsets, navBarItemPadding(isFloating))
    val newUI = ExperimentalUI.isNewUI()
    if (newUI) {
      offsets.width += if (vm.isFirst) 0 else CHEVRON_ICON.iconWidth + Breadcrumbs.CHEVRON_INSET.get()
    }
    else {
      offsets.width += getDecorationOffset() + if (vm.isFirst) getFirstElementLeftOffset() else 0
    }
    return Dimension(size.width + offsets.width, size.height + offsets.height)
  }

  fun focusItem() {
    val focusComponent: JComponent = if (isFocusable) this@NavBarItemComponent else panel
    IdeFocusManager.getInstance(panel.project).requestFocus(focusComponent, true)
  }

  fun update() {
    clear()

    val selected = isSelected
    val focused = isFocused

    val presentation = vm.presentation
    val attributes = presentation.textAttributes
    val fg: Color? = when {
      !ExperimentalUI.isNewUI() -> navBarItemForeground(selected, focused, vm.isInactive()) ?: attributes.fgColor
      isHovered -> Breadcrumbs.HOVER_FOREGROUND
      selected -> if (focused) Breadcrumbs.SELECTION_FOREGROUND else Breadcrumbs.SELECTION_INACTIVE_FOREGROUND
      else -> if (isFloating) Breadcrumbs.FLOATING_FOREGROUND else Breadcrumbs.FOREGROUND
    }
    val bg = navBarItemBackground(selected, focused)
    val waveColor = if (ExperimentalUI.isNewUI()) null else attributes.waveColor
    val style = if (ExperimentalUI.isNewUI()) SimpleTextAttributes.STYLE_PLAIN else attributes.style

    icon = effectiveIcon(presentation)
    background = bg
    append(presentation.text, SimpleTextAttributes(bg, fg, waveColor, style))
  }

  private fun effectiveIcon(presentation: NavBarItemPresentation): Icon? {
    return when {
      ExperimentalUI.isNewUI() && vm.isModuleContentRoot -> MODULE_ICON
      Registry.`is`("navBar.show.icons") || vm.isLast || presentation.hasContainingFile -> presentation.icon
      else -> null
    }
  }

  override fun shouldDrawBackground(): Boolean {
    return isSelected && isFocused
  }

  private val cache: MutableMap<ImageType, ScaleContext.Cache<BufferedImage>> = EnumMap(ImageType::class.java)

  override fun doPaint(g: Graphics2D) {
    val paddings = JBInsets.create(navBarItemPadding(isFloating))
    val isFirst = vm.isFirst
    if (ExperimentalUI.isNewUI()) {
      val rect = Rectangle(size)
      JBInsets.removeFrom(rect, paddings)
      var offset = rect.x
      if (!isFirst) {
        CHEVRON_ICON.paintIcon(this, g, offset, rect.y + (rect.height - CHEVRON_ICON.iconHeight) / 2)
        val delta = CHEVRON_ICON.iconWidth + Breadcrumbs.CHEVRON_INSET.get()
        offset += delta
        rect.width -= delta
      }
      val highlightColor = highlightColor()
      if (highlightColor != null) {
        AbstractNavBarUI.paintHighlight(g, Rectangle(offset, rect.y, rect.width, rect.height), highlightColor)
      }

      val icon = icon
      if (icon == null) {
        offset += ipad.left
      }
      else {
        offset += ipad.left
        icon.paintIcon(this, g, offset, rect.y + (rect.height - icon.iconHeight) / 2 + if (icon == MODULE_ICON) JBUI.scale(1) else 0)
        offset += icon.iconWidth
        offset += iconTextGap
      }
      doPaintText(g, offset, false)
    }
    else {
      val toolbarVisible = UISettings.getInstance().showMainToolbar
      val selected = isSelected && isFocused
      val nextSelected = vm.isNextSelected() && isFocused
      val type = ImageType.from(isFloating, toolbarVisible, selected, nextSelected)

      // see: https://github.com/JetBrains/intellij-community/pull/1111
      val imageCache = cache.computeIfAbsent(type) {
        ScaleContext.Cache { ctx: ScaleContext ->
          AbstractNavBarUI.drawToBuffer(this, ctx, isFloating, toolbarVisible, selected, nextSelected, vm.isLast)
        }
      }
      val image = imageCache.getOrProvide(ScaleContext.create(g))
                  ?: return
      StartupUiUtil.drawImage(g, image, 0, 0, null)
      val offset = if (isFirst) getFirstElementLeftOffset() else 0
      var textOffset = paddings.width() + offset
      val icon = icon
      if (icon != null) {
        val iconOffset = paddings.left + offset
        icon.paintIcon(this, g, iconOffset, (height - icon.iconHeight) / 2)
        textOffset += icon.iconWidth
      }
      doPaintText(g, textOffset, false)
    }
  }

  private fun highlightColor(): Color? {
    return when {
      isHovered -> Breadcrumbs.HOVER_BACKGROUND
      isSelected -> if (isFocused) Breadcrumbs.SELECTION_BACKGROUND else Breadcrumbs.SELECTION_INACTIVE_BACKGROUND
      else -> null
    }
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleNavBarItem()
    }
    return accessibleContext
  }

  private inner class AccessibleNavBarItem : AccessibleSimpleColoredComponent(), AccessibleAction {

    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PUSH_BUTTON

    override fun getAccessibleAction(): AccessibleAction = this

    override fun getAccessibleActionCount(): Int = 1

    override fun getAccessibleActionDescription(i: Int): String? {
      if (i == 0) {
        return UIManager.getString("AbstractButton.clickText")
      }
      return null
    }

    override fun doAccessibleAction(i: Int): Boolean {
      if (i == 0) {
        vm.select()
        return true
      }
      return false
    }
  }

  companion object {

    private val CHEVRON_ICON = AllIcons.General.ChevronRight

    private val MODULE_ICON = IconManager.getInstance().getIcon("expui/nodes/module8x8.svg", AllIcons::class.java)

    internal fun isItemComponentFocusable(): Boolean = ScreenReader.isActive()
  }
}
