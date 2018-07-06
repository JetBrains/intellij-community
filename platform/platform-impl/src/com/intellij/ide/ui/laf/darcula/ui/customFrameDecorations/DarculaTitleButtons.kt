// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.ComponentStyle
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.ComponentStyleState
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.StyleManager
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.StyleProperty
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Color
import javax.accessibility.AccessibleContext
import javax.security.auth.Destroyable
import javax.swing.*

open class DarculaTitleButtons constructor(myCloseAction: Action,
                                           private val myHelpAction: HelpAction) : Destroyable {
  companion object {
    fun create(myCloseAction: Action, myHelpAction: HelpAction): DarculaTitleButtons {
      val darculaTitleButtons = DarculaTitleButtons(myCloseAction, myHelpAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }
  }

  private val baseStyle = ComponentStyle.ComponentStyleBuilder<JComponent> {
    isOpaque = false
    border = JBUI.Borders.empty()
  }.apply {
    style(ComponentStyleState.HOVERED) {
      isOpaque = true
      background = JBColor(0xd1d1d1, 0x54585a)
    }
    style(ComponentStyleState.PRESSED) {
      isOpaque = true
      background = JBColor(0xb5b5b5, 0x686e70)
    }
  }

  val closeStyleBuilder = ComponentStyle.ComponentStyleBuilder<JButton> {
    isOpaque = false
    border = JBUI.Borders.empty()
    icon = AllIcons.Windows.CloseActive
  }.apply {
    style(ComponentStyleState.HOVERED) {
      isOpaque = true
      background = Color(0xe81123)
      icon = AllIcons.Windows.CloseHover
    }
    style(ComponentStyleState.PRESSED) {
      isOpaque = true
      background = Color(0xf1707a)
      icon = AllIcons.Windows.CloseHover
    }
  }
  private val activeCloseStyle = closeStyleBuilder.build()

  private val inactiveCloseStyle = closeStyleBuilder
    .updateDefault() {
      icon = AllIcons.Windows.CloseInactive
    }.build()

  protected val panel = JPanel(MigLayout("filly, ins 0, gap 0, hidemode 3, novisualpadding"))

  private val myCloseButton: JButton = createButton("Close", myCloseAction)
  private val myHelpButton: JButton = createButton("Help", myHelpAction)

  var isSelected = false
    set(value) {
      if(field != value) {
        field = value
        updateStyles()
      }
    }

  protected open fun updateStyles() {
    StyleManager.applyStyle(myHelpButton, getStyle(AllIcons.Windows.HelpButton, AllIcons.Windows.HelpButton))
    StyleManager.applyStyle(myCloseButton, if(isSelected) activeCloseStyle else inactiveCloseStyle)
  }

  protected fun createChildren() {
    fillButtonPane()
    addCloseButton()
    updateVisibility()
    updateStyles()
  }

  fun getView(): JComponent = panel

  protected open fun fillButtonPane() {
    addComponent(myHelpButton)
  }

  open fun updateVisibility() {
    myHelpButton.isVisible = myHelpAction.isAvailable()
  }

  private fun addCloseButton() {
    addComponent(myCloseButton)
  }

  protected fun addComponent(component: JComponent) {
    panel.add(component, "growy, wmin ${JBUI.scale(38)}, hmin ${JBUI.scale(28)}")
  }

  protected fun getStyle(icon: Icon, hoverIcon : Icon): ComponentStyle<JComponent> {
    val clone = baseStyle.clone()
    clone.updateDefault {
      this.icon = icon
    }

    clone.updateState(ComponentStyleState.HOVERED) {
      this.icon = hoverIcon
    }

    clone.updateState(ComponentStyleState.PRESSED) {
      this.icon = hoverIcon
    }
    return clone.build()
  }

  protected fun createButton(accessibleName: String, action: Action): JButton {
    val button = StyleProperty.BasicButton()
    button.action = action
    button.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, accessibleName)
    button.text = null
    return button
  }
}