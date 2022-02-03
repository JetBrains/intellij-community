// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoClassPresentationUpdater.getActionTitlePluralized
import com.intellij.ide.actions.SearchEverywhereAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.BigPopup.searchFieldBackground
import com.intellij.util.ui.JBUI.CurrentTheme.TabbedPane.DISABLED_TEXT_COLOR
import java.awt.Cursor
import java.awt.Cursor.DEFAULT_CURSOR
import java.awt.Cursor.TEXT_CURSOR
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.FocusManager
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.AncestorEvent
import javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt

class SearchEverywhereNewToolbarAction : SearchEverywhereAction(), AnActionListener, DumbAware {
  companion object {
    const val SHOW_HOT_KEY_TIP = "com.intellij.ide.navigationToolbar.experimental.showSearchEverywhereHotKeyTip"
  }

  private val margin = JBUI.scale(4)
  private var subscribedForDoubleShift = false
  private var firstOpened = false
  private var clearPosition = false
  private var shouldShow = true
  var seManager: SearchEverywhereManager? = null

  override fun update(event: AnActionEvent) {
    if (event.place != ActionPlaces.MAIN_TOOLBAR) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    val project = event.project ?: return
    if (seManager == null) {
      seManager = SearchEverywhereManager.getInstance(project)
      Disposer.register(project) {
        seManager = null
      }
    }
    event.presentation.isEnabledAndVisible = shouldShow
    event.presentation.text = if (!showHotkey()) {
      ActionsBundle.message("action.SearchEverywhereToolbar.text")
    }
    else {
      val shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).shortcuts
      val shortcut = if (shortcuts.isEmpty()) {
        ActionsBundle.message("action.SearchEverywhereToolbarHotKey.hotkey")
      }
      else KeymapUtil.getShortcutsText(shortcuts)
      ActionsBundle.message("action.SearchEverywhereToolbarHotKey.text", shortcut)
    }
    event.presentation.icon = AllIcons.Actions.Search
    if (!subscribedForDoubleShift) {
      ApplicationManager.getApplication().messageBus.connect(project).subscribe(AnActionListener.TOPIC, this)
      subscribedForDoubleShift = true
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {

    return object : ActionButtonWithText(this, presentation, place,
                                         Dimension(0, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height)) {

      private val listener = PropertyChangeListener { this.repaint() }

      init {
        setHorizontalTextAlignment(SwingConstants.LEFT)
        cursor = Cursor.getPredefinedCursor(TEXT_CURSOR)
        this.addAncestorListener(object : AncestorListenerAdapter() {
          override fun ancestorAdded(event: AncestorEvent?) {
            rootPane?.addComponentListener(object : ComponentAdapter() {
              override fun componentResized(e: ComponentEvent?) {
                shouldShow = true
              }
            })
          }
        })
      }

      override fun addNotify() {
        FocusManager.getCurrentManager().addPropertyChangeListener(listener)
        super.addNotify()
      }

      override fun removeNotify() {
        FocusManager.getCurrentManager().removePropertyChangeListener(listener)
        super.removeNotify()
      }

      override fun getPreferredSize(): Dimension {
        return Dimension(super.getPreferredSize().width, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height)
      }

      override fun updateToolTipText() {
        val shortcutText = getShortcut()
        val classesTabName = java.lang.String.join("/", getActionTitlePluralized())
        if (Registry.`is`("ide.helptooltip.enabled")) {
          HelpTooltip.dispose(this)
          if (presentation.isEnabledAndVisible) {
            HelpTooltip()
              .setTitle(myPresentation.text)
              .setShortcut(shortcutText)
              .setDescription(IdeBundle.message("search.everywhere.action.tooltip.description.text", classesTabName))
              .installOn(this)
          }
        }
        else {
          toolTipText = if (presentation.isEnabledAndVisible) {
            IdeBundle.message("search.everywhere.action.tooltip.text", shortcutText, classesTabName)
          }
          else {
            ""
          }
        }
      }

      override fun actionPerformed(e: AnActionEvent) {
        val focusManager = IdeFocusManager.findInstance()
        val focusedComponent = focusManager.focusOwner
        val ideWindow = focusManager.lastFocusedIdeWindow
        val dataContext = if (ideWindow === focusedComponent || focusedComponent === focusManager.getLastFocusedFor(
            ideWindow)) DataManager.getInstance().getDataContext(focusedComponent)
        else DataManager.getInstance().dataContext

        if (!firstOpened) {
          super.actionPerformed(AnActionEvent(
            e.inputEvent, dataContext, ActionPlaces.RUN_TOOLBAR_LEFT_SIDE, templatePresentation,
            ActionManager.getInstance(), 0))
          firstOpened = true
        }
        else {
          super.actionPerformed(AnActionEvent(
            e.inputEvent, dataContext, ActionPlaces.KEYBOARD_SHORTCUT, templatePresentation,
            ActionManager.getInstance(), 0))
        }

      }

      override fun getPopState(): Int {
        return NORMAL
      }


      override fun paint(g: Graphics?) {
        if (!checkIfEnoughSpace()) return
        if (preferredSize.width < ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width) return
        foreground = DISABLED_TEXT_COLOR
        background = searchFieldBackground()
        super.paint(g)
      }

      private fun checkIfEnoughSpace(): Boolean {
        if (parent == null) return false
        if (parent.bounds.width < parent.preferredSize.width) {
          if (shouldShow) {
            shouldShow = false
            updateToolTipText()
            cursor = Cursor.getPredefinedCursor(DEFAULT_CURSOR)
          }
          return false
        }
        else {
          if (!shouldShow) {
            shouldShow = true
            updateToolTipText()
            cursor = Cursor.getPredefinedCursor(TEXT_CURSOR)
          }
        }
        return true
      }

      override fun iconTextSpace(): Int {
        return super.iconTextSpace() + margin
      }

      override fun paintComponent(g: Graphics) {
        setupAntialiasing(g)

        val fm = getFontMetrics(font)
        val viewRect = buttonRect
        val iconRect = Rectangle()
        val textRect = Rectangle()
        val text = SwingUtilities.layoutCompoundLabel(this, fm, presentation.getText(true), icon,
                                                      SwingConstants.CENTER, horizontalTextAlignment(),
                                                      SwingConstants.CENTER, horizontalTextPosition(),
                                                      viewRect, iconRect, textRect, iconTextSpace())

        if (seManager != null && seManager!!.isShown) {
          cursor = Cursor.getPredefinedCursor(DEFAULT_CURSOR)
          this.isOpaque = false
          this.border = null
          drawStringUnderlineCharAt(g, ActionsBundle.message("action.SearchEverywhereToolbar.searching.text"), getMnemonicCharIndex(text),
                                    textRect.x, textRect.y + fm.ascent)

          return
        }
        else {
          cursor = Cursor.getPredefinedCursor(TEXT_CURSOR)
        }

        val icon = icon
        JBInsets.removeFrom(viewRect, insets)
        JBInsets.removeFrom(viewRect, margins)

        iconRect.x = margin
        val look = buttonLook
        look.paintBackground(g, this)
        look.paintIcon(g, this, icon, iconRect.x, iconRect.y)
        look.paintBorder(g, this)
        g.color = if (presentation.isEnabled) foreground else inactiveTextColor
        drawStringUnderlineCharAt(g, text, getMnemonicCharIndex(text),
                                  textRect.x, textRect.y + fm.ascent)
      }
    }
  }

  private fun showHotkey(): Boolean {
    return PropertiesComponent.getInstance().getBoolean(SHOW_HOT_KEY_TIP, true) && !AdvancedSettings.getBoolean(
      "ide.suppress.double.click.handler")

  }

  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    if (action is SearchEverywhereAction && showHotkey()) {
      if (event.inputEvent is KeyEvent) {
        if ((event.inputEvent as KeyEvent).keyCode == KeyEvent.VK_SHIFT) {
          PropertiesComponent.getInstance().setValue(SHOW_HOT_KEY_TIP, false, true)
        }
      }
    }
    if (action is SearchEverywhereNewToolbarAction && event.place == ActionPlaces.MAIN_TOOLBAR) {
      clearPosition = true
    }
  }

  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    if (action.javaClass == SearchEverywhereAction::class.java && clearPosition) {
      event.project?.let { WindowStateService.getInstance(it).putLocation(SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY, null) }
      clearPosition = false
    }
  }
}