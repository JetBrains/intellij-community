// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.ComponentStyle
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.Properties
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.States
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.StyleManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.accessibility.AccessibleContext
import javax.security.auth.Destroyable
import javax.swing.*

open class DarculaTitleButtons constructor(private val myCloseAction: Action,
                                           private val myHelpAction: HelpAction) : Destroyable {
  companion object {
    fun create(myCloseAction: Action, myHelpAction: HelpAction): DarculaTitleButtons {
      val darculaTitleButtons = DarculaTitleButtons(myCloseAction, myHelpAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }
  }

  protected val panel = JPanel(MigLayout("filly, ins 0, gap 0, hidemode 3, novisualpadding"))

  private val myCloseButton: JButton = createCloseButton()
  private val myHelpButton: JButton = createButton("Help", myHelpAction, AllIcons.Windows.HelpButton)
  /* setFont(Font("Segoe UI Regular", Font.PLAIN, JBUI.scale(15)))*/

  protected fun createChildren() {
    fillButtonPane()
    addCloseButton()
    updateVisibility()
  }

  fun getView(): JComponent = panel

  protected open fun fillButtonPane() {
    if (myHelpAction.isAvailable) {
      addComponent(myHelpButton)
    }
  }

  open fun updateVisibility() {
  }

  private fun addCloseButton() {
    addComponent(myCloseButton)
  }

  protected fun addComponent(component: JComponent) {
    panel.add(component, "growy, wmin ${JBUI.scale(20)}")
  }

  private fun createButton(accessibleName: String, action: Action, icon: Icon): JButton {
    val button = Properties.BasicButton()
    button.action = action
    button.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, accessibleName)

    StyleManager.applyStyle(button, getStyle(icon))
    return button
  }

  protected fun getStyle(icon: Icon): ComponentStyle<JButton> = ComponentStyle<JButton> {
    isOpaque = false
    border = JBUI.Borders.empty()
    this.icon = icon
  }.apply {
    style(States.HOVERED) {
      isOpaque = true
      background = JBColor(0xd1d1d1, 0x54585a)
    }
    style(States.PRESSED) {
      isOpaque = true
      background = JBColor(0xb5b5b5, 0x686e70)
    }
  }


  private fun createCloseButton(): JButton {
    val button = Properties.BasicButton()
    button.action = myCloseAction
    button.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, "Close")

    val style = ComponentStyle<JButton> {
      isOpaque = false
      border = JBUI.Borders.empty()
      icon = AllIcons.Windows.CloseActive
    }.apply {
      style(States.HOVERED) {
        isOpaque = true
        background = Color(0xe81123)
        icon = AllIcons.Windows.CloseHover
      }
      style(States.PRESSED) {
        isOpaque = true
        background = Color(0xf1707a)
        icon = AllIcons.Windows.CloseHover
      }
    }

    StyleManager.applyStyle(button, style)
    return button
  }
}