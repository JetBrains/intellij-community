// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.Insets
import java.awt.Point
import javax.swing.JLabel
import javax.swing.JPanel

@ApiStatus.Experimental
@ApiStatus.Internal
fun showActionKeyPopup(parent: Component,
                       point: Point,
                       height: Int,
                       actionId: String,
                       addAdditionalItems: (JPanel) -> Unit = {}) {
  val action = ActionManager.getInstance().getAction(actionId)

  lateinit var balloon: Balloon
  val jPanel = JPanel()
  jPanel.layout = VerticalLayout(UIUtil.DEFAULT_VGAP)
  jPanel.isOpaque = false
  jPanel.add(JLabel(action.templatePresentation.text).also {
    it.foreground = UIUtil.getToolTipForeground()
  })
  val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)
  for (shortcut in shortcuts) {
    if (shortcut is KeyboardShortcut) {
      @NonNls val shortcutText = KeymapUtil.getShortcutText(shortcut)
      val shortcutLabel = JLabel(shortcutText).also {
        it.font = it.font.deriveFont((it.font.size - 1).toFloat())
        it.foreground = JBUI.CurrentTheme.Tooltip.shortcutForeground()
      }
      jPanel.add(shortcutLabel)
    }
  }

  addAdditionalItems(jPanel)

  jPanel.add(ActionLink(IdeBundle.message("shortcut.balloon.add.shortcut")) {
    KeymapPanel.addKeyboardShortcut(actionId, ActionShortcutRestrictions.getInstance().getForActionId(actionId),
                                    KeymapManager.getInstance().activeKeymap, parent)
    balloon.hide()
    parent.repaint()
  }.also {
    it.foreground = JBColor.namedColor("ToolTip.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED)
  })
  val builder = JBPopupFactory.getInstance()
    .createBalloonBuilder(jPanel)
    .setShowCallout(true)
    .setHideOnKeyOutside(true)
    .setHideOnClickOutside(true)
    .setHideOnAction(true)
    .setAnimationCycle(0)
    .setCalloutShift(height / 2 + 1)
    .setBlockClicksThroughBalloon(true)
    .setBorderColor(JBUI.CurrentTheme.Tooltip.borderColor())
    .setFillColor(UIUtil.getToolTipBackground())
    .setBorderInsets(Insets(8, 10, 8, 10))
    .setShadow(true)
  balloon = builder.createBalloon()
  balloon.show(RelativePoint(parent, point), Balloon.Position.below)
}
