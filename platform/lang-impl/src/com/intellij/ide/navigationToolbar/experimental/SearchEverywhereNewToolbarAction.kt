// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoClassPresentationUpdater.getActionTitlePluralized
import com.intellij.ide.actions.SearchEverywhereAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.BigPopup.searchFieldBackground
import com.intellij.util.ui.JBUI.CurrentTheme.TabbedPane.DISABLED_TEXT_COLOR
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.KeyEvent
import javax.swing.FocusManager
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt

class SearchEverywhereNewToolbarAction : SearchEverywhereAction(), AnActionListener {
  private val margin = JBUI.scale(4)
  private var hotKeyWasUsed = AdvancedSettings.getBoolean("ide.suppress.double.click.handler")
  private var subscribedForDoubleShift = false
  private var firstOpened = false

  init {
    templatePresentation.icon = AllIcons.Actions.Search
  }

  override fun update(event: AnActionEvent) {
    event.presentation.text = if (hotKeyWasUsed) {
      ActionsBundle.message("action.SearchEverywhereToolbar.text")
    }
    else {
      ActionsBundle.message("action.SearchEverywhereToolbarHotKey.text")
    }
    if (!subscribedForDoubleShift) {
      event.project?.let {
        ApplicationManager.getApplication().messageBus.connect(it).subscribe(AnActionListener.TOPIC, this)
        subscribedForDoubleShift = true
      }
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {

    return object : ActionButtonWithText(this, presentation, place,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      var seManager: SearchEverywhereManager? = null

      init {
        FocusManager.getCurrentManager().addPropertyChangeListener { this.repaint() }
        presentation.icon = AllIcons.Actions.Search
        presentation.text = if (hotKeyWasUsed) {
          ActionsBundle.message("action.SearchEverywhereToolbar.text")
        }
        else {
          ActionsBundle.message("action.SearchEverywhereToolbarHotKey.text")
        }
        setHorizontalTextAlignment(SwingConstants.LEFT);
      }

      override fun updateToolTipText() {
        val shortcutText = getShortcut()
        val classesTabName = java.lang.String.join("/", getActionTitlePluralized())
        if (Registry.`is`("ide.helptooltip.enabled")) {
          HelpTooltip.dispose(this)
          HelpTooltip()
            .setTitle(myPresentation.text)
            .setShortcut(shortcutText)
            .setDescription(IdeBundle.message("search.everywhere.action.tooltip.description.text", classesTabName))
            .installOn(this)
        }
        else {
          toolTipText = IdeBundle.message("search.everywhere.action.tooltip.text", shortcutText, classesTabName)
        }
      }

      override fun actionPerformed(e: AnActionEvent) {
        seManager = SearchEverywhereManager.getInstance(e.project)
        if (!firstOpened) {
          super.actionPerformed(AnActionEvent(
            e.inputEvent, e.dataContext, ActionPlaces.RUN_TOOLBAR, templatePresentation,
            ActionManager.getInstance(), 0))
          firstOpened = true
        }
        else {
          super.actionPerformed(AnActionEvent(
            e.inputEvent, e.dataContext, ActionPlaces.KEYBOARD_SHORTCUT, templatePresentation,
            ActionManager.getInstance(), 0))
        }
      }

      override fun paint(g: Graphics?) {
        foreground = DISABLED_TEXT_COLOR
        background = searchFieldBackground()
        super.paint(g)
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
          this.isOpaque = false
          this.border = null
          drawStringUnderlineCharAt(g, ActionsBundle.message("action.SearchEverywhereToolbar.searching.text"), getMnemonicCharIndex(text),
            textRect.x, textRect.y + fm.ascent)

          return
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

  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    if (action is SearchEverywhereAction && !hotKeyWasUsed) {
      if (event.inputEvent is KeyEvent) {
        if ((event.inputEvent as KeyEvent).keyCode == KeyEvent.VK_SHIFT) {
          hotKeyWasUsed = true
        }
      }
    }
  }
}